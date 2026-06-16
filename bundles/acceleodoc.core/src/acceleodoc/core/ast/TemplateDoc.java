package acceleodoc.core.ast;

import java.util.List;

import acceleodoc.core.extract.CallReference;

/**
 * Documentation model for an Acceleo {@code [template ...]} element.
 *
 * <p>Extends {@link ElementDoc} with template-specific metadata: whether the
 * template is a main entry point, whether it overrides another template,
 * extracted call references from the template body AST, and the raw source
 * text of the full template declaration.</p>
 */
public final class TemplateDoc extends ElementDoc {

    private final boolean main;
    private final boolean override;
    private final List<CallReference> callReferences;
    private final String sourceText;

    private TemplateDoc(Builder builder) {
        super(builder);
        this.main           = builder.main;
        this.override       = builder.override;
        this.callReferences = List.copyOf(builder.callReferences);
        this.sourceText     = builder.sourceText;
    }

    /** Returns {@code true} if this template is annotated with {@code @main}. */
    public boolean isMain()     { return main; }

    /** Returns {@code true} if this template overrides one from an extended module. */
    public boolean isOverride() { return override; }

    /**
     * Returns all call references extracted from the template body AST,
     * categorised and deduplicated.
     */
    public List<CallReference> getCallReferences() { return callReferences; }

    /**
     * Returns the raw source text of the full template declaration including
     * opening {@code [template ...]} and closing {@code [/template]} tags.
     */
    public String getSourceText() { return sourceText; }

    public boolean hasCallReferences() { return !callReferences.isEmpty(); }
    public boolean hasSourceText()     { return sourceText != null && !sourceText.isBlank(); }

    /**
     * Returns call references filtered to a specific category name string.
     * Used from Freemarker templates where enum access is not direct.
     */
    public List<CallReference> getCallReferencesByCategory(String categoryName) {
        return callReferences.stream()
                .filter(r -> r.category().name().equals(categoryName))
                .toList();
    }

    @Override
    public String getKind() { return "template"; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder extends ElementDoc.Builder<Builder> {
        boolean main     = false;
        boolean override = false;
        List<CallReference> callReferences = List.of();
        String sourceText = "";

        public Builder main(boolean main)         { this.main = main;         return this; }
        public Builder override(boolean override) { this.override = override; return this; }
        public Builder callReferences(List<CallReference> refs)
                { this.callReferences = refs; return this; }
        public Builder sourceText(String sourceText)
                { this.sourceText = sourceText; return this; }

        public TemplateDoc build() { return new TemplateDoc(this); }
    }
}
