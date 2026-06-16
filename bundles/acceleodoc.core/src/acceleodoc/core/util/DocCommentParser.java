package acceleodoc.core.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses the body text of an Acceleo {@code [** ... /]} documentation
 * comment into a structured {@link DocComment}.
 *
 * <h2>Acceleo 4.x comment format</h2>
 * <pre>
 * [**
 * Description text, possibly multi-line.
 *
 * @param paramName  Description of the parameter,
 *                   possibly spanning multiple lines.
 * @return           Description of the return value.
 * @see              some::other::module
 * @author           Author Name
 * @version          1.0
 * /]
 * </pre>
 *
 * <p>Note: Acceleo 4.x documentation comments do NOT use leading {@code *}
 * on each line (unlike Javadoc). Lines are plain text. The opening delimiter
 * is {@code [**} and the closing delimiter is {@code /]}.</p>
 *
 * <h2>Parameter name normalisation</h2>
 * <p>If a {@code @param} description starts with the parameter name (possibly
 * followed by a separator such as {@code -}, {@code —}, {@code :}), the
 * redundant name prefix is stripped since the name is already shown as a
 * heading in the rendered documentation.</p>
 *
 * <h2>Multi-line parameter descriptions</h2>
 * <p>Parameter descriptions that span multiple lines are fully supported.
 * Continuation lines (lines that do not start with {@code @}) are joined
 * with a single space and normalised.</p>
 */
public final class DocCommentParser {

    /** Matches a tag line: {@code @tagName rest-of-line} */
    private static final Pattern TAG_LINE = Pattern.compile(
            "^@(\\w+)(?:\\s+(.*))?$");

    private DocCommentParser() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses a full Acceleo documentation block including the opening
     * {@code [**} and closing {@code /]} delimiters as read directly from
     * the {@code .mtl} source file.
     *
     * <p>This is the preferred entry point when source text is available
     * via {@link org.eclipse.acceleo.aql.parser.AcceleoAstResult} position
     * information, as it correctly handles multi-line {@code @param}
     * descriptions that the Acceleo AST {@code CommentBody.getValue()} may
     * truncate to a single line.</p>
     *
     * <p>Strips the {@code [**} and {@code /]} delimiters, then delegates
     * to {@link #parse(String)}.</p>
     *
     * @param fullSource the complete documentation block source text
     *                   including delimiters, as read from the {@code .mtl}
     *                   source file
     * @return a structured {@link DocComment} (never null)
     */
    public static DocComment parseFullDocBlock(String fullSource) {
        if (fullSource == null || fullSource.isBlank()) {
            return DocComment.EMPTY;
        }
        String body = fullSource.strip();

        // Strip opening [**
        if (body.startsWith("[**")) {
            body = body.substring(3);
        }

        // Strip closing /]
        if (body.endsWith("/]")) {
            body = body.substring(0, body.length() - 2);
        }

        return parse(body.strip());
    }

    /**
     * Parses the raw body text of a documentation comment.
     *
     * <p>The body text is the content between the {@code [**} and {@code /]}
     * delimiters, with those delimiters already removed. In Acceleo 4.x,
     * lines do not have leading {@code *} characters.</p>
     *
     * @param rawBody the comment body, or {@code null} / blank if absent
     * @return a {@link DocComment} (never null; returns {@link DocComment#EMPTY}
     *         for null or blank input)
     */
    public static DocComment parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return DocComment.EMPTY;
        }

        List<String> descriptionLines = new ArrayList<>();
        Map<String, String> params    = new LinkedHashMap<>();
        Map<String, String> tags      = new HashMap<>();

        boolean inDescription        = true;
        String currentTag            = null;
        StringBuilder currentTagBody = new StringBuilder();

        for (String rawLine : rawBody.split("\r?\n")) {
            // In Acceleo 4.x documentation comments, lines do NOT have
            // leading '*' characters — strip leading whitespace only.
            // We also handle the case where someone writes Javadoc-style
            // comments with leading '*' for compatibility.
            String line = rawLine.stripLeading();
            if (line.startsWith("*") && !line.startsWith("*/")) {
                // Optional: strip a single leading '*' for Javadoc-style compat
                line = line.substring(1).stripLeading();
            }

            Matcher tagMatcher = TAG_LINE.matcher(line);
            if (tagMatcher.matches()) {
                // Flush the previous tag before starting a new one
                if (currentTag != null) {
                    flushTag(currentTag, currentTagBody.toString().strip(),
                             params, tags);
                }
                inDescription = false;
                currentTag    = tagMatcher.group(1);
                currentTagBody = new StringBuilder(
                        tagMatcher.group(2) != null ? tagMatcher.group(2) : "");
            } else if (!inDescription && currentTag != null) {
                // Continuation line of the current tag body.
                // Append with a space, normalising internal whitespace.
                String trimmed = line.strip();
                if (!trimmed.isBlank()) {
                    if (!currentTagBody.isEmpty()) {
                        currentTagBody.append(' ');
                    }
                    currentTagBody.append(trimmed);
                }
            } else {
                // Description line (before any tag)
                descriptionLines.add(line);
            }
        }

        // Flush the last tag
        if (currentTag != null) {
            flushTag(currentTag, currentTagBody.toString().strip(), params, tags);
        }

        String description = descriptionLines.stream()
                .map(String::strip)
                .dropWhile(String::isBlank)
                .collect(Collectors.joining(" "))
                .strip();

        return new DocComment(description, params, tags);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void flushTag(String tag, String body,
                                  Map<String, String> params,
                                  Map<String, String> tags) {
        if ("param".equals(tag)) {
            // body format: "paramName [separator] description text"
            // The description may span multiple lines already joined by parse()
            int spaceIdx = body.indexOf(' ');
            if (spaceIdx > 0) {
                String paramName = body.substring(0, spaceIdx).strip();
                String paramDesc = body.substring(spaceIdx + 1).strip();
                // Strip redundant leading parameter name from the description
                // if the developer repeated it (e.g. "aType - A UML type...")
                paramDesc = stripRedundantParamName(paramName, paramDesc);
                params.put(paramName, paramDesc);
            } else if (!body.isBlank()) {
                // @param with name only, no description
                params.put(body.strip(), "");
            }
        } else {
            tags.put(tag, body);
        }
    }

    /**
     * Strips a redundant parameter name prefix from a param description.
     *
     * <p>If the description starts with the parameter name (case-sensitive)
     * followed optionally by a separator ({@code -}, {@code —}, {@code :})
     * and whitespace, the name and separator are removed. This avoids
     * redundant output like:</p>
     * <pre>
     * aType - A UML type...   →   A UML type...
     * aType: A UML type...    →   A UML type...
     * aType — A UML type...   →   A UML type...
     * </pre>
     *
     * @param paramName the parameter name
     * @param desc      the raw description text (already multi-line joined)
     * @return the description with redundant name prefix removed, or the
     *         original description if no prefix was detected
     */
    private static String stripRedundantParamName(String paramName, String desc) {
        if (desc == null || desc.isBlank()) return desc;
        if (!desc.startsWith(paramName)) return desc;

        // What follows the param name?
        String rest = desc.substring(paramName.length()).stripLeading();

        if (rest.startsWith("-") || rest.startsWith("—") || rest.startsWith(":")) {
            return rest.substring(1).stripLeading();
        }

        // Only whitespace followed the name — keep original to avoid
        // stripping a description that IS just the param name
        if (rest.isBlank()) return desc;

        return rest;
    }
}
