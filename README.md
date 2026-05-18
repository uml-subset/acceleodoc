# acceleodoc

Javadoc-style HTML documentation generator for [Acceleo 4.2](https://eclipse.dev/acceleo/) projects.

Parses `[** ... */]` documentation comments from `.mtl` files using the Acceleo 4.x AQL AST API
and generates multi-page HTML with a Lunr.js-powered search index.

---

## Project Structure

```
acceleodoc/
├── bundles/
│   ├── acceleodoc.core/        Parser + extractor + Freemarker HTML renderer (reusable)
│   ├── acceleodoc.cli/         CLI entry point (IApplication + static main)
│   └── acceleodoc.eclipse/     Eclipse context menu action + optional project builder
├── features/
│   └── acceleodoc.feature/     Eclipse feature grouping all three bundles
├── releng/
│   ├── acceleodoc.target/      Target platform (Eclipse 2025-12 + Acceleo 4.2)
│   └── acceleodoc.product/     Standalone executable product definition
└── repository/
    └── acceleodoc.repository/  P2 update site for Eclipse installation
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java (OpenLogic-OpenJDK) | 21 |
| Maven | 3.9+ |
| Eclipse | 2025-12 |
| Acceleo | 4.2 |
| Tycho | 5.0.0 |

---

## Before Building — Required: Add Lunr.js

The search feature requires Lunr.js 2.3.9. Download it and place it at:

```
bundles/acceleodoc.core/resources/templates/lunr.min.js
```

Download URL:
```
https://unpkg.com/lunr@2.3.9/lunr.min.js
```

---

## Before Building — Required: Add Freemarker JAR

Freemarker 2.3.33 must be placed as a wrapped JAR at:

```
bundles/acceleodoc.core/lib/freemarker.jar
```

Download from Maven Central:
```
https://repo1.maven.org/maven2/org/freemarker/freemarker/2.3.33/freemarker-2.3.33.jar
```

Then rename to `freemarker.jar` and place in `bundles/acceleodoc.core/lib/`.

---

## Building

```bash
# Default (Linux)
mvn clean verify

# Windows
mvn clean verify -Dbuild.platform=windows

# All platforms
mvn clean verify -Dbuild.platform=all
```

---

## CLI Usage

### Via Eclipse launcher

```bash
eclipse -application acceleodoc.cli.app \
        -input  /workspace/ucmism2t/bundles/ucmism2t.core/src \
        -output /workspace/ucmism2t/doc
```

### Via standalone product (after `mvn clean verify`)

```bash
acceleodoc -input /path/to/mtl/src -output /path/to/doc
```

### Arguments

| Argument | Required | Description |
|----------|----------|-------------|
| `-input <path>`  | Yes | Root directory containing `.mtl` files (scanned recursively) |
| `-output <path>` | Yes | Output directory for generated HTML |

---

## Eclipse Integration

### Context menu action

Right-click any **project** or **`.mtl` file** in the Project Explorer and select:

> **Generate Acceleo Documentation**

HTML is written to the `doc/` folder inside the project and the workspace is refreshed automatically.

### Automatic builder (optional)

1. Right-click the project → **Properties** → **Acceleo Documentation**
2. Check **Enable automatic documentation generation on build**
3. Optionally change the output folder (default: `doc`)
4. Click **OK**

The builder runs incrementally — it only regenerates when a `.mtl` file changes.

---

## Supported Documentation Tags

| Tag | Applies to | Description |
|-----|-----------|-------------|
| `@param <name> <desc>` | Template, Query | Parameter documentation |
| `@return <desc>` | Query | Return value description |
| `@see <reference>` | Module, Template, Query | Cross-reference |
| `@deprecated <desc>` | Module, Template, Query | Deprecation notice |
| `@author <name>` | Module, Template, Query | Author |
| `@version <value>` | Module, Template, Query | Version |

### Example

```
[**
 * Generates an xs:simpleType declaration for a UML enumeration.
 * Each enumeration literal becomes an xs:enumeration facet.
 *
 * @param anEnumeration A UML Enumeration element.
 * @deprecated Use enumerationXsdV2 instead.
 * @author UCMIS
 * @version 1.0
 */]
[template public enumerationXsd(anEnumeration : Enumeration)]
  ...
[/template]
```

---

## Using acceleodoc with other Acceleo projects

`acceleodoc.core` is a reusable OSGi bundle. Any other Acceleo 4.2 Tycho project can use it by:

1. Adding `acceleodoc.core` to its target platform (via the P2 repository) or
   importing the project into the same workspace.
2. Calling `DocGenerator` directly:

```java
import acceleodoc.core.DocGenerator;

new DocGenerator().generate(
    Path.of("/path/to/src"),
    Path.of("/path/to/doc")
);
```
