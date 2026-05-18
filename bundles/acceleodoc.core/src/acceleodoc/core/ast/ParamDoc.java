package acceleodoc.core.ast;

/**
 * Represents a single {@code @param} tag extracted from an Acceleo
 * {@code [** ... *&#47;]} documentation comment.
 *
 * <p>Instances are produced by {@link acceleodoc.core.extract.DocExtractor}
 * and consumed by the Freemarker HTML renderer.</p>
 *
 * @param name        The parameter name as declared in the template/query signature.
 * @param type        The OCL/AQL type string as resolved from the AST (e.g. {@code String},
 *                    {@code uml::Class}, {@code Sequence(Property)}).
 * @param description The description text from the {@code @param} tag body.
 *                    May be empty if the tag has no description text.
 */
public record ParamDoc(
        String name,
        String type,
        String description) {

    /** Returns {@code true} if a non-blank description is present. */
    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }
}
