package acceleodoc.core.extract;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.acceleo.CommentBody;
import org.eclipse.acceleo.Module;
import org.eclipse.acceleo.ModuleDocumentation;
import org.eclipse.acceleo.ModuleElement;
import org.eclipse.acceleo.ModuleElementDocumentation;
import org.eclipse.acceleo.ParameterDocumentation;
import org.eclipse.acceleo.Query;
import org.eclipse.acceleo.Template;
import org.eclipse.acceleo.TypedElement;
import org.eclipse.acceleo.Variable;
import org.eclipse.acceleo.aql.parser.AcceleoAstResult;
import org.eclipse.acceleo.aql.parser.AcceleoParser;

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
 * <h2>AST documentation model</h2>
 * <p>The Acceleo 4.x metamodel uses two distinct documentation types:</p>
 * <ul>
 *   <li>{@link ModuleDocumentation} — attached to a {@link Module} via
 *       {@code getDocumentation()}. Carries the free-text body in
 *       {@code getBody().getValue()} and typed attributes {@code getAuthor()},
 *       {@code getVersion()}, {@code getSince()}.</li>
 *   <li>{@link ModuleElementDocumentation} — attached to {@link Template}
 *       and {@link Query} via {@code getDocumentation()}. Carries the
 *       free-text body in {@code getBody().getValue()} and a list of
 *       {@link ParameterDocumentation} nodes in
 *       {@code getParameterDocumentation()}, one per parameter in
 *       declaration order, each with its text in
 *       {@code getBody().getValue()}.</li>
 * </ul>
 *
 * <h2>Type extraction</h2>
 * <p>{@link TypedElement#getType()} returns an
 * {@link org.eclipse.acceleo.query.parser.AstResult} whose root AST node
 * is resolved as follows:</p>
 * <ul>
 *   <li>{@link org.eclipse.acceleo.query.ast.CollectionTypeLiteral} —
 *       collection types: {@code OrderedSet} (java.util.Set) and
 *       {@code Sequence} (java.util.List). Element type resolved
 *       recursively via {@code getElementType()}.</li>
 *   <li>{@link org.eclipse.acceleo.query.ast.EClassifierTypeLiteral} —
 *       EMF classifier types such as {@code uml::Model}. Package and
 *       classifier name available as plain strings.</li>
 *   <li>{@link org.eclipse.acceleo.query.ast.ClassTypeLiteral} —
 *       Java/AQL primitive types: {@code String}, {@code Boolean},
 *       {@code Integer}, {@code Real}.</li>
 * </ul>
 */
public class DocExtractor {

    private final AcceleoParser parser;

    public DocExtractor() {
        this.parser = new AcceleoParser();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Recursively scans {@code sourceRoot} for {@code .mtl} files and
     * extracts a {@link ModuleDoc} from each one.
     *
     * @param sourceRoot root directory containing {@code .mtl} files
     * @return list of extracted module documentation objects (never null)
     * @throws IOException if the directory cannot be walked
     */
    public List<ModuleDoc> extractAll(Path sourceRoot) throws IOException {
        List<ModuleDoc> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(p -> p.toString().endsWith(".mtl"))
                .forEach(mtlPath -> {
                    try {
                        ModuleDoc doc = extractOne(mtlPath, sourceRoot);
                        if (doc != null) {
                            result.add(doc);
                        }
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
     * @return extracted documentation, or {@code null} if parsing failed fatally
     * @throws IOException if the file cannot be read
     */
    public ModuleDoc extractOne(Path mtlPath, Path sourceRoot) throws IOException {
        String source        = Files.readString(mtlPath);
        String relativePath  = sourceRoot.relativize(mtlPath).toString();
        String qualifiedName = deriveQualifiedName(relativePath);

        AcceleoAstResult astResult = parser.parse(source, qualifiedName, null);

        if (astResult == null || astResult.getModule() == null) {
            System.err.println("[acceleodoc] Warning: null AST for " + mtlPath);
            return null;
        }

        if (!astResult.getErrors().isEmpty()) {
            astResult.getErrors().forEach(err ->
                System.err.println("[acceleodoc] Parse warning in "
                        + relativePath + ": " + err));
        }

        return extractModule(astResult.getModule(), relativePath, qualifiedName);
    }

    // -------------------------------------------------------------------------
    // Module extraction
    // -------------------------------------------------------------------------

    private ModuleDoc extractModule(Module module, String relativePath,
                                     String qualifiedName) {
        // Skip modules that failed to parse — name is null on ErrorModuleImpl
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
            // @author, @version, @since are typed attributes on ModuleDocumentation
            author  = nullIfBlank(modDoc.getAuthor());
            version = nullIfBlank(modDoc.getVersion());

            // Free-text body may still contain @deprecated and @see
            DocComment parsed = DocCommentParser.parse(bodyValue(modDoc.getBody()));
            description = parsed.description();
            deprecated  = parsed.tag("deprecated");
            see         = parsed.tag("see");
        }

        List<String> metamodels = new ArrayList<>();
        for (org.eclipse.acceleo.Metamodel mm : module.getMetamodels()) {
            String uri = mm.getReferencedPackage();
            if (uri != null && !uri.isBlank()) {
                metamodels.add(uri);
            }
        }

        List<TemplateDoc> templates = new ArrayList<>();
        List<QueryDoc>    queries   = new ArrayList<>();

        for (ModuleElement element : module.getModuleElements()) {
            if (element instanceof Template template) {
                templates.add(extractTemplate(template));
            } else if (element instanceof Query query) {
                queries.add(extractQuery(query));
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

    private TemplateDoc extractTemplate(Template template) {
        String description = "";
        String deprecated  = null;
        String see         = null;
        String author      = null;
        String version     = null;
        List<ParamDoc> params;

        if (template.getDocumentation() instanceof ModuleElementDocumentation elemDoc) {
            DocComment parsed = DocCommentParser.parse(bodyValue(elemDoc.getBody()));
            description = parsed.description();
            deprecated  = parsed.tag("deprecated");
            see         = parsed.tag("see");
            author      = parsed.tag("author");
            version     = parsed.tag("version");
            // @param documentation is carried by ParameterDocumentation nodes,
            // correlated to parameters by position
            params = correlateParams(
                    template.getParameters(),
                    elemDoc.getParameterDocumentation());
        } else {
            params = undocumentedParams(template.getParameters());
        }

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
                .build();
    }

    // -------------------------------------------------------------------------
    // Query extraction
    // -------------------------------------------------------------------------

    private QueryDoc extractQuery(Query query) {
        String description       = "";
        String returnDescription = "";
        String deprecated        = null;
        String see               = null;
        String author            = null;
        String version           = null;
        List<ParamDoc> params;

        if (query.getDocumentation() instanceof ModuleElementDocumentation elemDoc) {
            DocComment parsed = DocCommentParser.parse(bodyValue(elemDoc.getBody()));
            description       = parsed.description();
            returnDescription = parsed.tag("return") != null
                    ? parsed.tag("return") : "";
            deprecated        = parsed.tag("deprecated");
            see               = parsed.tag("see");
            author            = parsed.tag("author");
            version           = parsed.tag("version");
            params = correlateParams(
                    query.getParameters(),
                    elemDoc.getParameterDocumentation());
        } else {
            params = undocumentedParams(query.getParameters());
        }

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
                .build();
    }

    // -------------------------------------------------------------------------
    // Parameter helpers
    // -------------------------------------------------------------------------

    /**
     * Correlates {@link Variable} parameters with their
     * {@link ParameterDocumentation} nodes by position.
     *
     * <p>The i-th {@link ParameterDocumentation} in
     * {@link ModuleElementDocumentation#getParameterDocumentation()}
     * corresponds to the i-th parameter in the signature.</p>
     */
    private List<ParamDoc> correlateParams(List<Variable> variables,
                                            List<ParameterDocumentation> paramDocs) {
        List<ParamDoc> result = new ArrayList<>();
        for (int i = 0; i < variables.size(); i++) {
            Variable var = variables.get(i);
            String desc  = (i < paramDocs.size())
                    ? bodyValue(paramDocs.get(i).getBody())
                    : "";
            result.add(new ParamDoc(var.getName(), typeString(var), desc));
        }
        return result;
    }

    /**
     * Produces a list of {@link ParamDoc} with no descriptions for
     * parameters that have no documentation comment.
     */
    private List<ParamDoc> undocumentedParams(List<Variable> variables) {
        return variables.stream()
                .map(v -> new ParamDoc(v.getName(), typeString(v), ""))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Type extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts the human-readable type string from any {@link TypedElement}.
     *
     * <p>Accepts both {@link Variable} (parameter types) and {@link Query}
     * (return types) since both extend {@link TypedElement}.
     * {@link TypedElement#getType()} returns an
     * {@link org.eclipse.acceleo.query.parser.AstResult} whose root
     * expression is walked by {@link #extractTypeFromExpression}.</p>
     */
    private String typeString(TypedElement element) {
        if (element == null) return "";
        org.eclipse.acceleo.query.parser.AstResult astResult = element.getType();
        if (astResult == null) return "";
        return extractTypeFromExpression(astResult.getAst());
    }

    /**
     * Recursively walks an AQL {@link org.eclipse.acceleo.query.ast.Expression}
     * to find a type literal node and returns its human-readable type name.
     *
     * <p>Node types handled:</p>
     * <ul>
     *   <li>{@link org.eclipse.acceleo.query.ast.CollectionTypeLiteral} —
     *       checked first because it extends
     *       {@link org.eclipse.acceleo.query.ast.ClassTypeLiteral}.
     *       In Acceleo 4.x only {@code OrderedSet} (java.util.Set) and
     *       {@code Sequence} (java.util.List) exist.
     *       Element type resolved recursively via {@code getElementType()}.</li>
     *   <li>{@link org.eclipse.acceleo.query.ast.EClassifierTypeLiteral} —
     *       EMF types such as {@code uml::Model}. Package and classifier
     *       name available as plain strings.</li>
     *   <li>{@link org.eclipse.acceleo.query.ast.ClassTypeLiteral} —
     *       AQL primitive types: {@code String}, {@code Boolean},
     *       {@code Integer}, {@code Real}.</li>
     * </ul>
     */
    private String extractTypeFromExpression(
            org.eclipse.acceleo.query.ast.Expression expr) {
        if (expr == null) return "";

        // CollectionTypeLiteral must be checked before ClassTypeLiteral
        // because it extends ClassTypeLiteral
        if (expr instanceof org.eclipse.acceleo.query.ast.CollectionTypeLiteral col) {
            String collectionKind = collectionKind(col.getValue());
            org.eclipse.acceleo.query.ast.TypeLiteral elementTypeLiteral =
                    col.getElementType();
            String elementType = elementTypeLiteral != null
                    ? extractTypeFromExpression(
                            (org.eclipse.acceleo.query.ast.Expression) elementTypeLiteral)
                    : "";
            if (!elementType.isBlank()) {
                return collectionKind + "(" + elementType + ")";
            }
            return collectionKind;
        }

        // EMF classifier type — e.g. uml::Model, uml::NamedElement
        if (expr instanceof org.eclipse.acceleo.query.ast.EClassifierTypeLiteral lit) {
            String pkg  = lit.getEPackageName();
            String name = lit.getEClassifierName();
            if (pkg != null && !pkg.isBlank()) {
                return pkg + "::" + name;
            }
            return name != null ? name : "";
        }

        // Java/AQL primitive type — e.g. String, Boolean, Integer, Real
        if (expr instanceof org.eclipse.acceleo.query.ast.ClassTypeLiteral lit) {
            Class<?> value = lit.getValue();
            if (value != null) {
                return mapJavaToAqlName(value.getSimpleName());
            }
        }

        // Recurse into sub-expressions as fallback
        for (org.eclipse.emf.ecore.EObject child : expr.eContents()) {
            if (child instanceof org.eclipse.acceleo.query.ast.Expression childExpr) {
                String found = extractTypeFromExpression(childExpr);
                if (!found.isBlank()) return found;
            }
        }

        return "";
    }

    /**
     * Maps the Java interface used internally by Acceleo 4.x to the
     * corresponding AQL collection kind name.
     *
     * <p>In Acceleo 4.x only two collection types exist:</p>
     * <ul>
     *   <li>{@code OrderedSet} — represented as {@code java.util.Set}</li>
     *   <li>{@code Sequence}   — represented as {@code java.util.List}</li>
     * </ul>
     */
    private String collectionKind(Class<?> javaClass) {
        if (javaClass == null) return "Collection";
        return switch (javaClass.getSimpleName()) {
            case "Set"  -> "OrderedSet";
            case "List" -> "Sequence";
            default     -> javaClass.getSimpleName();
        };
    }

    /**
     * Maps Java simple class names to their AQL equivalents where they differ.
     */
    private String mapJavaToAqlName(String javaSimpleName) {
        return switch (javaSimpleName) {
            case "Double"  -> "Real";
            case "Integer" -> "Integer";
            case "String"  -> "String";
            case "Boolean" -> "Boolean";
            default        -> javaSimpleName;
        };
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the plain string value from a {@link CommentBody},
     * or an empty string if the body is null or its value is null.
     */
    private String bodyValue(CommentBody body) {
        if (body == null) return "";
        String value = body.getValue();
        return value != null ? value.strip() : "";
    }

    /** Returns null if the string is null or blank, otherwise the string itself. */
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
     *   <li>{@code ucmism2t/templates/m2t.mtl} becomes
     *       {@code ucmism2t::templates::m2t}</li>
     *   <li>{@code src/ucmism2t/queries/helpers.mtl} becomes
     *       {@code ucmism2t::queries::helpers}
     *       (leading {@code src/} is stripped)</li>
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
