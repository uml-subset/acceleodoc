package acceleodoc.core.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.acceleo.Block;
import org.eclipse.acceleo.query.ast.Call;
import org.eclipse.emf.ecore.EObject;

/**
 * Walks an Acceleo or AQL AST subtree and extracts all {@link Call} nodes,
 * categorising each via {@link CallCategoriser} and deduplicating by name
 * within each {@link CallCategory}.
 *
 * <h2>Walking strategy</h2>
 * <p>Uses EMF containment traversal via {@link EObject#eAllContents()} on the
 * root node. This works for both:</p>
 * <ul>
 *   <li>Query bodies — root is an AQL
 *       {@link org.eclipse.acceleo.query.ast.Expression}</li>
 *   <li>Template bodies — root is an Acceleo {@link Block} containing
 *       {@link org.eclipse.acceleo.Statement} children of various concrete
 *       types, each of which may contain AQL expressions with embedded
 *       {@link Call} nodes</li>
 * </ul>
 * <p>Since {@link Call} extends
 * {@link org.eclipse.acceleo.query.ast.Expression} which extends
 * {@link org.eclipse.emf.ecore.EObject}, every {@link Call} in the subtree
 * is reachable without needing to know the concrete statement types.</p>
 *
 * <h2>Deduplication</h2>
 * <p>Within each {@link CallCategory}, duplicate call names are collapsed to
 * a single {@link CallReference}. This avoids listing {@code select()} twenty
 * times if a template uses it repeatedly.</p>
 */
public class AstCallWalker {

	/**
	 * Returns true for AQL engine internal service names that should not
	 * appear in generated documentation.
	 */
	private boolean isInternalEngineName(String name) {
	    return switch (name) {
	        case "aqlFeatureAccess",   // feature access: aType.name
	             "aqlUnaryMin",        // unary minus: -x
	             "add",                // + operator
	             "sub",                // - operator
	             "mult",               // * operator
	             "divOp",              // / operator
	             "lessThan",           // < operator
	             "lessThanEqual",      // <= operator
	             "greaterThan",        // > operator
	             "greaterThanEqual",   // >= operator
	             "equals",             // = operator
	             "differs"             // <> operator
	             -> true;
	        default -> false;
	    };
	}
	private final CallCategoriser categoriser;

    /**
     * @param categoriser the categoriser used to resolve each extracted call
     */
    public AstCallWalker(CallCategoriser categoriser) {
        this.categoriser = categoriser;
    }

    /**
     * Walks the given AST root and returns a deduplicated, categorised list
     * of all {@link CallReference} instances found in the subtree.
     *
     * <p>The list is ordered by first occurrence in the AST traversal.
     * {@link CallCategory#UNKNOWN} calls are excluded from the result.</p>
     *
     * @param root any {@link EObject} — typically a {@link Block} for templates
     *             or an {@link org.eclipse.acceleo.query.ast.Expression} for
     *             queries
     * @return deduplicated list of call references, never null
     */
    public List<CallReference> walk(EObject root) {
        if (root == null) return List.of();

        // Use a map keyed by (category, name) to deduplicate
        Map<String, CallReference> seen = new LinkedHashMap<>();

        // Walk all contained EObjects recursively
        var iterator = root.eAllContents();
        while (iterator.hasNext()) {
            EObject obj = iterator.next();
            if (obj instanceof Call call) {
                String name = call.getServiceName();
                // Skip internal AQL engine implementation names
                if (name == null || name.isBlank() || isInternalEngineName(name)) {
                    continue; // or use iterator skip pattern
                }
                CallReference ref = categoriser.categorise(call);
                if (ref.category() != CallCategory.UNKNOWN
                        && !ref.name().isBlank()) {
                    // Key includes category so the same name in different
                    // categories (e.g. a query named "size" vs AQL "size")
                    // produces separate entries
                    String key = ref.category().name() + ":" + ref.name();
                    seen.putIfAbsent(key, ref);
                    System.err.println("[acceleodoc DEBUG] walker adding: name='" + ref.name()
                    + "' category=" + ref.category()
                    + " url='" + ref.url() + "'");
            seen.putIfAbsent(key, ref);                }
                
            }
        }

        return new ArrayList<>(seen.values());
    }

    /**
     * Filters the result of {@link #walk} to a specific {@link CallCategory}.
     *
     * @param root     the AST root to walk
     * @param category the category to filter by
     * @return list of call references matching the given category
     */
    public List<CallReference> walkCategory(EObject root, CallCategory category) {
        return walk(root).stream()
                .filter(ref -> ref.category() == category)
                .toList();
    }
}
