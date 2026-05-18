package acceleodoc.core.util;

/**
 * A single entry in the Lunr.js static search index.
 *
 * <p>The search index is serialized to JSON and embedded in the generated
 * HTML output so that the JS search widget can query it client-side without
 * a server-side component.</p>
 *
 * @param id          Unique identifier used by Lunr (e.g. {@code "m2t#getAllClasses"}).
 * @param kind        Element kind: {@code "module"}, {@code "template"}, or {@code "query"}.
 * @param name        Short element name (highest search priority).
 * @param moduleName  Name of the containing module.
 * @param description Free-text description from the documentation comment.
 * @param params      Concatenated parameter names and descriptions (lower priority).
 * @param url         Relative URL to the HTML page and anchor (e.g. {@code "m2t.html#getAllClasses"}).
 */
public record SearchIndexEntry(
        String id,
        String kind,
        String name,
        String moduleName,
        String description,
        String params,
        String url) {
}
