package acceleodoc.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import acceleodoc.core.DocGenerator;

/**
 * CLI entry point for acceleodoc.
 *
 * <p>Accepts two required arguments and delegates to {@link DocGenerator}:</p>
 *
 * <pre>
 * -input  &lt;path&gt;   Root directory containing .mtl source files (scanned recursively)
 * -output &lt;path&gt;   Output directory for generated HTML documentation
 * </pre>
 *
 * <h2>Eclipse application launch</h2>
 * <pre>
 *   eclipse -application acceleodoc.cli.app \
 *           -input  /workspace/ucmism2t/bundles/ucmism2t.core/src \
 *           -output /workspace/ucmism2t/doc
 * </pre>
 *
 * <h2>Standalone JAR / product launch</h2>
 * <pre>
 *   acceleodoc -input /path/to/src -output /path/to/doc
 * </pre>
 *
 * Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — argument error or generation failure</li>
 * </ul>
 */
public class Main implements IApplication {

    private static final String ARG_INPUT  = "-input";
    private static final String ARG_OUTPUT = "-output";

    // ── IApplication ─────────────────────────────────────────────────────────

    @Override
    public Object start(IApplicationContext context) throws Exception {
        String[] args = (String[]) context.getArguments()
                .get(IApplicationContext.APPLICATION_ARGS);
        return run(args) ? IApplication.EXIT_OK : 1;
    }

    @Override
    public void stop() { /* nothing to clean up */ }

    // ── Static main (standalone JAR / product) ────────────────────────────────

    public static void main(String[] args) {
        boolean ok = new Main().run(args);
        System.exit(ok ? 0 : 1);
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private boolean run(String[] args) {
        printBanner();

        Arguments parsed = parseArguments(args);
        if (parsed == null) {
            printUsage();
            return false;
        }

        System.out.println("Input  : " + parsed.input);
        System.out.println("Output : " + parsed.output);
        System.out.println();

        try {
            DocGenerator generator = new DocGenerator();
            int count = generator.generate(parsed.input, parsed.output);
            if (count == 0) {
                System.out.println("[acceleodoc] Warning: no .mtl files found under " + parsed.input);
            }
            System.out.println("[acceleodoc] Done.");
            return true;
        } catch (Exception e) {
            System.err.println("[acceleodoc] Error: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    private Arguments parseArguments(String[] args) {
        if (args == null) return null;
        String input  = null;
        String output = null;

        for (int i = 0; i < args.length; i++) {
            if (ARG_INPUT.equals(args[i]) && i + 1 < args.length) {
                input = args[++i];
            } else if (ARG_OUTPUT.equals(args[i]) && i + 1 < args.length) {
                output = args[++i];
            } else {
                System.err.println("[acceleodoc] Unknown argument: " + args[i]);
                return null;
            }
        }

        if (input == null)  { System.err.println("[acceleodoc] Missing required argument: " + ARG_INPUT);  return null; }
        if (output == null) { System.err.println("[acceleodoc] Missing required argument: " + ARG_OUTPUT); return null; }

        return new Arguments(Paths.get(input), Paths.get(output));
    }

    private void printBanner() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              acceleodoc — Version 1.0.0                      ║");
        System.out.println("║  Javadoc-style documentation generator for Acceleo 4.2       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printUsage() {
        System.out.println("Usage: acceleodoc -input <source-dir> -output <output-dir>");
        System.out.println();
        System.out.println("  -input  <path>   Root directory containing .mtl files (scanned recursively)");
        System.out.println("  -output <path>   Directory where HTML documentation is written");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  acceleodoc -input ucmism2t/bundles/ucmism2t.core/src -output ucmism2t/doc");
    }

    private record Arguments(Path input, Path output) {}
}
