package acceleodoc.core.extract;

import java.util.Map;
import java.util.Set;

import org.eclipse.acceleo.query.ast.Call;
import org.eclipse.acceleo.query.ast.CallType;

/**
 * Categorises AQL {@link Call} AST nodes extracted from Acceleo template
 * and query bodies into {@link CallCategory} values and resolves documentation
 * URLs for the generated cross-reference sections.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>Internal AQL engine names (e.g. {@code aqlFeatureAccess}) →
 *       {@link CallCategory#UNKNOWN} — excluded from output.</li>
 *   <li>{@link CallType#COLLECTIONCALL} ({@code ->} syntax) →
 *       {@link CallCategory#AQL_COLLECTION}, URL from AQL link map.</li>
 *   <li>Name found in query module map →
 *       {@link CallCategory#INTERNAL_QUERY}, URL is
 *       {@code moduleName.html#queryName}.</li>
 *   <li>Name found in template module map →
 *       {@link CallCategory#INTERNAL_TEMPLATE}, URL is
 *       {@code moduleName.html#templateName}.</li>
 *   <li>Name in AQL built-in service set →
 *       {@link CallCategory#AQL_SERVICE}, URL from AQL link map.</li>
 *   <li>Name in UML2 link map →
 *       {@link CallCategory#EXTERNAL_UML2}, URL from UML2 link map.</li>
 *   <li>Name in EMF link map →
 *       {@link CallCategory#EXTERNAL_EMF}, URL from EMF link map.</li>
 *   <li>Name in project service link map →
 *       {@link CallCategory#PROJECT_SERVICE}, URL from project service map.</li>
 *   <li>Otherwise →
 *       {@link CallCategory#PROJECT_SERVICE} with no URL (plain badge).</li>
 * </ol>
 *
 * <h2>Cross-module links</h2>
 * <p>{@code queryModuleMap} and {@code templateModuleMap} map element names
 * to the HTML filename of the module where they are defined
 * (e.g. {@code "commonQueries.html"}). This is populated in pass 1 of
 * {@link DocExtractor#extractAll} so that cross-module references resolve
 * to the correct page regardless of file processing order.</p>
 */
public class CallCategoriser {

    /**
     * AQL built-in services invoked with dot syntax ({@code CALLSERVICE}).
     * These map to entries in the AQL link map.
     */
    private static final Set<String> AQL_BUILTIN_SERVICES = Set.of(
            // EObject services
            "eClass", "eContainer", "eContainerOrSelf", "eContainingFeature",
            "eContainmentFeature", "eContents", "eAllContents", "eInverse",
            "eGet", "eCrossReferences", "eResource",
            // Object services
            "oclIsKindOf", "oclIsTypeOf", "oclAsType", "toString", "trace",
            // String services
            "concat", "contains", "endsWith", "equalsIgnoreCase", "first",
            "index", "isAlpha", "isAlphaNum", "last", "lastIndex", "matches",
            "prefix", "replace", "replaceAll", "size", "startsWith", "strcmp",
            "strstr", "substitute", "substituteAll", "substring", "toInteger",
            "toLower", "toLowerFirst", "toReal", "toUpper", "toUpperFirst",
            "tokenize", "trim",
            // Numeric services
            "abs", "div", "divOp", "floor", "max", "min", "round",
            // Boolean services
            "and", "implies", "not", "or", "xor",
            // Resource/URI services
            "fileExtension", "getContents", "getURI", "isPlatformPlugin",
            "isPlatformResource", "lastSegment",
            // Other
            "allInstances", "filter"
    );

    /**
     * AQL engine internal implementation names that must never appear
     * in generated documentation.
     *
     * <p>These are not real service calls — they are synthetic AST nodes
     * produced by the AQL parser to represent operators and feature accesses
     * in a uniform call-node form.</p>
     */
    private static final Set<String> INTERNAL_ENGINE_NAMES = Set.of(
            "aqlFeatureAccess",  // property access: aType.name
            "aqlUnaryMin",       // unary minus: -x
            "add",               // + operator
            "sub",               // - operator
            "mult",              // * operator
            "divOp",             // / operator
            "lessThan",          // < operator
            "lessThanEqual",     // <= operator
            "greaterThan",       // > operator
            "greaterThanEqual",  // >= operator
            "equals",            // = operator
            "differs"            // <> operator
    );

    private final Map<String, String> aqlLinks;
    private final Map<String, String> uml2Links;
    private final Map<String, String> emfLinks;
    private final Map<String, String> projectServiceLinks;

