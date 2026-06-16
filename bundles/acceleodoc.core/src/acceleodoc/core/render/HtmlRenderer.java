package acceleodoc.core.render;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import acceleodoc.core.ast.ElementDoc;
import acceleodoc.core.ast.ModuleDoc;
import acceleodoc.core.ast.QueryDoc;
import acceleodoc.core.ast.TemplateDoc;
import acceleodoc.core.extract.DocExtractor;
import acceleodoc.core.util.SearchIndexEntry;

/**
 * Renders a list of {@link ModuleDoc} objects into a multi-page HTML
 * documentation site using Freemarker templates.
 *
 * <h2>Output structure</h2>
 * <pre>
 * outputDir/
 *   index.html            — module overview table
 *   stylesheet.css        — CSS
 *   search.js             — Lunr search widget
 *   lunr.min.js           — Lunr library
 *   search-index.json     — pre-built search index
 *   aql-reference.html    — local AQL documentation with injected anchors
 *   {moduleName}.html     — one page per module
 * </pre>
 *
 * <h2>Link maps</h2>
 * <p>All link map JSON files are loaded from the bundle resources directory
 * ({@code /resources/templates/}) via {@code getResourceAsStream}:</p>
 * <ul>
 *   <li>{@code aql-links.json} — AQL operation names to aql-reference.html anchors</li>
 *   <li>{@code service-links-uml2.json} — UML2 method names to Javadoc URLs</li>
 *   <li>{@code service-links-emf.json} — EMF method names to Javadoc URLs</li>
 *   <li>{@code java-services.json} — project Java service method names to URLs</li>
 * </ul>
 * <p>All four files must be placed in
 * {@code bundles/acceleodoc.core/resources/templates/}.</p>
 */
public class HtmlRenderer {

    private static final String TEMPLATE_DIR = "/resources/templates/";
    private final Configuration freemarker;

    /** AQL operation name → URL in aql-reference.html */
    private Map<String, String> aqlLinks  = Map.of();

    /** UML2 method name → full Javadoc URL */
    private Map<String, String> uml2Links = Map.of();

    /** EMF method name → full Javadoc URL */
    private Map<String, String> emfLinks  = Map.of();

