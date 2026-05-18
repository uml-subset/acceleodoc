package acceleodoc.core.ast;

/**
 * Documentation model for an Acceleo {@code [query ...]} element.
 *
 * <p>Extends {@link ElementDoc} with query-specific metadata: the return
 * type and the optional {@code @return} tag description.</p>
 */
public final class QueryDoc extends ElementDoc {

    private final String returnType;
    private final String returnDescription;

    private QueryDoc(Builder builder) {
        super(builder);
        this.returnType        = builder.returnType;
        this.returnDescription = builder.returnDescription;
    }

    /**
     * Returns the AQL/OCL return type string as resolved from the AST
     * (e.g. {@code String}, {@code Boolean}, {@code Sequence(uml::Class)}).
     */
    public String getReturnType()        { return returnType; }

    /** Returns the description from the {@code @return} tag, or an empty string. */
    public String getReturnDescription() { return returnDescription; }

    public boolean hasReturnDescription() {
        return returnDescription != null && !returnDescription.isBlank();
    }

    @Override
    public String getKind() { return "query"; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder extends ElementDoc.Builder<Builder> {
        String returnType        = "";
        String returnDescription = "";

        public Builder returnType(String returnType)               { this.returnType = returnType;               return this; }
        public Builder returnDescription(String returnDescription) { this.returnDescription = returnDescription; return this; }

        public QueryDoc build() { return new QueryDoc(this); }
    }
}
