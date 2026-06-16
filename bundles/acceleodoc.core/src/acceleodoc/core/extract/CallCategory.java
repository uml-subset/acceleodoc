package acceleodoc.core.extract;

/**
 * Categorises a service call extracted from an Acceleo template or query body
 * for the purpose of generating cross-reference documentation sections.
 *
 * <p>Categories are assigned by {@link CallCategoriser} based on the call's
 * {@link org.eclipse.acceleo.query.ast.CallType} and its service name:</p>
 * <ul>
 *   <li>Collection calls ({@code ->} syntax) are always
 *       {@link #AQL_COLLECTION}.</li>
 *   <li>Dot-syntax calls are resolved against the AQL built-in service set,
 *       the curated external method maps, and the known internal elements of
 *       the current module.</li>
 * </ul>
 */
public enum CallCategory {

    /** Call to another query defined in the same module. */
    INTERNAL_QUERY,

    /** Call to a template defined in the same module. */
    INTERNAL_TEMPLATE,

    /**
     * AQL collection operation using {@code ->} syntax.
     * Examples: {@code ->select()}, {@code ->collect()}, {@code ->size()}.
     */
    AQL_COLLECTION,

    /**
     * AQL built-in service using dot syntax.
     * Examples: {@code eClass()}, {@code eContainer()}, {@code oclIsKindOf()}.
     */
    AQL_SERVICE,

    /**
     * Java method from the Eclipse UML2 API.
     * Linked to {@code https://download.eclipse.org/modeling/mdt/uml2/javadoc/5.5.0/}.
     */
    EXTERNAL_UML2,

    /**
     * Java method from the Eclipse EMF API.
     * Linked to {@code https://download.eclipse.org/modeling/emf/emf/javadoc/2.9.0/}.
     */
    EXTERNAL_EMF,

    /**
     * Java service method defined in the current project.
     * Shown as plain text (no external URL available).
     */
    PROJECT_SERVICE,

    /**
     * Call that could not be resolved to any known category.
     * Omitted from the rendered cross-reference sections.
     */
    UNKNOWN
}
