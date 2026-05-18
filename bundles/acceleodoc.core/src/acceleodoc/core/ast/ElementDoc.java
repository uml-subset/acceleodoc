package acceleodoc.core.ast;

import java.util.List;

/**
 * Base documentation model for a documented Acceleo module element
 * (either a {@link TemplateDoc} or a {@link QueryDoc}).
 *
 * <p>Captures all information extracted from the element's signature
 * and its {@code [** ... *&#47;]} documentation comment.</p>
 */
public abstract sealed class ElementDoc permits TemplateDoc, QueryDoc {

    private final String name;
    private final String visibility;
    private final String description;
    private final List<ParamDoc> params;
    private final String deprecated;
    private final String see;
    private final String author;
    private final String version;

    protected ElementDoc(Builder<?> builder) {
        this.name        = builder.name;
        this.visibility  = builder.visibility;
        this.description = builder.description;
        this.params      = List.copyOf(builder.params);
        this.deprecated  = builder.deprecated;
        this.see         = builder.see;
        this.author      = builder.author;
        this.version     = builder.version;
    }

    public String getName()        { return name; }
    public String getVisibility()  { return visibility; }
    public String getDescription() { return description; }
    public List<ParamDoc> getParams() { return params; }
    public String getDeprecated()  { return deprecated; }
    public String getSee()         { return see; }
    public String getAuthor()      { return author; }
    public String getVersion()     { return version; }

    public boolean hasDescription()  { return description != null && !description.isBlank(); }
    public boolean isDeprecated()    { return deprecated != null && !deprecated.isBlank(); }
    public boolean hasSee()          { return see != null && !see.isBlank(); }
    public boolean hasAuthor()       { return author != null && !author.isBlank(); }
    public boolean hasVersion()      { return version != null && !version.isBlank(); }

    /** Returns {@code "template"} or {@code "query"} for use in HTML rendering. */
    public abstract String getKind();

    // -------------------------------------------------------------------------
    // Builder base
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public abstract static class Builder<T extends Builder<T>> {
        String name        = "";
        String visibility  = "public";
        String description = "";
        List<ParamDoc> params = List.of();
        String deprecated  = null;
        String see         = null;
        String author      = null;
        String version     = null;

        public T name(String name)               { this.name = name;               return (T) this; }
        public T visibility(String visibility)   { this.visibility = visibility;   return (T) this; }
        public T description(String description) { this.description = description; return (T) this; }
        public T params(List<ParamDoc> params)   { this.params = params;           return (T) this; }
        public T deprecated(String deprecated)   { this.deprecated = deprecated;   return (T) this; }
        public T see(String see)                 { this.see = see;                 return (T) this; }
        public T author(String author)           { this.author = author;           return (T) this; }
        public T version(String version)         { this.version = version;         return (T) this; }
    }
}
