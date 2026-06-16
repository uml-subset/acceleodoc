<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Acceleo Documentation — Overview</title>
    <link rel="stylesheet" href="stylesheet.css"/>
</head>
<body>
<nav class="nav-bar">
    <span class="nav-title">Acceleo Documentation</span>
    <div class="search-box">
        <input type="text" id="search-input" placeholder="Search…" autocomplete="off"/>
        <div id="search-results" class="search-dropdown"></div>
    </div>
</nav>

<div class="page-wrapper">
    <aside class="sidebar">
        <h3>Modules</h3>
        <ul>
            <#list modules as mod>
            <li><a href="${mod.htmlFileName}">${mod.name}</a></li>
            </#list>
        </ul>
    </aside>

    <main class="content">
        <h1 class="page-title">Module Overview</h1>
        <p class="overview-summary">
            This documentation was generated from Acceleo 4.2 <code>.mtl</code> source files.
        </p>

        <table class="summary-table">
            <thead>
                <tr>
                    <th>Module</th>
                    <th>Templates</th>
                    <th>Queries</th>
                    <th>Description</th>
                </tr>
            </thead>
            <tbody>
                <#list modules as mod>
                <tr>
                    <td>
                        <a href="${mod.htmlFileName}">${mod.qualifiedName}</a>
                        <#if mod.isDeprecated()>
                            <span class="badge deprecated">Deprecated</span>
                        </#if>
                        <br/><span class="source-path"><code>${mod.sourceFile}</code></span>
                    </td>
                    <td class="count">${mod.templates?size}</td>
                    <td class="count">${mod.queries?size}</td>
                    <td>
                        <#if mod.hasDescription()>
                            ${mod.description?html}
                        <#else>
                            <span class="no-doc">—</span>
                        </#if>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
    </main>
</div>

<script src="lunr.min.js"></script>
<script src="search.js"></script>
</body>
</html>
