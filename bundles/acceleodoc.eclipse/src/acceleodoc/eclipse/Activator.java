package acceleodoc.eclipse;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for {@code acceleodoc.eclipse}.
 *
 * <p>Provides access to the singleton plugin instance for use by
 * actions, the builder, and the property page.</p>
 */
public class Activator extends Plugin {

    /** The bundle symbolic name — matches {@code MANIFEST.MF}. */
    public static final String PLUGIN_ID = "acceleodoc.eclipse";

    /** Builder ID — matches the {@code id} in {@code plugin.xml}. */
    public static final String BUILDER_ID = "acceleodoc.eclipse.builder";

    /** Project persistent property key for storing the output folder. */
    public static final String PROP_OUTPUT_FOLDER = PLUGIN_ID + ".outputFolder";

    /** Project persistent property key for the builder-enabled flag. */
    public static final String PROP_BUILDER_ENABLED = PLUGIN_ID + ".builderEnabled";

    /** Default output folder name relative to the project root. */
    public static final String DEFAULT_OUTPUT_FOLDER = "doc";

    private static Activator instance;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }

    public static Activator getInstance() {
        return instance;
    }
}
