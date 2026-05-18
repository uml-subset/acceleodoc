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
import acceleodoc.core.util.SearchIndexEntry;

/**
 * Renders a list of {@link ModuleDoc} objects into a multi-page HTML
 * documentation site using Freemarker templates.
 *
 * <h2>Output structure</h2>
 * <pre>
 * outputDir/
 *   index.html          — module overview table
 *   stylesheet.css      — Javadoc-inspired CSS (copied from resources)
 *   search.js           — Lunr.js search widget
 *   lunr.min.js         — Lunr.js library (copied from resources)
 *   search-index.json   — pre-built Lunr search index
 *   {moduleName}.html   — one page per module
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * HtmlRenderer renderer = new HtmlRenderer();
 * renderer.render(moduleDocs, Path.of("/path/to/output"));
 * }</pre>
 */
public class HtmlRenderer {

	private static final String TEMPLATE_DIR = "/resources/templates/";
//    private static final String TEMPLATE_DIR = "/acceleodoc/resources/templates/";
    private final Configuration freemarker;

    public HtmlRenderer() {
        freemarker = new Configuration(Configuration.VERSION_2_3_33);
     // Correct — looks for /resources/templates/ relative to the bundle root
        freemarker.setTemplateLoader(
                new ClassTemplateLoader(HtmlRenderer.class, "/resources/templates/"));
//        freemarker.setTemplateLoader(
//                new ClassTemplateLoader(HtmlRenderer.class, TEMPLATE_DIR));
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarker.setLogTemplateExceptions(false);
        freemarker.setWrapUncheckedExceptions(true);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Renders all module documentation to the specified output directory.
     *
     * @param modules   list of extracted module documentation objects
     * @param outputDir directory to write HTML files into (created if absent)
     * @throws IOException      if a file cannot be written
     * @throws TemplateException if a Freemarker template fails
     */
    public void render(List<ModuleDoc> modules, Path outputDir)
            throws IOException, TemplateException {

        Files.createDirectories(outputDir);

        // Copy static resources (CSS, JS, Lunr library)
        copyResource("stylesheet.css",  outputDir.resolve("stylesheet.css"));
        copyResource("search.js",       outputDir.resolve("search.js"));
        copyResource("lunr.min.js",     outputDir.resolve("lunr.min.js"));

        // Build search index
        List<SearchIndexEntry> searchIndex = buildSearchIndex(modules);
        writeSearchIndex(searchIndex, outputDir.resolve("search-index.json"));

        // Render index page
        renderTemplate("index.ftl", Map.of("modules", modules), outputDir.resolve("index.html"));

        // Render per-module pages
        for (ModuleDoc module : modules) {
            Map<String, Object> model = new HashMap<>();
            model.put("module", module);
            model.put("modules", modules);
            renderTemplate("module.ftl", model, outputDir.resolve(module.getHtmlFileName()));
        }

        System.out.println("[acceleodoc] Generated documentation for " + modules.size()
                + " module(s) in " + outputDir.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Template rendering
    // -------------------------------------------------------------------------

    private void renderTemplate(String templateName, Map<String, Object> model, Path outputFile)
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
            // Module entry
            entries.add(new SearchIndexEntry(
                    module.getName(),
                    "module",
                    module.getName(),
                    module.getName(),
                    module.getDescription(),
                    "",
                    module.getHtmlFileName()));

            // Template entries
            for (TemplateDoc t : module.getTemplates()) {
                entries.add(makeElementEntry(t, module));
            }

            // Query entries
            for (QueryDoc q : module.getQueries()) {
                entries.add(makeElementEntry(q, module));
            }
        }

        return entries;
    }

    private SearchIndexEntry makeElementEntry(ElementDoc element, ModuleDoc module) {
        String id  = module.getName() + "#" + element.getName();
        String url = module.getHtmlFileName() + "#" + element.getName();

        String paramText = element.getParams().stream()
                .map(p -> p.name() + " " + p.description())
                .reduce("", (a, b) -> a + " " + b)
                .strip();

        return new SearchIndexEntry(
                id,
                element.getKind(),
                element.getName(),
                module.getName(),
                element.getDescription(),
                paramText,
                url);
    }

    private void writeSearchIndex(List<SearchIndexEntry> entries, Path outputFile)
            throws IOException {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            SearchIndexEntry e = entries.get(i);
            json.append("  {")
                .append("\"id\":").append(jsonString(e.id())).append(",")
                .append("\"kind\":").append(jsonString(e.kind())).append(",")
                .append("\"name\":").append(jsonString(e.name())).append(",")
                .append("\"moduleName\":").append(jsonString(e.moduleName())).append(",")
                .append("\"description\":").append(jsonString(e.description())).append(",")
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

    private void copyResource(String resourceName, Path target) throws IOException {
        try (var in = HtmlRenderer.class.getResourceAsStream(TEMPLATE_DIR + resourceName)) {
            if (in == null) {
                System.err.println("[acceleodoc] Warning: static resource not found: " + resourceName);
                return;
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
