package acceleodoc.core.ast;

import java.util.List;

import acceleodoc.core.extract.CallReference;

/**
 * Documentation model for an Acceleo {@code [query ...]} element.
 *
 * <p>Extends {@link ElementDoc} with query-specific metadata: the return
 * type, optional {@code @return} tag description, extracted call references
 * from the query body AST, and the raw source text of the query.</p>
 */
public final class QueryDoc extends ElementDoc {

    private final String returnType;
    private final String returnDescription;
    private final List<CallReference> callReferences;
    private final String sourceText;

    private QueryDoc(Builder builder) {
        super(builder);
        this.returnType        = builder.returnType;
        this.returnDescription = builder.returnDescription;
        this.callReferences    = List.copyOf(builder.callReferences);
        this.sourceText        = builder.sourceText;
    }

    /**
     * Returns the AQL/OCL return type string as resolved from the AST
     * (e.g. {@code String}, {@code Boolean}, {@code OrderedSet(uml::Class)}).
     */
    public String getReturnType()        { return returnType; }

    /** Returns the description from the {@code @return} tag, or empty string. */
    public String getReturnDescription() { return returnDescription; }

    /**
     * Returns all call references extracted from the query body AST,
     * categorised and deduplicated.
     */
    public List<CallReference> getCallReferences() { return callReferences; }

    /**
     * Returns the raw source text of the full query declaration including
     * opening {@code [query ...]} and closing {@code [/query]} tags.
     */
    public String getSourceText() { return sourceText; }

    public boolean hasReturnDescription() {
        return returnDescription != null && !returnDescription.isBlank();
    }

    public boolean hasCallReferences() { return !callReferences.isEmpty(); }
    public boolean hasSourceText()     { return sourceText != null && !sourceText.isBlank(); }

    /**
     * Returns call references filtered to a specific category name string.
     * Used from Freemarker templates where enum access is not direct.
     */
    public List<CallReference> getCallReferencesByCategory(String categoryName) {
        List<CallReference> result = callReferences.stream()
                .filter(r -> r.category().name().equals(categoryName))
                .toList();
        if (!callReferences.isEmpty()) {
            System.err.println("[acceleodoc DEBUG] getCallReferencesByCategory("
                    + categoryName + ") on " + getName()
                    + ": total=" + callReferences.size()
                    + " matched=" + result.size());
        }
        return result;
    }
//    public List<CallReference> getCallReferencesByCategory(String categoryName) {
//        return callReferences.stream()
//                .filter(r -> r.category().name().equals(categoryName))
//                .toList();
//    }

    @Override
    public String getKind() { return "query"; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder extends ElementDoc.Builder<Builder> {
        String returnType        = "";
        String returnDescription = "";
        List<CallReference> callReferences = List.of();
        String sourceText        = "";

        public Builder returnType(String returnType)
                { this.returnType = returnType; return this; }
        public Builder returnDescription(String returnDescription)
                { this.returnDescription = returnDescription; return this; }
        public Builder callReferences(List<CallReference> callReferences)
                { this.callReferences = callReferences; return this; }
        public Builder sourceText(String sourceText)
                { this.sourceText = sourceText; return this; }

        public QueryDoc build() { return new QueryDoc(this); }
    }
}
