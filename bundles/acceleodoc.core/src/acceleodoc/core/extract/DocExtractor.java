package acceleodoc.core.extract;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.acceleo.CommentBody;
import org.eclipse.acceleo.Module;
import org.eclipse.acceleo.ModuleDocumentation;
import org.eclipse.acceleo.ModuleElement;
import org.eclipse.acceleo.ModuleElementDocumentation;
import org.eclipse.acceleo.Query;
import org.eclipse.acceleo.Template;
import org.eclipse.acceleo.TypedElement;
import org.eclipse.acceleo.Variable;
import org.eclipse.acceleo.aql.parser.AcceleoAstResult;
import org.eclipse.acceleo.aql.parser.AcceleoParser;
import org.eclipse.acceleo.query.ast.ASTNode;

import acceleodoc.core.ast.ModuleDoc;
import acceleodoc.core.ast.ParamDoc;
import acceleodoc.core.ast.QueryDoc;
import acceleodoc.core.ast.TemplateDoc;
import acceleodoc.core.util.DocComment;
import acceleodoc.core.util.DocCommentParser;

/**
 * Scans a source folder for {@code .mtl} files, parses each one using the
 * Acceleo 4.x AQL {@link AcceleoParser}, and extracts a {@link ModuleDoc}
 * from every parsed {@link Module}.
 *
 * <h2>Documentation comment parsing</h2>
 * <p>For each template and query, the full source text of the
 * {@code [** ... /]} documentation block is read directly from the
 * {@code .mtl} source file using position information from
 * {@link AcceleoAstResult}. This ensures that multi-line {@code @param}
 * descriptions are captured in full.</p>
 *
 * <h2>Call reference extraction</h2>
 * <p>Template and query body ASTs are walked via {@link AstCallWalker}
 * to extract cross-reference information: internal query/template calls,
 * AQL collection operations, AQL services, and external Java method calls
 * (UML2, EMF, project services).</p>
 *
 * <h2>Two-pass extraction</h2>
 * <p>Pass 1 collects all query and template names (with their module HTML
 * filenames) across all {@code .mtl} files before any documentation is
 * extracted. This ensures cross-module query/template references are
 * correctly categorised and linked regardless of file processing order.</p>
 *
 * <h2>Project services</h2>
 * <p>If a file named {@code java-services.json} is present in
 * {@code sourceRoot}, it is loaded as a flat map of method name to
 * documentation URL. Methods found there are linked in the generated HTML.</p>
 */
public class DocExtractor {

    private final AcceleoParser parser;

    /** Link maps for call categorisation, set via {@link #setLinkMaps}. */
    private Map<String, String> aqlLinks            = Map.of();
    private Map<String, String> uml2Links           = Map.of();
    private Map<String, String> emfLinks            = Map.of();
    private Map<String, String> projectServiceLinks = Map.of();

    /** Source text of the .mtl file currently being processed. */
    private String currentSource = "";

    /** AcceleoAstResult of the file currently being processed. */
    private AcceleoAstResult currentAstResult = null;

    /**
     * Global maps accumulated in pass 1:
     *   key   = element name (query or template)
     *   value = HTML filename of the module where it is defined
     *           (e.g. "commonQueries.html")
     * Used for cross-module link resolution in CallCategoriser.
     */
    private final Map<String, String> allKnownQueryNames    = new HashMap<>();
    private final Map<String, String> allKnownTemplateNames = new HashMap<>();

    public DocExtractor() {
        this.parser = new AcceleoParser();
    }

    /**
     * Sets the AQL, UML2 and EMF link maps used for call categorisation.
     * Must be called before {@link #extractAll} or {@link #extractOne}.
     */
    public void setLinkMaps(Map<String, String> aqlLinks,
                             Map<String, String> uml2Links,
                             Map<String, String> emfLinks) {
        this.aqlLinks  = aqlLinks;
        this.uml2Links = uml2Links;
        this.emfLinks  = emfLinks;
    }

