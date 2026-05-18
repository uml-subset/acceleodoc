# Using acceleodoc with other Acceleo projects

This guide describes how to integrate `acceleodoc.core` into any Acceleo 4.2 Tycho project
so that it can generate HTML documentation from that project's `.mtl` files.

Two approaches are available. They differ in coupling and the build context they suit best.

---

## Approach comparison

| | Approach A — Shared workspace | Approach B — P2 repository |
|---|---|---|
| **How it works** | Both projects are imported into the same Eclipse workspace; Tycho resolves `acceleodoc.core` from workspace projects | `acceleodoc` is built and published to a local P2 repository; the target project references it as an external dependency |
| **Best for** | Active development on acceleodoc itself alongside the target project | Stable, versioned use of acceleodoc; CI builds; no workspace dependency |
| **Requires** | Both repositories cloned locally; same Eclipse workspace | `mvn clean verify` run once on acceleodoc to produce the P2 site |
| **Build isolation** | Low — workspace changes in acceleodoc are immediately visible | High — target project is independent of acceleodoc source |

---

## Approach A — Shared Eclipse workspace

In this approach both `acceleodoc` and your target project (e.g. `ucmism2t`) are imported into
the same Eclipse workspace. Tycho's workspace resolution mechanism makes `acceleodoc.core`
visible to your project's bundle without any P2 repository.

### Step 1 — Clone both repositories side by side

```
workspace/
  acceleodoc/       ← git clone https://…/acceleodoc.git
  ucmism2t/         ← already present
```

The exact directory names do not matter; what matters is that both are imported into the
same Eclipse workspace in the next step.

### Step 2 — Complete the acceleodoc manual prerequisites

Before importing, place the two required files that are not committed to the repository:

1. Download Freemarker 2.3.33:
   `https://repo1.maven.org/maven2/org/freemarker/freemarker/2.3.33/freemarker-2.3.33.jar`
   Rename it to `freemarker.jar` and place it at:
   `acceleodoc/bundles/acceleodoc.core/lib/freemarker.jar`

2. Download Lunr.js 2.3.9:
   `https://unpkg.com/lunr@2.3.9/lunr.min.js`
   Place it at:
   `acceleodoc/bundles/acceleodoc.core/resources/templates/lunr.min.js`

### Step 3 — Import acceleodoc projects into Eclipse

1. In Eclipse: **File → Import → Maven → Existing Maven Projects**
2. Set **Root Directory** to the `acceleodoc/` folder
3. Eclipse will detect all modules. Select all of them:
   - `acceleodoc-parent`
   - `acceleodoc.target`
   - `acceleodoc.core`
   - `acceleodoc.cli`
   - `acceleodoc.eclipse`
   - `acceleodoc.feature`
   - `acceleodoc.product`
   - `acceleodoc.repository`
4. Click **Finish**
5. If prompted by m2e about lifecycle mappings, accept the suggested fixes

### Step 4 — Activate the acceleodoc target platform

The acceleodoc target platform (`acceleodoc.target`) and the ucmism2t target platform
(`ucmism2t.target`) resolve the same P2 locations (Eclipse 2025-12 and Acceleo 4.2),
so either one can be active. Keep the existing `ucmism2t.target` active — there is no
need to switch.

If you ever need to verify: open the active `.target` file and confirm it contains both
the Eclipse 2025-12 and Acceleo 4.2 R202602100910 repository locations.

### Step 5 — Declare the dependency in your bundle's MANIFEST.MF

Open `ucmism2t/bundles/ucmism2t.core/META-INF/MANIFEST.MF` and add `acceleodoc.core`
to the `Require-Bundle` header:

```
Require-Bundle: org.eclipse.acceleo.aql;bundle-version="[4.2.0,5.0.0)",
 org.eclipse.acceleo.query;bundle-version="[8.0.0,9.0.0)",
 org.eclipse.emf.ecore;bundle-version="[2.35.0,3.0.0)",
 org.eclipse.emf.common;bundle-version="[2.29.0,3.0.0)",
 org.eclipse.uml2.uml;bundle-version="[5.5.0,6.0.0)",
 org.eclipse.uml2.types;bundle-version="[2.5.0,3.0.0)",
 org.eclipse.uml2.common;bundle-version="[2.5.0,3.0.0)",
 acceleodoc.core;bundle-version="[1.0.0,2.0.0)"
```

### Step 6 — Call DocGenerator from your code

`acceleodoc.core` exports the `acceleodoc.core` package. You can now call `DocGenerator`
directly from any Java class in `ucmism2t.core`:

```java
import acceleodoc.core.DocGenerator;
import java.nio.file.Path;

public class GenerateDocumentation {

    public void run(Path sourceRoot, Path outputDir) throws Exception {
        new DocGenerator().generate(sourceRoot, outputDir);
    }
}
```

A typical invocation for ucmism2t would be:

```java
new DocGenerator().generate(
    Path.of("bundles/ucmism2t.core/src"),
    Path.of("doc")
);
```

### Step 7 — Verify the workspace build

In Eclipse: **Project → Clean → Clean all projects**, then wait for the workspace build
to complete. No red markers should appear on `ucmism2t.core` or `acceleodoc.core`.