    /**
     * Maps query name → HTML filename of its declaring module
     * (e.g. {@code "e_path" → "commonQueries.html"}).
     * Populated by pass 1 of {@link DocExtractor#extractAll}.
     */
    private final Map<String, String> queryModuleMap;

    /**
     * Maps template name → HTML filename of its declaring module.
     * Populated by pass 1 of {@link DocExtractor#extractAll}.
     */
    private final Map<String, String> templateModuleMap;

    /**
     * @param aqlLinks            AQL operation/service name → URL in
     *                            {@code aql-reference.html}
     * @param uml2Links           UML2 method name → full Javadoc URL
     * @param emfLinks            EMF method name → full Javadoc URL
     * @param projectServiceLinks project Java service method name → URL,
     *                            or empty map if not configured
     * @param queryModuleMap      query name → declaring module HTML filename
     * @param templateModuleMap   template name → declaring module HTML filename
     */
    public CallCategoriser(Map<String, String> aqlLinks,
                           Map<String, String> uml2Links,
                           Map<String, String> emfLinks,
                           Map<String, String> projectServiceLinks,
                           Map<String, String> queryModuleMap,
                           Map<String, String> templateModuleMap) {
        this.aqlLinks            = aqlLinks;
        this.uml2Links           = uml2Links;
        this.emfLinks            = emfLinks;
        this.projectServiceLinks = projectServiceLinks;
        this.queryModuleMap      = queryModuleMap;
        this.templateModuleMap   = templateModuleMap;
    }

    /**
     * Categorises a single {@link Call} node and returns a
     * {@link CallReference} with resolved category and URL.
     *
     * @param call the AQL AST call node to categorise
     * @return a {@link CallReference} (never null)
     */
    public CallReference categorise(Call call) {
        String name = call.getServiceName();
        if ("u_mainPackage".equals(name)) {
            System.err.println("[acceleodoc DEBUG] u_mainPackage: queryModuleMap.containsKey="
                    + queryModuleMap.containsKey(name)
                    + " queryModuleMap.size=" + queryModuleMap.size()
                    + " projectServiceLinks.containsKey="
                    + projectServiceLinks.containsKey(name)
                    + " projectServiceLinks.size=" + projectServiceLinks.size());
        }
        if (name == null || name.isBlank()) {
            return new CallReference("", CallCategory.UNKNOWN, null);
        }
        System.err.println("[acceleodoc DEBUG] categorise: name='" + name
                + "' type=" + call.getType()
                + " inProjectLinks=" + projectServiceLinks.containsKey(name));

        // Exclude internal AQL engine implementation names
        if (INTERNAL_ENGINE_NAMES.contains(name)) {
            return new CallReference(name, CallCategory.UNKNOWN, null);
        }

        // 1. Collection call (-> syntax) — always AQL built-in
        if (call.getType() == CallType.COLLECTIONCALL) {
            String url = aqlLinks.get(name);
            return new CallReference(name, CallCategory.AQL_COLLECTION, url);
        }

        // 2. Internal query reference — URL includes the declaring module page
        if (queryModuleMap.containsKey(name)) {
            String htmlFile = queryModuleMap.get(name);
            return new CallReference(name, CallCategory.INTERNAL_QUERY,
                    htmlFile + "#" + name);
        }

        // 3. Internal template reference — URL includes the declaring module page
        if (templateModuleMap.containsKey(name)) {
            String htmlFile = templateModuleMap.get(name);
            return new CallReference(name, CallCategory.INTERNAL_TEMPLATE,
                    htmlFile + "#" + name);
        }

        // 4. AQL built-in dot-syntax service
        if (AQL_BUILTIN_SERVICES.contains(name)) {
            String url = aqlLinks.get(name);
            return new CallReference(name, CallCategory.AQL_SERVICE, url);
        }

        // 5. External UML2 method
        if (uml2Links.containsKey(name)) {
            return new CallReference(name, CallCategory.EXTERNAL_UML2,
                    uml2Links.get(name));
        }

        // 6. External EMF method
        if (emfLinks.containsKey(name)) {
            return new CallReference(name, CallCategory.EXTERNAL_EMF,
                    emfLinks.get(name));
        }

        // 7. Project-specific Java service
        // URL is present if java-services.json is configured, null otherwise
        String projectUrl = projectServiceLinks.get(name);
        System.err.println("[acceleodoc DEBUG] PROJECT_SERVICE: name='" + name
                + "' url='" + projectUrl + "'");
        return new CallReference(name, CallCategory.PROJECT_SERVICE, projectUrl);
    }
}
