package acceleodoc.core.ast;

import java.util.List;

/**
 * Documentation model for an Acceleo {@code [module ...]} element.
 *
 * <p>Represents a single {@code .mtl} file. Holds the module-level
 * {@code [** ... *&#47;]} documentation comment and aggregates all
 * {@link TemplateDoc} and {@link QueryDoc} elements declared within it.</p>
 */
public final class ModuleDoc {

    private final String name;
    private final String qualifiedName;
    private final String sourceFile;
    private final String description;
    private final String author;
    private final String version;
    private final String deprecated;
    private final String see;
    private final List<String> metamodels;
    private final List<TemplateDoc> templates;
    private final List<QueryDoc> queries;

    private ModuleDoc(Builder builder) {
        this.name          = builder.name;
        this.qualifiedName = builder.qualifiedName;
        this.sourceFile    = builder.sourceFile;
        this.description   = builder.description;
        this.author        = builder.author;
        this.version       = builder.version;
        this.deprecated    = builder.deprecated;
        this.see           = builder.see;
        this.metamodels    = List.copyOf(builder.metamodels);
        this.templates     = List.copyOf(builder.templates);
        this.queries       = List.copyOf(builder.queries);
    }

    /** Short module name (last segment of the qualified name). */
    public String getName()          { return name; }

    /** Fully qualified module name (e.g. {@code ucmism2t::templates::m2t}). */
    public String getQualifiedName() { return qualifiedName; }

    /** Relative path to the source {@code .mtl} file. */
    public String getSourceFile()    { return sourceFile; }

    public String getDescription()   { return description; }
    public String getAuthor()        { return author; }
    public String getVersion()       { return version; }
    public String getDeprecated()    { return deprecated; }
    public String getSee()           { return see; }

    /** Metamodel URIs declared in the module header. */
    public List<String> getMetamodels()     { return metamodels; }
    public List<TemplateDoc> getTemplates() { return templates; }
    public List<QueryDoc> getQueries()      { return queries; }

    public boolean hasDescription()  { return description != null && !description.isBlank(); }
    public boolean hasAuthor()       { return author != null && !author.isBlank(); }
    public boolean hasVersion()      { return version != null && !version.isBlank(); }
    public boolean isDeprecated()    { return deprecated != null && !deprecated.isBlank(); }
    public boolean hasSee()          { return see != null && !see.isBlank(); }

    /** Returns the HTML output file name for this module (e.g. {@code m2t.html}). */
    public String getHtmlFileName()  { return name + ".html"; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        String name          = "";
        String qualifiedName = "";
        String sourceFile    = "";
        String description   = "";
        String author        = null;
        String version       = null;
        String deprecated    = null;
        String see           = null;
        List<String> metamodels    = List.of();
        List<TemplateDoc> templates = List.of();
        List<QueryDoc> queries      = List.of();

        public Builder name(String name)                   { this.name = name;                   return this; }
        public Builder qualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; return this; }
        public Builder sourceFile(String sourceFile)       { this.sourceFile = sourceFile;       return this; }
        public Builder description(String description)     { this.description = description;     return this; }
        public Builder author(String author)               { this.author = author;               return this; }
        public Builder version(String version)             { this.version = version;             return this; }
        public Builder deprecated(String deprecated)       { this.deprecated = deprecated;       return this; }
        public Builder see(String see)                     { this.see = see;                     return this; }
        public Builder metamodels(List<String> metamodels) { this.metamodels = metamodels;       return this; }
        public Builder templates(List<TemplateDoc> templates) { this.templates = templates;      return this; }
        public Builder queries(List<QueryDoc> queries)     { this.queries = queries;             return this; }

        public ModuleDoc build() { return new ModuleDoc(this); }
    }
}