To verify via Maven:

```bash
cd ucmism2t/
mvn clean verify
```

Tycho resolves `acceleodoc.core` from the workspace automatically during a Maven build
when both projects share the same parent workspace directory, provided the
`resolveWorkspaceProjects=true` setting is present in `.settings/org.eclipse.m2e.core.prefs`
(which it is in both projects as generated).

---

## Approach B — Local P2 repository

In this approach `acceleodoc` is built once to produce a self-contained P2 update site.
The target project's `.target` file is then extended to reference that site. The two
repositories remain fully independent; no shared workspace is required.

### Step 1 — Complete the acceleodoc manual prerequisites

Same as Approach A, Step 2 above — place `freemarker.jar` and `lunr.min.js` before
building.

### Step 2 — Build acceleodoc and publish the P2 site

```bash
cd acceleodoc/
mvn clean verify
```

After a successful build, the P2 update site is produced at:

```
acceleodoc/repository/acceleodoc.repository/target/repository/
```

This directory is a self-contained P2 site containing `artifacts.jar`, `content.jar`,
and the `features/` and `plugins/` folders.

### Step 3 — Determine the P2 site URL

The target platform definition requires a `file://` URL. Derive it from the absolute path
of the repository output directory.

On Linux / macOS:
```
file:///home/youruser/acceleodoc/repository/acceleodoc.repository/target/repository
```

On Windows:
```
file:///C:/dev/acceleodoc/repository/acceleodoc.repository/target/repository
```

> **Tip:** To make this path portable across machines and CI environments, publish the
> P2 site to a shared network location or a web server and use an `http://` or `https://`
> URL instead.

### Step 4 — Add the acceleodoc P2 site to ucmism2t's target platform

Open `ucmism2t/releng/ucmism2t.target/ucmism2t.target` and add a new `<location>` block
after the existing Acceleo 4.2 location:

```xml
<!--
  acceleodoc — local P2 repository
  Built from: acceleodoc/repository/acceleodoc.repository/target/repository/
  Update the path below to match your local build output.
-->
<location includeAllPlatforms="false"
          includeConfigurePhase="true"
          includeMode="planner"
          includeSource="true"
          type="InstallableUnit">
    <repository location="file:///home/youruser/acceleodoc/repository/acceleodoc.repository/target/repository"/>
    <unit id="acceleodoc.feature.feature.group" version="0.0.0"/>
</location>
```

The `unit id` uses the feature group IU, which is the standard way to pull in a feature
and all its constituent bundles (including `acceleodoc.core`) in a single declaration.

### Step 5 — Reload the target platform in Eclipse

1. Open `ucmism2t.target` in the Target Editor
2. Click **Reload Target Platform** (top-right of the editor)
3. Wait for resolution to complete — the status bar shows progress
4. Click **Set as Active Target Platform**

After reloading, `acceleodoc.core` is available as an OSGi bundle in the target platform.

### Step 6 — Declare the dependency in your bundle's MANIFEST.MF

Same as Approach A, Step 5 — add `acceleodoc.core` to the `Require-Bundle` header of
`ucmism2t/bundles/ucmism2t.core/META-INF/MANIFEST.MF`:

```
 acceleodoc.core;bundle-version="[1.0.0,2.0.0)"
```

### Step 7 — Call DocGenerator from your code

Same as Approach A, Step 6.

### Step 8 — Verify the Maven build

```bash
cd ucmism2t/
mvn clean verify
```

Tycho resolves `acceleodoc.core` from the P2 location declared in `ucmism2t.target`.
No workspace dependency on `acceleodoc` is needed.

---

## Choosing the output location

When calling `DocGenerator` programmatically, the `outputDir` argument can be any path.
The conventional location — consistent with how `acceleodoc.eclipse` writes output — is
a `doc/` folder at the root of the project:

```java
Path projectRoot = Path.of(System.getProperty("user.dir"));
Path sourceRoot  = projectRoot.resolve("bundles/ucmism2t.core/src");
Path outputDir   = projectRoot.resolve("doc");

new DocGenerator().generate(sourceRoot, outputDir);
```

After generation, `doc/index.html` is the entry point for the documentation site.

---

## Summary

| Step | Approach A (shared workspace) | Approach B (P2 repository) |
|---|---|---|
| 1 | Clone acceleodoc next to ucmism2t | Place `freemarker.jar` and `lunr.min.js` |
| 2 | Place `freemarker.jar` and `lunr.min.js` | Run `mvn clean verify` on acceleodoc |
| 3 | Import acceleodoc into Eclipse workspace | Note the P2 site path |
| 4 | Keep existing ucmism2t target platform active | Add acceleodoc P2 location to `ucmism2t.target` |
| 5 | Add `acceleodoc.core` to `Require-Bundle` | Reload target platform in Eclipse |
| 6 | Call `DocGenerator` from your code | Add `acceleodoc.core` to `Require-Bundle` |
| 7 | Verify with `mvn clean verify` | Call `DocGenerator` from your code |
| 8 | | Verify with `mvn clean verify` |
