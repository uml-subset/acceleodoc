<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>${module.qualifiedName} — Acceleo Documentation</title>
    <link rel="stylesheet" href="stylesheet.css"/>
</head>
<body>
<nav class="nav-bar">
    <a class="nav-title" href="index.html">Acceleo Documentation</a>
    <div class="search-box">
        <input type="text" id="search-input" placeholder="Search…" autocomplete="off"/>
        <div id="search-results" class="search-dropdown"></div>
    </div>
</nav>

<div class="page-wrapper">
    <aside class="sidebar">
        <h3><a href="index.html">Modules</a></h3>
        <ul>
            <#list modules as mod>
            <li<#if mod.name == module.name> class="active"</#if>>
                <a href="${mod.htmlFileName}">${mod.name}</a>
            </li>
            </#list>
        </ul>
    </aside>

    <main class="content">

        <!-- ═══════════════════════════════════ Module header -->
        <h1 class="page-title">
            <span class="kind-badge kind-module">module</span>
            ${module.qualifiedName}
        </h1>

        <#if module.isDeprecated()>
        <div class="deprecated-block">
            <strong>Deprecated.</strong> ${module.deprecated?html}
        </div>
        </#if>

        <#if module.hasDescription()>
        <div class="description">${module.description?html}</div>
        </#if>

        <table class="meta-table">
            <#if module.metamodels?has_content>
            <tr><th>Metamodels</th><td>
                <#list module.metamodels as mm><code>${mm}</code><#sep>, </#list>
            </td></tr>
            </#if>
            <#if module.hasAuthor()>
            <tr><th>Author</th><td>${module.author?html}</td></tr>
            </#if>
            <#if module.hasVersion()>
            <tr><th>Version</th><td>${module.version?html}</td></tr>
            </#if>
            <#if module.hasSee()>
            <tr><th>See</th><td><code>${module.see?html}</code></td></tr>
            </#if>
            <tr><th>Source</th><td><code>${module.sourceFile}</code></td></tr>
        </table>

        <!-- ═══════════════════════════════════ Template summary -->
        <#if module.templates?has_content>
        <h2>Template Summary</h2>
        <table class="summary-table">
            <thead>
                <tr><th>Name</th><th>Description</th></tr>
            </thead>
            <tbody>
                <#list module.templates as t>
                <tr>
                    <td>
                        <a href="#${t.name}">
                            <code>${t.name}(<#list t.params as p>${p.name}<#sep>, </#list>)</code>
                        </a>
                        <#if t.isMain()><span class="badge main">@main</span></#if>
                        <#if t.isOverride()><span class="badge override">@override</span></#if>
                        <#if t.isDeprecated()><span class="badge deprecated">Deprecated</span></#if>
                    </td>
                    <td>
                        <#if t.hasDescription()>${t.description?html}
                        <#else><span class="no-doc">—</span></#if>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
        </#if>

        <!-- ═══════════════════════════════════ Query summary -->
        <#if module.queries?has_content>
        <h2>Query Summary</h2>
        <table class="summary-table">
            <thead>
                <tr><th>Name</th><th>Description</th></tr>
            </thead>
            <tbody>
                <#list module.queries as q>
                <tr>
                    <td>
                        <a href="#${q.name}">
                            <code>${q.name}(<#list q.params as p>${p.name}<#sep>, </#list>) : ${q.returnType?html}</code>
                        </a>
                        <#if q.isDeprecated()><span class="badge deprecated">Deprecated</span></#if>
                    </td>
                    <td>
                        <#if q.hasDescription()>${q.description?html}
                        <#else><span class="no-doc">—</span></#if>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
        </#if>

        <!-- ═══════════════════════════════════ Template detail -->
        <#if module.templates?has_content>
        <h2>Template Detail</h2>
        <#list module.templates as t>
        <div class="element-detail" id="${t.name}">
            <h3>
                <span class="kind-badge kind-template">template</span>
                <span class="visibility ${t.visibility}">${t.visibility}</span>
                <#if t.isMain()><span class="badge main">@main</span></#if>
                <#if t.isOverride()><span class="badge override">@override</span></#if>
                ${t.name}
            </h3>

            <div class="signature">
                <code>[template ${t.visibility} ${t.name}(<#list t.params as p>${p.name} : ${p.type}<#sep>, </#list>)]</code>
            </div>

            <#if t.isDeprecated()>
            <div class="deprecated-block"><strong>Deprecated.</strong> ${t.deprecated?html}</div>
            </#if>

            <#if t.hasDescription()>
            <div class="description">${t.description?html}</div>
            </#if>

            <#if t.params?has_content>
            <h4>Parameters</h4>
            <dl class="param-list">
                <#list t.params as p>
                <dt><code>${p.name}</code> <span class="param-type">: ${p.type?html}</span></dt>
                <dd><#if p.hasDescription()>${p.description?html}<#else><span class="no-doc">No description.</span></#if></dd>
                </#list>
            </dl>
            </#if>

            <#if t.hasSee()>
            <p class="see-also"><strong>See also:</strong> <code>${t.see?html}</code></p>
            </#if>

            <@crossRefs element=t/>
            <@sourceDropdown element=t/>
        </div>
        </#list>
        </#if>

        <!-- ═══════════════════════════════════ Query detail -->
        <#if module.queries?has_content>
        <h2>Query Detail</h2>
        <#list module.queries as q>
        <div class="element-detail" id="${q.name}">
            <h3>
                <span class="kind-badge kind-query">query</span>
                <span class="visibility ${q.visibility}">${q.visibility}</span>
                ${q.name}
            </h3>

            <div class="signature">
                <code>[query ${q.visibility} ${q.name}(<#list q.params as p>${p.name} : ${p.type}<#sep>, </#list>) : ${q.returnType?html} = ...]</code>
            </div>

            <#if q.isDeprecated()>
            <div class="deprecated-block"><strong>Deprecated.</strong> ${q.deprecated?html}</div>
            </#if>

            <#if q.hasDescription()>
            <div class="description">${q.description?html}</div>
            </#if>

            <#if q.params?has_content>
            <h4>Parameters</h4>
            <dl class="param-list">
                <#list q.params as p>
                <dt><code>${p.name}</code> <span class="param-type">: ${p.type?html}</span></dt>
                <dd><#if p.hasDescription()>${p.description?html}<#else><span class="no-doc">No description.</span></#if></dd>
                </#list>
            </dl>
            </#if>

            <h4>Returns</h4>
            <p>
                <code>${q.returnType?html}</code>
                <#if q.hasReturnDescription()> — ${q.returnDescription?html}</#if>
            </p>

            <#if q.hasSee()>
            <p class="see-also"><strong>See also:</strong> <code>${q.see?html}</code></p>
            </#if>

            <@crossRefs element=q/>
            <@sourceDropdown element=q/>
        </div>
        </#list>
        </#if>

    </main>
</div>

<!-- ═══════════════════════════════════════════════════════════
     Macro: cross-reference sections
     Renders categorised call references extracted from the AST.
     ═══════════════════════════════════════════════════════════ -->
<#macro crossRefs element>
    <#assign internalQueries    = element.getCallReferencesByCategory("INTERNAL_QUERY")/>
    <#assign internalTemplates  = element.getCallReferencesByCategory("INTERNAL_TEMPLATE")/>
    <#assign aqlCollections     = element.getCallReferencesByCategory("AQL_COLLECTION")/>
    <#assign aqlServices        = element.getCallReferencesByCategory("AQL_SERVICE")/>
    <#assign uml2Methods        = element.getCallReferencesByCategory("EXTERNAL_UML2")/>
    <#assign emfMethods         = element.getCallReferencesByCategory("EXTERNAL_EMF")/>
    <#assign projectServices    = element.getCallReferencesByCategory("PROJECT_SERVICE")/>

    <#if internalQueries?has_content || internalTemplates?has_content ||
         aqlCollections?has_content || aqlServices?has_content ||
         uml2Methods?has_content || emfMethods?has_content ||
         projectServices?has_content>
    <div class="cross-refs">
        <h4>References</h4>

        <#if internalQueries?has_content>
        <div class="ref-section">
            <span class="ref-label">Queries</span>
            <#list internalQueries as ref>
                <a class="ref-badge ref-internal-query"
                   href="${ref.url}">${ref.name}</a>
            </#list>
        </div>
        </#if>

        <#if internalTemplates?has_content>
        <div class="ref-section">
            <span class="ref-label">Templates</span>
            <#list internalTemplates as ref>
                <a class="ref-badge ref-internal-template"
                   href="${ref.url}">${ref.name}</a>
            </#list>
        </div>
        </#if>

        <#if aqlCollections?has_content>
        <div class="ref-section">
            <span class="ref-label">AQL collection operations</span>
            <#list aqlCollections as ref>
                <#if ref.hasUrl()>
                    <a class="ref-badge ref-aql-collection"
                       href="${ref.url}" target="_blank">${ref.displayName}</a>
                <#else>
                    <span class="ref-badge ref-aql-collection">${ref.displayName}</span>
                </#if>
            </#list>
        </div>
        </#if>

        <#if aqlServices?has_content>
        <div class="ref-section">
            <span class="ref-label">AQL services</span>
            <#list aqlServices as ref>
                <#if ref.hasUrl()>
                    <a class="ref-badge ref-aql-service"
                       href="${ref.url}" target="_blank">${ref.displayName}</a>
                <#else>
                    <span class="ref-badge ref-aql-service">${ref.displayName}</span>
                </#if>
            </#list>
        </div>
        </#if>

        <#if uml2Methods?has_content>
        <div class="ref-section">
            <span class="ref-label">Eclipse UML2</span>
            <#list uml2Methods as ref>
                <#if ref.hasUrl()>
                    <a class="ref-badge ref-uml2"
                       href="${ref.url}" target="_blank">${ref.displayName}</a>
                <#else>
                    <span class="ref-badge ref-uml2">${ref.displayName}</span>
                </#if>
            </#list>
        </div>
        </#if>

        <#if emfMethods?has_content>
        <div class="ref-section">
            <span class="ref-label">Eclipse EMF</span>
            <#list emfMethods as ref>
                <#if ref.hasUrl()>
                    <a class="ref-badge ref-emf"
                       href="${ref.url}" target="_blank">${ref.displayName}</a>
                <#else>
                    <span class="ref-badge ref-emf">${ref.displayName}</span>
                </#if>
            </#list>
        </div>
        </#if>

        <#if projectServices?has_content>
				<div class="ref-section">
				    <span class="ref-label">Project services</span>
				    <#list projectServices as ref>
				        <#if ref.hasUrl()>
				            <a class="ref-badge ref-project-service"
				               href="${ref.url}" target="_blank">${ref.displayName}</a>
				        <#else>
				            <span class="ref-badge ref-project-service">${ref.displayName}</span>
				        </#if>
				    </#list>
				</div>
				</#if>
        
    </div>
    </#if>
</#macro>

<!-- ═══════════════════════════════════════════════════════════
     Macro: source code dropdown
     Uses native HTML <details>/<summary> — no JavaScript needed.
     ═══════════════════════════════════════════════════════════ -->
<#macro sourceDropdown element>
    <#if element.hasSourceText()>
    <details class="source-dropdown">
        <summary>Show source</summary>
        <pre class="source-code"><code>${element.sourceText?html}</code></pre>
    </details>
    </#if>
</#macro>

<script src="lunr.min.js"></script>
<script src="search.js"></script>
</body>
</html>
