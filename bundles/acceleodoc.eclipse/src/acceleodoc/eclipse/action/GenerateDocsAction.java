package acceleodoc.eclipse.action;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import acceleodoc.core.DocGenerator;
import acceleodoc.eclipse.Activator;

/**
 * Eclipse context menu action: <em>Generate Acceleo Documentation</em>.
 *
 * <p>Registered for both {@code IProject} and {@code IFile} (*.mtl) objects
 * via {@code plugin.xml}. When invoked:</p>
 * <ol>
 *   <li>Resolves the source root and the target project.</li>
 *   <li>Determines the output folder from the project's persistent property
 *       ({@link Activator#PROP_OUTPUT_FOLDER}), defaulting to {@code doc/}.</li>
 *   <li>Runs {@link DocGenerator} in a progress dialog.</li>
 *   <li>Refreshes the project so the {@code doc/} folder appears in the
 *       Project Explorer without a manual refresh.</li>
 * </ol>
 */
public class GenerateDocsAction implements IObjectActionDelegate {

    private ISelection currentSelection;
    private IWorkbenchPart targetPart;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        this.targetPart = targetPart;
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        this.currentSelection = selection;
    }

    @Override
    public void run(IAction action) {
        IProject project = resolveProject();
        if (project == null) {
            MessageDialog.openError(
                    targetPart.getSite().getShell(),
                    "acceleodoc",
                    "Could not determine project from selection.");
            return;
        }

        Path sourceRoot  = resolveSourceRoot(project);
        Path outputDir   = resolveOutputDir(project);

        try {
            PlatformUI.getWorkbench().getProgressService().busyCursorWhile(
                new GenerateRunnable(project, sourceRoot, outputDir));

            // Refresh so the doc/ folder appears immediately
            project.refreshLocal(IResource.DEPTH_INFINITE, null);

            MessageDialog.openInformation(
                    targetPart.getSite().getShell(),
                    "acceleodoc",
                    "Documentation generated in:\n" + outputDir.toAbsolutePath());

        } catch (InvocationTargetException e) {
            MessageDialog.openError(
                    targetPart.getSite().getShell(),
                    "acceleodoc — Error",
                    "Documentation generation failed:\n" + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (CoreException e) {
            MessageDialog.openError(
                    targetPart.getSite().getShell(),
                    "acceleodoc — Error",
                    "Could not refresh project: " + e.getMessage());
        }
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private IProject resolveProject() {
        if (!(currentSelection instanceof IStructuredSelection sel)) return null;
        Object first = sel.getFirstElement();

        if (first instanceof IProject p) return p;
        if (first instanceof IFile f)   return f.getProject();
        if (first instanceof IAdaptable a) {
            IProject p = a.getAdapter(IProject.class);
            if (p != null) return p;
            IResource r = a.getAdapter(IResource.class);
            if (r != null) return r.getProject();
        }
        return null;
    }

    private Path resolveSourceRoot(IProject project) {
        // Source root is the project location itself; DocExtractor walks recursively
        return project.getLocation().toFile().toPath();
    }

    private Path resolveOutputDir(IProject project) {
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

    // ── Runnable ──────────────────────────────────────────────────────────────

    private record GenerateRunnable(
            IProject project,
            Path sourceRoot,
            Path outputDir) implements IRunnableWithProgress {

        @Override
        public void run(IProgressMonitor monitor)
                throws InvocationTargetException, InterruptedException {
            monitor.beginTask("Generating Acceleo documentation…", IProgressMonitor.UNKNOWN);
            try {
                new DocGenerator().generate(sourceRoot, outputDir);
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            } finally {
                monitor.done();
            }
        }
    }
}