    /**
     * Sets the project-specific service link map.
     * Maps method names to documentation URLs for project Java services.
     * Called either by {@link #loadProjectServices} or externally.
     */
    public void setProjectServiceLinks(Map<String, String> projectServiceLinks) {
        this.projectServiceLinks = projectServiceLinks;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Recursively scans {@code sourceRoot} for {@code .mtl} files and
     * extracts a {@link ModuleDoc} from each one.
     *
     * <p>Runs two passes over the file set:</p>
     * <ol>
     *   <li>Pass 1 — collects all query and template names with their module
     *       HTML filenames for cross-module link resolution.</li>
     *   <li>Pass 2 — full extraction using the complete name maps.</li>
     * </ol>
     *
     * @param sourceRoot root directory containing {@code .mtl} files
     * @return list of extracted module documentation objects (never null)
     * @throws IOException if the directory cannot be walked
     */
    public List<ModuleDoc> extractAll(Path sourceRoot) throws IOException {
    	System.err.println("[acceleodoc DEBUG] extractAll called, sourceRoot="
                + sourceRoot);
    	allKnownQueryNames.clear();
        allKnownTemplateNames.clear();
        loadProjectServices(sourceRoot);

        // Pass 1: collect all query and template names across all modules
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(p -> p.toString().endsWith(".mtl"))
                .forEach(mtlPath -> collectNames(mtlPath, sourceRoot));
        }

        // Pass 2: full extraction using the complete name maps
        List<ModuleDoc> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(p -> p.toString().endsWith(".mtl"))
                .forEach(mtlPath -> {
                    try {
                        ModuleDoc doc = extractOne(mtlPath, sourceRoot);
                        if (doc != null) result.add(doc);
                    } catch (IOException e) {
                        System.err.println("[acceleodoc] Warning: could not parse "
                                + mtlPath + ": " + e.getMessage());
                    }
                });
        }
        return result;
    }

    /**
     * Parses a single {@code .mtl} file and returns its {@link ModuleDoc}.
     *
     * @param mtlPath    path to the {@code .mtl} file
     * @param sourceRoot root used to derive the relative file path for display
     * @return extracted documentation, or {@code null} if parsing failed
     * @throws IOException if the file cannot be read
     */
    public ModuleDoc extractOne(Path mtlPath, Path sourceRoot) throws IOException {
        currentSource    = Files.readString(mtlPath);
        String relativePath  = sourceRoot.relativize(mtlPath).toString();
        String qualifiedName = deriveQualifiedName(relativePath);

        currentAstResult = parser.parse(currentSource, qualifiedName, null);

        if (currentAstResult == null || currentAstResult.getModule() == null) {
            System.err.println("[acceleodoc] Warning: null AST for " + mtlPath);
            return null;
        }

        if (!currentAstResult.getErrors().isEmpty()) {
            currentAstResult.getErrors().forEach(err ->
                System.err.println("[acceleodoc] Parse warning in "
                        + relativePath + ": " + err));
        }

        return extractModule(
                currentAstResult.getModule(), relativePath, qualifiedName);
    }

    // -------------------------------------------------------------------------
    // Pass 1: name collection
    // -------------------------------------------------------------------------

    /**
     * Parses one {@code .mtl} file and adds all query and template names
     * to the global maps, keyed by name and valued by the module HTML filename.
     *
     * <p>Used in pass 1 of {@link #extractAll}. Errors are silently skipped
     * since they will be reported again in pass 2.</p>
     */
    private void collectNames(Path mtlPath, Path sourceRoot) {
        try {
            String source        = Files.readString(mtlPath);
            String relativePath  = sourceRoot.relativize(mtlPath).toString();
            String qualifiedName = deriveQualifiedName(relativePath);
            AcceleoAstResult result = parser.parse(source, qualifiedName, null);
            if (result == null || result.getModule() == null) return;
            Module module = result.getModule();
            if (module.getName() == null) return;
            String htmlFile = module.getName() + ".html";
            for (ModuleElement element : module.getModuleElements()) {
                if (element instanceof Query q)
                    allKnownQueryNames.put(q.getName(), htmlFile);
                if (element instanceof Template t)
                    allKnownTemplateNames.put(t.getName(), htmlFile);
            }
        } catch (IOException e) {
            // silently skip — reported in pass 2
        }
    }

    // -------------------------------------------------------------------------
    // Project services loading
    // -------------------------------------------------------------------------

    /**
     * Loads project-specific Java service links from {@code java-services.json}
     * in the source root.
     *
     * <p>The file is a flat JSON map of method name to documentation URL
     * relative to the acceleodoc output {@code doc/} folder:</p>
     * <pre>
     * {
     *   "getCurrentDateTime": "java/ucmism2t/services/DateTimeService.html#getCurrentDateTime--",
     *   "getProperty":        "java/ucmism2t/services/PropertiesService.html#getProperty--"
     * }
     * </pre>
     *
     * <p>If the file is absent, {@link #projectServiceLinks} is set to an
     * empty map and project service badges render as plain unlinked text.</p>
     */
    private void loadProjectServices(Path sourceRoot) {
    	System.err.println("[acceleodoc DEBUG] loadProjectServices called");
    	Path config = sourceRoot.resolve("java-services.json");
        System.err.println("[acceleodoc DEBUG] looking for java-services.json at: "
                + config.toAbsolutePath());
        if (!Files.exists(config)) {
            System.err.println("[acceleodoc DEBUG] not found");
            projectServiceLinks = Map.of();
            return;
        }
        System.err.println("[acceleodoc DEBUG] found, loading...");
        try {
            String json = Files.readString(config);

            // ── debug ──────────────────────────────────────────────────
            System.err.println("[acceleodoc DEBUG] json length: " + json.length());
            System.err.println("[acceleodoc DEBUG] json first 200 chars: "
                    + json.substring(0, Math.min(200, json.length()))
                          .replace("\t", "\\t")
                          .replace("\r", "\\r")
                          .replace("\n", "\\n"));
            // ───────────────────────────────────────────────────────────

            Map<String, String> result = new HashMap<>();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(json);

            // ── debug ──────────────────────────────────────────────────
            int matchCount = 0;
            while (m.find()) {
                matchCount++;
                System.err.println("[acceleodoc DEBUG] match " + matchCount
                        + ": key=" + m.group(1) + " value=" + m.group(2));
                result.put(m.group(1), m.group(2));
            }
            System.err.println("[acceleodoc DEBUG] regex match count: " + matchCount);
            // ───────────────────────────────────────────────────────────

            projectServiceLinks = result;
            projectServiceLinks = result;
            System.err.println("[acceleodoc DEBUG] projectServiceLinks keys: "
                    + projectServiceLinks.keySet());
            System.out.println("[acceleodoc] Loaded " + result.size()
                    + " project service entries from java-services.json");
            System.out.println("[acceleodoc] Loaded " + result.size()
                    + " project service entries from java-services.json");
        } catch (IOException e) {
            System.err.println("[acceleodoc] Warning: could not load "
                    + "java-services.json: " + e.getMessage());
            projectServiceLinks = Map.of();
        }
    }
    //    private void loadProjectServices(Path sourceRoot) {
