package acceleodoc.core.extract;

/**
 * Represents a single service or operation call extracted from an Acceleo
 * template or query body AST, categorised and resolved to a documentation URL.
 *
 * <p>Instances are produced by {@link AstCallWalker} and consumed by the
 * Freemarker HTML renderer to generate cross-reference sections in the
 * generated documentation.</p>
 *
 * @param name     The service or operation name as it appears in the source
 *                 (e.g. {@code "select"}, {@code "eClass"}, {@code "getModel"}).
 * @param category The resolved {@link CallCategory}.
 * @param url      The resolved documentation URL, or {@code null} if no URL
 *                 is available (e.g. for {@link CallCategory#PROJECT_SERVICE}
 *                 and {@link CallCategory#UNKNOWN}).
 */
public record CallReference(
        String name,
        CallCategory category,
        String url) {

    /** Returns {@code true} if a documentation URL is available. */
    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    /**
     * Returns a display label for the call reference, including parentheses
     * for method/operation calls and plain name for feature accesses.
     *
     * <p>Collection calls ({@link CallCategory#AQL_COLLECTION}) and service
     * calls ({@link CallCategory#AQL_SERVICE}, {@link CallCategory#EXTERNAL_UML2},
     * {@link CallCategory#EXTERNAL_EMF}, {@link CallCategory#PROJECT_SERVICE})
     * are displayed with {@code ()} appended. Internal query and template
     * references are displayed as plain names.</p>
     */
    public String getDisplayName() {
        return switch (category) {
            case INTERNAL_QUERY, INTERNAL_TEMPLATE -> name;
            default -> name + "()";
        };
    }

    /** Returns the category name for use as a CSS class in templates. */
    public String getCategoryClass() {
        return category.name().toLowerCase().replace('_', '-');
    }
}
