package acceleodoc.eclipse.builder;

import java.nio.file.Path;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;

import acceleodoc.core.DocGenerator;
import acceleodoc.eclipse.Activator;

/**
 * Eclipse incremental project builder for acceleodoc.
 *
 * <p>When enabled on a project (via Project Properties &gt; Acceleo
 * Documentation), this builder automatically regenerates HTML documentation
 * whenever a {@code .mtl} file in the project changes.</p>
 *
 * <h2>Enabling the builder</h2>
 * <p>The builder is <strong>disabled by default</strong>. It is added to a
 * project's {@code .project} file only when the user explicitly enables it
 * in {@link AcceleoDocPropertyPage}.</p>
 *
 * <h2>Build behaviour</h2>
 * <ul>
 *   <li><strong>Full build</strong> — always regenerates all documentation.</li>
 *   <li><strong>Incremental / auto build</strong> — regenerates only if the
 *       resource delta contains at least one {@code .mtl} file change.</li>
 *   <li><strong>Clean build</strong> — no-op (output files are left in place;
 *       the next full build will overwrite them).</li>
 * </ul>
 */
public class AcceleoDocBuilder extends IncrementalProjectBuilder {

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
            throws CoreException {

        if (kind == FULL_BUILD) {
            generate(monitor);
        } else {
            // Incremental / auto build: only regenerate if .mtl files changed
            IResourceDelta delta = getDelta(getProject());
            if (delta != null && hasMtlChanges(delta)) {
                generate(monitor);
            }
        }
        return null; // no referenced projects
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // No-op: we do not delete the doc/ folder on clean
        // so previously generated docs remain browsable.
    }

    // ── Generation ────────────────────────────────────────────────────────────

    private void generate(IProgressMonitor monitor) throws CoreException {
        IProject project  = getProject();
        Path sourceRoot   = project.getLocation().toFile().toPath();
        Path outputDir    = resolveOutputDir(project);

        monitor.beginTask("acceleodoc: generating documentation…", IProgressMonitor.UNKNOWN);
        try {
            new DocGenerator().generate(sourceRoot, outputDir);
            project.refreshLocal(org.eclipse.core.resources.IResource.DEPTH_INFINITE, monitor);
        } catch (Exception e) {
            throw new CoreException(Status.error(
                    "acceleodoc build failed: " + e.getMessage(), e));
        } finally {
            monitor.done();
        }
    }

    // ── Delta check ───────────────────────────────────────────────────────────

    private boolean hasMtlChanges(IResourceDelta delta) {
        // Use a visitor to detect any .mtl changes in the delta
        final boolean[] found = { false };
        try {
            delta.accept(d -> {
                if (found[0]) return false; // short-circuit
                String name = d.getResource().getName();
                if (name.endsWith(".mtl")) {
                    found[0] = true;
                    return false;
                }
                return true; // continue visiting children
            });
        } catch (CoreException ignored) {}
        return found[0];
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    private Path resolveOutputDir(IProject project) throws CoreException {
        String folder = Activator.DEFAULT_OUTPUT_FOLDER;
        try {
            String stored = project.getPersistentProperty(
                    new QualifiedName(Activator.PLUGIN_ID, Activator.PROP_OUTPUT_FOLDER));
            if (stored != null && !stored.isBlank()) {
                folder = stored;
            }
        } catch (CoreException ignored) { /* use default */ }
        return project.getLocation().toFile().toPath().resolve(folder);
    }
}