//        Path config = sourceRoot.resolve("java-services.json");
//        System.err.println("[acceleodoc DEBUG] looking for java-services.json at: "
//                + config.toAbsolutePath());
//        if (!Files.exists(config)) {
//            System.err.println("[acceleodoc DEBUG] not found");
//            projectServiceLinks = Map.of();
//            return;
//        }
//        System.err.println("[acceleodoc DEBUG] found, loading...");
//     // At end of loadProjectServices(), after loading:
//        System.err.println("[acceleodoc DEBUG] projectServiceLinks loaded: "
//                + projectServiceLinks.keySet());
//
//        // At start of extractModule(), before building CallCategoriser:
//        System.err.println("[acceleodoc DEBUG] building CallCategoriser with projectServiceLinks: "
//                + projectServiceLinks.keySet());
//        System.err.println("[acceleodoc DEBUG] json length: " + json.length());
//        System.err.println("[acceleodoc DEBUG] json first 200 chars: "
//                + json.substring(0, Math.min(200, json.length()))
//                      .replace("\t", "\\t")
//                      .replace("\r", "\\r")
//                      .replace("\n", "\\n"));
//        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
//                "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
//        java.util.regex.Matcher m = p.matcher(json);
//        int matchCount = 0;
//        while (m.find()) {
//            matchCount++;
//            result.put(m.group(1), m.group(2));
//        }
//        System.err.println("[acceleodoc DEBUG] regex match count: " + matchCount);
//    }
//    private void loadProjectServices(Path sourceRoot) {
//        Path config = sourceRoot.resolve("java-services.json");
//        if (!Files.exists(config)) {
//            projectServiceLinks = Map.of();
//            return;
//        }
//        try {
//            String json = Files.readString(config);
//            Map<String, String> result = new HashMap<>();
//            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
//                    "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
//            java.util.regex.Matcher m = p.matcher(json);
//            while (m.find()) {
//                result.put(m.group(1), m.group(2));
//            }
//            projectServiceLinks = result;
//            System.out.println("[acceleodoc] Loaded " + result.size()
//                    + " project service entries from java-services.json");
//        } catch (IOException e) {
//            System.err.println("[acceleodoc] Warning: could not load "
//                    + "java-services.json: " + e.getMessage());
//            projectServiceLinks = Map.of();
//        }
//    }

    // -------------------------------------------------------------------------
    // Module extraction
    // -------------------------------------------------------------------------

    private ModuleDoc extractModule(Module module, String relativePath,
                                     String qualifiedName) {
    	System.err.println("[acceleodoc DEBUG] extractModule projectServiceLinks size: "
    	        + projectServiceLinks.size());
    	if (module.getName() == null) {
            System.err.println("[acceleodoc] Skipping unparseable module: "
                    + relativePath);
            return null;
        }

        String description = "";
        String author      = null;
        String version     = null;
        String deprecated  = null;
        String see         = null;

        if (module.getDocumentation() instanceof ModuleDocumentation modDoc) {
            author  = nullIfBlank(modDoc.getAuthor());
            version = nullIfBlank(modDoc.getVersion());
            String fullSource = fullDocSource(modDoc);
            DocComment parsed = fullSource != null
                    ? DocCommentParser.parseFullDocBlock(fullSource)
                    : DocCommentParser.parse(bodyValue(modDoc.getBody()));
            description = parsed.description();
            deprecated  = parsed.tag("deprecated");
            see         = parsed.tag("see");
        }

        List<String> metamodels = new ArrayList<>();
        for (org.eclipse.acceleo.Metamodel mm : module.getMetamodels()) {
            String uri = mm.getReferencedPackage();
            if (uri != null && !uri.isBlank()) metamodels.add(uri);
        }

        // Build categoriser using the global cross-module name maps
        // populated in pass 1, so cross-module references resolve correctly
        CallCategoriser categoriser = new CallCategoriser(
                aqlLinks, uml2Links, emfLinks,
                projectServiceLinks,
                allKnownQueryNames,
                allKnownTemplateNames);
        AstCallWalker walker = new AstCallWalker(categoriser);

        List<TemplateDoc> templates = new ArrayList<>();
        List<QueryDoc>    queries   = new ArrayList<>();

        for (ModuleElement element : module.getModuleElements()) {
            if (element instanceof Template template) {
                templates.add(extractTemplate(template, walker));
            } else if (element instanceof Query query) {
                queries.add(extractQuery(query, walker));
            }
        }

        return ModuleDoc.builder()
                .name(module.getName())
                .qualifiedName(qualifiedName)
                .sourceFile(relativePath)
                .description(description)
                .author(author)
                .version(version)
                .deprecated(deprecated)
                .see(see)
                .metamodels(metamodels)
                .templates(templates)
                .queries(queries)
                .build();
    }

    // -------------------------------------------------------------------------
    // Template extraction
    // -------------------------------------------------------------------------

    private TemplateDoc extractTemplate(Template template, AstCallWalker walker) {
        String description = "";
        String deprecated  = null;
        String see         = null;
        String author      = null;
        String version     = null;
        List<ParamDoc> params;

        if (template.getDocumentation() instanceof ModuleElementDocumentation elemDoc) {
            String fullSource = fullDocSource(elemDoc);
            DocComment parsed = fullSource != null
                    ? DocCommentParser.parseFullDocBlock(fullSource)
                    : DocCommentParser.parse(bodyValue(elemDoc.getBody()));
            description = parsed.description();
            deprecated  = parsed.tag("deprecated");
            see         = parsed.tag("see");
            author      = parsed.tag("author");
            version     = parsed.tag("version");
            params = correlateParamsFromDoc(template.getParameters(), parsed);
        } else {
            params = undocumentedParams(template.getParameters());
        }

        List<CallReference> callRefs = template.getBody() != null
                ? walker.walk(template.getBody())
                : List.of();

        String sourceText = extractSourceText(template);

        return TemplateDoc.builder()
                .name(template.getName())
                .visibility(template.getVisibility() != null
                        ? template.getVisibility().getLiteral() : "public")
                .description(description)
                .params(params)
                .deprecated(deprecated)
                .see(see)
                .author(author)
                .version(version)
                .main(template.isMain())
                .override(false)
                .callReferences(callRefs)
                .sourceText(sourceText)
                .build();
    }

    // -------------------------------------------------------------------------
    // Query extraction
    // -------------------------------------------------------------------------

    private QueryDoc extractQuery(Query query, AstCallWalker walker) {
        String description       = "";
        String returnDescription = "";
        String deprecated        = null;
        String see               = null;
        String author            = null;
        String version           = null;
        List<ParamDoc> params;

        if (query.getDocumentation() instanceof ModuleElementDocumentation elemDoc) {
            String fullSource = fullDocSource(elemDoc);
            DocComment parsed = fullSource != null
                    ? DocCommentParser.parseFullDocBlock(fullSource)
                    : DocCommentParser.parse(bodyValue(elemDoc.getBody()));
            description       = parsed.description();
            returnDescription = parsed.tag("return") != null
                    ? parsed.tag("return") : "";
            deprecated        = parsed.tag("deprecated");
            see               = parsed.tag("see");
            author            = parsed.tag("author");
            version           = parsed.tag("version");
            params = correlateParamsFromDoc(query.getParameters(), parsed);
        } else {
            params = undocumentedParams(query.getParameters());
        }

        List<CallReference> callRefs = query.getBody() != null
                ? walker.walk(query.getBody())
                : List.of();

        String sourceText = extractSourceText(query);

        return QueryDoc.builder()
                .name(query.getName())
                .visibility(query.getVisibility() != null
                        ? query.getVisibility().getLiteral() : "public")
                .description(description)
                .params(params)
                .returnType(typeString(query))
                .returnDescription(returnDescription)
                .deprecated(deprecated)
                .see(see)
                .author(author)
                .version(version)
                .callReferences(callRefs)
                .sourceText(sourceText)
                .build();
    }

    // -------------------------------------------------------------------------
    // Parameter helpers
    // -------------------------------------------------------------------------

    /**
     * Correlates {@link Variable} parameters with their descriptions from a
     * parsed {@link DocComment}.
     *
     * <p>Uses the {@link DocComment} produced by
     * {@link DocCommentParser#parseFullDocBlock(String)} which correctly
     * handles multi-line {@code @param} descriptions.</p>
     */
    private List<ParamDoc> correlateParamsFromDoc(List<Variable> variables,
                                                   DocComment doc) {
        return variables.stream()
                .map(v -> new ParamDoc(
                        v.getName(),
                        typeString(v),
                        doc.paramDescription(v.getName())))
                .toList();
    }

    /**
     * Produces {@link ParamDoc} entries with no descriptions for parameters
     * that have no documentation comment.
     */
    private List<ParamDoc> undocumentedParams(List<Variable> variables) {
        return variables.stream()
                .map(v -> new ParamDoc(v.getName(), typeString(v), ""))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Source text extraction
    // -------------------------------------------------------------------------

    /**
     * Reads the full source text of a documentation node from the current
     * source file using position information from {@link AcceleoAstResult}.
     *
     * <p>Returns the complete {@code [** ... /]} block including delimiters
     * and all continuation lines of multi-line {@code @param} descriptions.</p>
     */
    private String fullDocSource(ASTNode node) {
        if (currentAstResult == null || currentSource == null
                || node == null) return null;
        int start = currentAstResult.getStartPosition(node);
        int end   = currentAstResult.getEndPosition(node);
        if (start < 0 || end <= start || end > currentSource.length()) return null;
        return currentSource.substring(start, end);
    }

    /**
     * Extracts the full source text of a template or query declaration
     * (including opening and closing tags) for the source code dropdown.
     */
    private String extractSourceText(ASTNode node) {
        if (currentAstResult == null || currentSource == null) return "";
        int start = currentAstResult.getStartPosition(node);
        int end   = currentAstResult.getEndPosition(node);
        if (start < 0 || end <= start || end > currentSource.length()) return "";
        return currentSource.substring(start, end).strip();
    }

    // -------------------------------------------------------------------------
    // Type extraction
    // -------------------------------------------------------------------------

    private String typeString(TypedElement element) {
        if (element == null) return "";
        org.eclipse.acceleo.query.parser.AstResult astResult = element.getType();
        if (astResult == null) return "";
        return extractTypeFromExpression(astResult.getAst());
    }

    private String extractTypeFromExpression(
            org.eclipse.acceleo.query.ast.Expression expr) {
        if (expr == null) return "";

        // CollectionTypeLiteral must be checked before ClassTypeLiteral
        if (expr instanceof org.eclipse.acceleo.query.ast.CollectionTypeLiteral col) {
            String kind = collectionKind(col.getValue());
            org.eclipse.acceleo.query.ast.TypeLiteral elemType = col.getElementType();
            String elem = elemType != null
                    ? extractTypeFromExpression(
                            (org.eclipse.acceleo.query.ast.Expression) elemType)
                    : "";
            return elem.isBlank() ? kind : kind + "(" + elem + ")";
        }

        if (expr instanceof org.eclipse.acceleo.query.ast.EClassifierTypeLiteral lit) {
            String pkg  = lit.getEPackageName();
            String name = lit.getEClassifierName();
            return (pkg != null && !pkg.isBlank())
                    ? pkg + "::" + name
                    : (name != null ? name : "");
        }

        if (expr instanceof org.eclipse.acceleo.query.ast.ClassTypeLiteral lit) {
            Class<?> value = lit.getValue();
            return value != null ? mapJavaToAqlName(value.getSimpleName()) : "";
        }

        for (org.eclipse.emf.ecore.EObject child : expr.eContents()) {
            if (child instanceof org.eclipse.acceleo.query.ast.Expression childExpr) {
                String found = extractTypeFromExpression(childExpr);
                if (!found.isBlank()) return found;
            }
        }
        return "";
    }

    private String collectionKind(Class<?> javaClass) {
        if (javaClass == null) return "Collection";
        return switch (javaClass.getSimpleName()) {
            case "Set"  -> "OrderedSet";
            case "List" -> "Sequence";
            default     -> javaClass.getSimpleName();
        };
    }

    private String mapJavaToAqlName(String javaSimpleName) {
        return switch (javaSimpleName) {
            case "Double" -> "Real";
            default       -> javaSimpleName;
        };
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private String bodyValue(CommentBody body) {
        if (body == null) return "";
        String value = body.getValue();
        return value != null ? value.strip() : "";
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // -------------------------------------------------------------------------
    // Qualified name derivation
    // -------------------------------------------------------------------------

    /**
     * Derives an Acceleo qualified name from a relative file path.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ucmism2t/templates/m2t.mtl} →
     *       {@code ucmism2t::templates::m2t}</li>
     *   <li>{@code src/ucmism2t/queries/helpers.mtl} →
     *       {@code ucmism2t::queries::helpers}</li>
     * </ul>
     */
    private String deriveQualifiedName(String relativePath) {
        String path = relativePath.replace(File.separatorChar, '/');
        for (String prefix : List.of("src/", "source/", "templates/")) {
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
                break;
            }
        }
        if (path.endsWith(".mtl")) {
            path = path.substring(0, path.length() - 4);
        }
        return path.replace("/", "::");
    }
}
