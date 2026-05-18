package acceleodoc.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import acceleodoc.core.ast.ModuleDoc;
import acceleodoc.core.extract.DocExtractor;
import acceleodoc.core.render.HtmlRenderer;
import freemarker.template.TemplateException;

/**
 * Main entry point for the acceleodoc documentation generation pipeline.
 *
 * <p>This facade is the single public API used by both the CLI module
 * ({@code acceleodoc.cli}) and the Eclipse plugin module
 * ({@code acceleodoc.eclipse}). Callers provide a source folder containing
 * {@code .mtl} files and a target output directory; this class orchestrates
 * parsing, extraction, and HTML rendering.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DocGenerator generator = new DocGenerator();
 * generator.generate(Path.of("/path/to/src"), Path.of("/path/to/doc"));
 * }</pre>
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>{@link DocExtractor} scans for {@code .mtl} files and parses each
 *       using the Acceleo 4.x AQL AST API.</li>
 *   <li>Documentation comments ({@code [** ... *&#47;]}) are extracted into
 *       {@link ModuleDoc} objects.</li>
 *   <li>{@link HtmlRenderer} renders the documentation model into multi-page
 *       HTML using Freemarker templates.</li>
 * </ol>
 */
public class DocGenerator {

    private final DocExtractor extractor;
    private final HtmlRenderer renderer;

    public DocGenerator() {
        this.extractor = new DocExtractor();
        this.renderer  = new HtmlRenderer();
    }

    /**
     * Runs the full documentation generation pipeline.
     *
     * @param sourceRoot path to the folder containing {@code .mtl} files
     *                   (scanned recursively)
     * @param outputDir  path to the folder where HTML output is written
     *                   (created if it does not exist)
     * @throws IOException       if files cannot be read or written
     * @throws TemplateException if a Freemarker template fails to render
     * @return number of modules documented
     */
    public int generate(Path sourceRoot, Path outputDir) throws IOException {
        System.out.println("[acceleodoc] Scanning for .mtl files in: "
                + sourceRoot.toAbsolutePath());
        List<ModuleDoc> modules = extractor.extractAll(sourceRoot);

        if (modules.isEmpty()) {
            System.out.println("[acceleodoc] No .mtl files found — nothing to generate.");
            return 0;
        }

        System.out.println("[acceleodoc] Found " + modules.size()
                + " module(s). Rendering HTML...");
        try {
            renderer.render(modules, outputDir);
        } catch (freemarker.template.TemplateException e) {
            throw new IOException(
                    "Freemarker template rendering failed: " + e.getMessage(), e);
        }
        return modules.size();
    }
//    public int generate(Path sourceRoot, Path outputDir)
//            throws IOException, TemplateException {
//
//        System.out.println("[acceleodoc] Scanning for .mtl files in: " + sourceRoot.toAbsolutePath());
//        List<ModuleDoc> modules = extractor.extractAll(sourceRoot);
//
//        if (modules.isEmpty()) {
//            System.out.println("[acceleodoc] No .mtl files found — nothing to generate.");
//            return 0;
//        }
//
//        System.out.println("[acceleodoc] Found " + modules.size() + " module(s). Rendering HTML...");
//        renderer.render(modules, outputDir);
//
//        return modules.size();
//    }
}
