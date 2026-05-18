package acceleodoc.core.ast;

/**
 * Documentation model for an Acceleo {@code [template ...]} element.
 *
 * <p>Extends {@link ElementDoc} with template-specific metadata such as
 * whether the template is a main entry point ({@code [comment @main /]})
 * and whether it overrides a template from an extended module.</p>
 */
public final class TemplateDoc extends ElementDoc {

    private final boolean main;
    private final boolean override;

    private TemplateDoc(Builder builder) {
        super(builder);
        this.main     = builder.main;
        this.override = builder.override;
    }

    /** Returns {@code true} if this template is annotated with {@code @main}. */
    public boolean isMain()     { return main; }

    /** Returns {@code true} if this template overrides one from an extended module. */
    public boolean isOverride() { return override; }

    @Override
    public String getKind() { return "template"; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder extends ElementDoc.Builder<Builder> {
        boolean main     = false;
        boolean override = false;

        public Builder main(boolean main)         { this.main = main;         return this; }
        public Builder override(boolean override) { this.override = override; return this; }

        public TemplateDoc build() { return new TemplateDoc(this); }
    }
}
