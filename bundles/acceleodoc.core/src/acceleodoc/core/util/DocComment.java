package acceleodoc.core.util;

import java.util.Map;

/**
 * Structured representation of a parsed Acceleo {@code [** ... *&#47;]}
 * documentation comment.
 *
 * <p>Produced by {@link DocCommentParser#parse(String)} and consumed by
 * {@link acceleodoc.core.extract.DocExtractor} to populate the documentation
 * model ({@link acceleodoc.core.ast.ModuleDoc}, {@link acceleodoc.core.ast.TemplateDoc},
 * {@link acceleodoc.core.ast.QueryDoc}).</p>
 *
 * @param description      Free-text description preceding the first tag line.
 * @param paramDescriptions Map from parameter name to its {@code @param} description.
 * @param tags             Map from tag name (e.g. {@code "return"}, {@code "author"})
 *                         to tag body text.
 */
public record DocComment(
        String description,
        Map<String, String> paramDescriptions,
        Map<String, String> tags) {

    /** Canonical empty instance returned when no documentation is present. */
    public static final DocComment EMPTY =
            new DocComment("", Map.of(), Map.of());

    /**
     * Returns the description for a named parameter from a {@code @param} tag,
     * or an empty string if the parameter is not documented.
     *
     * @param paramName the parameter name as declared in the signature
     * @return description text, never null
     */
    public String paramDescription(String paramName) {
        return paramDescriptions.getOrDefault(paramName, "");
    }

    /**
     * Returns the body of a named tag (e.g. {@code "return"}, {@code "author"},
     * {@code "version"}), or {@code null} if the tag is not present.
     *
     * @param tagName tag name without the leading {@code @}
     * @return tag body text, or {@code null}
     */
    public String tag(String tagName) {
        return tags.get(tagName);
    }
}
