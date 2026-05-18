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
 * Parses the body text of an Acceleo {@code [** ... *&#47;]} documentation
 * comment into a structured {@link DocComment}.
 *
 * <h2>Comment format</h2>
 * <pre>
 * [**
 *  * Description text, possibly multi-line.
 *  *
 *  * @param paramName  Description of the parameter.
 *  * @return           Description of the return value.
 *  * @see              some::other::module
 *  * @author           Author Name
 *  * @version          1.0
 *  *&#47;]
 * </pre>
 *
 * <p>The leading {@code *} on each line is stripped before parsing.
 * Lines before the first {@code @} tag are treated as the description.</p>
 */
public final class DocCommentParser {

    /** Matches a tag line: {@code @tagName rest-of-line} */
    private static final Pattern TAG_LINE = Pattern.compile(
            "^@(\\w+)(?:\\s+(.*))?$");

    private DocCommentParser() {
        // static utility class — do not instantiate
    }

    /**
     * Parses the raw body text of a documentation comment.
     *
     * @param rawBody the comment body as extracted from the Acceleo AST,
     *                or {@code null} / blank if no documentation is present
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

        boolean inDescription         = true;
        String currentTag             = null;
        StringBuilder currentTagBody  = new StringBuilder();

        for (String rawLine : rawBody.split("\r?\n")) {
            // Strip leading whitespace and a single leading '*' (comment margin)
            String line = rawLine.stripLeading();
            if (line.startsWith("*")) {
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
                // Continuation line of the current tag body
                if (!currentTagBody.isEmpty()) {
                    currentTagBody.append(' ');
                }
                currentTagBody.append(line);
            } else {
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
            // body format: "paramName description text"
            int spaceIdx = body.indexOf(' ');
            if (spaceIdx > 0) {
                String paramName = body.substring(0, spaceIdx).strip();
                String paramDesc = body.substring(spaceIdx + 1).strip();
                params.put(paramName, paramDesc);
            } else if (!body.isBlank()) {
                params.put(body.strip(), "");
            }
        } else {
            tags.put(tag, body);
        }
    }
}