    public HtmlRenderer() {
        freemarker = new Configuration(Configuration.VERSION_2_3_33);
        freemarker.setTemplateLoader(
                new ClassTemplateLoader(HtmlRenderer.class, TEMPLATE_DIR));
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(
                TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
        freemarker.setWrapUncheckedExceptions(true);
    }

    // -------------------------------------------------------------------------
    // Link map loading
    // -------------------------------------------------------------------------

    /**
     * Loads all link maps from bundle resources and configures the
     * {@link DocExtractor} with them.
     *
     * <p>Loads four JSON files from
     * {@code bundles/acceleodoc.core/resources/templates/}:</p>
     * <ul>
     *   <li>{@code aql-links.json}</li>
     *   <li>{@code service-links-uml2.json}</li>
     *   <li>{@code service-links-emf.json}</li>
     *   <li>{@code java-services.json} — project Java service links;
     *       if absent, project service badges render as plain unlinked text</li>
     * </ul>
     *
     * <p>Must be called before {@link #render}.</p>
     */
    public void loadLinkMaps(DocExtractor extractor) throws IOException {
        aqlLinks  = loadLinkMap("aql-links.json");
        uml2Links = loadLinkMap("service-links-uml2.json");
        emfLinks  = loadLinkMap("service-links-emf.json");
        extractor.setLinkMaps(aqlLinks, uml2Links, emfLinks);

        // Load project Java service links from the bundle resources.
        // The file java-services.json must be placed alongside the other
        // JSON files in bundles/acceleodoc.core/resources/templates/.
//        Map<String, String> projectLinks = loadLinkMap("java-services.json");
//        if (!projectLinks.isEmpty()) {
//            extractor.setProjectServiceLinks(projectLinks);
//            System.out.println("[acceleodoc] Loaded " + projectLinks.size()
//                    + " project service entries from java-services.json");
//        }
    }

    /**
     * Loads a simple flat JSON map of {@code "name": "url"} pairs from a
     * resource file bundled in the templates directory.
     *
     * <p>Uses a minimal hand-written JSON parser to avoid a JSON library
     * dependency. The format must be a flat object with string values only.</p>
     *
     * <p>Returns an empty map if the resource is not found, so callers
     * do not need to handle the absent-file case specially.</p>
     */
    private Map<String, String> loadLinkMap(String resourceName) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (var in = HtmlRenderer.class.getResourceAsStream(
                TEMPLATE_DIR + resourceName)) {
            if (in == null) {
                // Not all link maps are required — java-services.json is optional
                if (!resourceName.equals("java-services.json")) {
                    System.err.println("[acceleodoc] Warning: link map not found: "
                            + resourceName);
                }
                return result;
            }
            String json = new String(
                    in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(json);
            while (m.find()) {
                result.put(m.group(1), m.group(2));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Renders all module documentation to the specified output directory.
     */
    public void render(List<ModuleDoc> modules, Path outputDir)
            throws IOException, TemplateException {

        Files.createDirectories(outputDir);

        copyResource("stylesheet.css",     outputDir.resolve("stylesheet.css"));
        copyResource("search.js",          outputDir.resolve("search.js"));
        copyResource("lunr.min.js",        outputDir.resolve("lunr.min.js"));
        copyResource("aql-reference.html", outputDir.resolve("aql-reference.html"));

        List<SearchIndexEntry> searchIndex = buildSearchIndex(modules);
        writeSearchIndex(searchIndex, outputDir.resolve("search-index.json"));

        // Build Freemarker model with link maps available in all templates
        Map<String, Object> commonModel = new HashMap<>();
        commonModel.put("aqlLinks",  aqlLinks);
        commonModel.put("uml2Links", uml2Links);
        commonModel.put("emfLinks",  emfLinks);

        // Render index page
        Map<String, Object> indexModel = new HashMap<>(commonModel);
        indexModel.put("modules", modules);
        renderTemplate("index.ftl", indexModel, outputDir.resolve("index.html"));

        // Render per-module pages
        for (ModuleDoc module : modules) {
            Map<String, Object> model = new HashMap<>(commonModel);
            model.put("module",  module);
            model.put("modules", modules);
            renderTemplate("module.ftl", model,
                    outputDir.resolve(module.getHtmlFileName()));
        }

        System.out.println("[acceleodoc] Generated documentation for "
                + modules.size() + " module(s) in "
                + outputDir.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Template rendering
    // -------------------------------------------------------------------------

    private void renderTemplate(String templateName, Map<String, Object> model,
                                 Path outputFile)
            throws IOException, TemplateException {
        Template template = freemarker.getTemplate(templateName);
        try (Writer writer = Files.newBufferedWriter(outputFile)) {
            template.process(model, writer);
        }
    }

    // -------------------------------------------------------------------------
    // Search index
    // -------------------------------------------------------------------------

    private List<SearchIndexEntry> buildSearchIndex(List<ModuleDoc> modules) {
        List<SearchIndexEntry> entries = new ArrayList<>();
        for (ModuleDoc module : modules) {
            entries.add(new SearchIndexEntry(
                    module.getName(), "module", module.getName(),
                    module.getName(), module.getDescription(), "",
                    module.getHtmlFileName()));
            for (TemplateDoc t : module.getTemplates()) {
                entries.add(makeElementEntry(t, module));
            }
            for (QueryDoc q : module.getQueries()) {
                entries.add(makeElementEntry(q, module));
            }
        }
        return entries;
    }

    private SearchIndexEntry makeElementEntry(ElementDoc element,
                                               ModuleDoc module) {
        String id  = module.getName() + "#" + element.getName();
        String url = module.getHtmlFileName() + "#" + element.getName();
        String paramText = element.getParams().stream()
                .map(p -> p.name() + " " + p.description())
                .reduce("", (a, b) -> a + " " + b).strip();
        return new SearchIndexEntry(id, element.getKind(), element.getName(),
                module.getName(), element.getDescription(), paramText, url);
    }

    private void writeSearchIndex(List<SearchIndexEntry> entries,
                                   Path outputFile) throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            SearchIndexEntry e = entries.get(i);
            json.append("  {")
                .append("\"id\":").append(jsonString(e.id())).append(",")
                .append("\"kind\":").append(jsonString(e.kind())).append(",")
                .append("\"name\":").append(jsonString(e.name())).append(",")
                .append("\"moduleName\":").append(jsonString(e.moduleName()))
                .append(",")
                .append("\"description\":").append(jsonString(e.description()))
                .append(",")
                .append("\"params\":").append(jsonString(e.params())).append(",")
                .append("\"url\":").append(jsonString(e.url()))
                .append("}");
            if (i < entries.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("]");
        Files.writeString(outputFile, json.toString());
    }

    private String jsonString(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "") + "\"";
    }

    // -------------------------------------------------------------------------
    // Static resource copying
    // -------------------------------------------------------------------------

    private void copyResource(String resourceName, Path target)
            throws IOException {
        try (var in = HtmlRenderer.class.getResourceAsStream(
                TEMPLATE_DIR + resourceName)) {
            if (in == null) {
                System.err.println(
                        "[acceleodoc] Warning: static resource not found: "
                        + resourceName);
                return;
            }
            Files.copy(in, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
