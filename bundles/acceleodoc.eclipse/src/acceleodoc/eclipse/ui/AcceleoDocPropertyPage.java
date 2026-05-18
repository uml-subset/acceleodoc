package acceleodoc.eclipse.ui;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;

import acceleodoc.eclipse.Activator;

/**
 * Project property page: <em>Acceleo Documentation</em>.
 *
 * <p>Accessible via <strong>Project &gt; Properties &gt; Acceleo Documentation</strong>.
 * Provides two settings stored as Eclipse persistent project properties:</p>
 *
 * <ul>
 *   <li><strong>Enable automatic builder</strong> — adds/removes
 *       {@link acceleodoc.eclipse.builder.AcceleoDocBuilder} from the project's
 *       {@code .project} build spec.</li>
 *   <li><strong>Output folder</strong> — path relative to the project root
 *       where HTML documentation is written (default: {@code doc}).</li>
 * </ul>
 */
public class AcceleoDocPropertyPage extends PropertyPage {

    private Button  builderEnabledCheckbox;
    private Text    outputFolderText;

    // ── Page creation ─────────────────────────────────────────────────────────

    @Override
    protected Control createContents(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(2, false));
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        // Title
        Label title = new Label(composite, SWT.NONE);
        title.setText("Configure documentation generation for this project.");
        GridData titleGd = new GridData(GridData.FILL_HORIZONTAL);
        titleGd.horizontalSpan = 2;
        title.setLayoutData(titleGd);

        // Spacer
        new Label(composite, SWT.NONE).setLayoutData(spanGd(2));

        // Builder checkbox
        builderEnabledCheckbox = new Button(composite, SWT.CHECK);
        builderEnabledCheckbox.setText(
                "Enable automatic documentation generation on build");
        GridData cbGd = new GridData(GridData.FILL_HORIZONTAL);
        cbGd.horizontalSpan = 2;
        builderEnabledCheckbox.setLayoutData(cbGd);

        // Output folder
        Label folderLabel = new Label(composite, SWT.NONE);
        folderLabel.setText("Output folder (relative to project):");

        outputFolderText = new Text(composite, SWT.BORDER | SWT.SINGLE);
        outputFolderText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // Load current values
        loadSettings();

        return composite;
    }

    // ── Settings persistence ──────────────────────────────────────────────────

    private void loadSettings() {
        IProject project = getProject();
        try {
            String folder = project.getPersistentProperty(
                    new QualifiedName(Activator.PLUGIN_ID, Activator.PROP_OUTPUT_FOLDER));
            outputFolderText.setText(
                    folder != null && !folder.isBlank() ? folder : Activator.DEFAULT_OUTPUT_FOLDER);

            String enabledStr = project.getPersistentProperty(
                    new QualifiedName(Activator.PLUGIN_ID, Activator.PROP_BUILDER_ENABLED));
            builderEnabledCheckbox.setSelection("true".equals(enabledStr));

        } catch (CoreException e) {
            outputFolderText.setText(Activator.DEFAULT_OUTPUT_FOLDER);
            builderEnabledCheckbox.setSelection(false);
        }
    }

    @Override
    public boolean performOk() {
        IProject project = getProject();
        try {
            // Persist output folder
            String folder = outputFolderText.getText().strip();
            if (folder.isBlank()) folder = Activator.DEFAULT_OUTPUT_FOLDER;
            project.setPersistentProperty(
                    new QualifiedName(Activator.PLUGIN_ID, Activator.PROP_OUTPUT_FOLDER),
                    folder);

            // Persist builder-enabled flag
            boolean enabled = builderEnabledCheckbox.getSelection();
            project.setPersistentProperty(
                    new QualifiedName(Activator.PLUGIN_ID, Activator.PROP_BUILDER_ENABLED),
                    String.valueOf(enabled));

            // Add or remove the builder from the project's build spec
            if (enabled) {
                addBuilderToProject(project);
            } else {
                removeBuilderFromProject(project);
            }

        } catch (CoreException e) {
            setErrorMessage("Could not save settings: " + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    protected void performDefaults() {
        outputFolderText.setText(Activator.DEFAULT_OUTPUT_FOLDER);
        builderEnabledCheckbox.setSelection(false);
    }

    // ── Builder management ────────────────────────────────────────────────────

    private void addBuilderToProject(IProject project) throws CoreException {
        IProjectDescription desc = project.getDescription();
        ICommand[] existing = desc.getBuildSpec();

        for (ICommand cmd : existing) {
            if (Activator.BUILDER_ID.equals(cmd.getBuilderName())) {
                return; // already present
            }
        }

        ICommand newCmd = desc.newCommand();
        newCmd.setBuilderName(Activator.BUILDER_ID);

        ICommand[] updated = new ICommand[existing.length + 1];
        System.arraycopy(existing, 0, updated, 0, existing.length);
        updated[existing.length] = newCmd;

        desc.setBuildSpec(updated);
        project.setDescription(desc, null);
    }

    private void removeBuilderFromProject(IProject project) throws CoreException {
        IProjectDescription desc = project.getDescription();
        ICommand[] existing = desc.getBuildSpec();

        java.util.List<ICommand> updated = new java.util.ArrayList<>();
        for (ICommand cmd : existing) {
            if (!Activator.BUILDER_ID.equals(cmd.getBuilderName())) {
                updated.add(cmd);
            }
        }

        desc.setBuildSpec(updated.toArray(ICommand[]::new));
        project.setDescription(desc, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private IProject getProject() {
        return (IProject) getElement().getAdapter(IProject.class);
    }

    private static GridData spanGd(int span) {
        GridData gd = new GridData();
        gd.horizontalSpan = span;
        return gd;
    }
}
