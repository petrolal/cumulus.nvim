---
title: 'Epic 3 Story 3.3: REST Endpoint Extractor, Import Optimizer & Java Header Generator'
type: 'feature'
created: '2026-08-14'
status: 'done'
baseline_commit: '76456b1'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-3-context.md', '_bmad-output/implementation-artifacts/spec-3-1-codelens-extractor.md', '_bmad-output/implementation-artifacts/spec-3-2-spring-boot-beans.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** JVM developers need quick discovery of REST endpoints, clean import organization, and boilerplate code generation for new files. Manual inspection of endpoint definitions and import management are tedious and error-prone.

**Approach:** Implement three subcommands: `extract-endpoints --dir <path>` to find REST endpoints with HTTP methods and paths (Spring and JAX-RS); `optimize-imports` (via stdin) to deduplicate and sort imports; `generate-java-header --file <path>` to infer package from directory structure and generate class boilerplate.

## Boundaries & Constraints

**Always:**
- Use `os-lib` exclusively for file operations and directory traversal.
- Subcommand signatures: `cumulus-engine extract-endpoints --dir <path>`, `cumulus-engine optimize-imports`, `cumulus-engine generate-java-header --file <path>`.
- Response envelope: `CumulusResponse[T]` with zero runtime reflection (uPickle only).
- Endpoint detection: Spring (@GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping, @RequestMapping) and JAX-RS (@Path, @GET, @POST, @PUT, @DELETE, @PATCH).
- Resolve class-level @RequestMapping base paths when computing full endpoint paths.
- Import optimization: deduplicate via Set, sort lexically, preserve non-import lines.
- Java header generation: infer package from `/src/main/java/`, `/src/test/java/`, `/src/` paths; output `package ...;` and `public class ClassName { }`.
- All JSON output conforms to `SPEC-031` schema.

**Ask First:**
- Should optimize-imports also reformat wildcard imports (e.g., `import com.example.*` → explicit imports)?
- Support for Spring WebFlux (@GetMapping on reactive types)?
- Should generate-java-header create public/private/package-private class visibility?

**Never:**
- Do not invoke external tools (javac, IDE, etc.).
- Do not modify source files during extraction.
- Do not use runtime reflection for annotation detection.
- Do not assume specific import ordering conventions beyond lexical sort.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Spring endpoints | `extract-endpoints --dir /spring-app` with @GetMapping, @PostMapping | Return list with method, path, class, handler name | N/A |
| JAX-RS endpoints | `extract-endpoints --dir /jaxrs-app` with @Path, @GET, @POST | Return list with HTTP method, path, class, handler | N/A |
| Class-level @RequestMapping | @RequestMapping("/api") on class, @GetMapping("/users") on method | Resolved path: `/api/users` | N/A |
| Duplicate imports | `optimize-imports` via stdin with `import java.util.List; import java.util.List;` | Return deduplicated, sorted import block | N/A |
| Wildcard imports | `import java.util.*;` | Keep as-is or suggest expansion (per Ask First) | N/A |
| Unsorted imports | Imports in random order | Return lexically sorted import block | N/A |
| Generate Java header | `generate-java-header --file /src/main/java/com/example/MyClass.java` | Return `package com.example;` and `public class MyClass { }` | N/A |
| Infer package from path | File at `/src/main/java/com/example/util/Helper.java` | Package: `com.example.util`; class: `Helper` | N/A |
| No endpoints found | `extract-endpoints --dir /util-lib` | Return empty list with success=true | N/A |
| Invalid path | `generate-java-header --file /nonexistent/File.java` | Return error `FILE_NOT_FOUND` | Graceful error |
| Kotlin file endpoint | `extract-endpoints --dir .` with Kotlin class with @GetMapping | Detect and return endpoint | N/A |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:420-460` -- CLI router: add `extract-endpoints`, `optimize-imports`, `generate-java-header` subcommands
- `engine/src/main/scala/cumulus/code/EndpointScanner.scala` -- **NEW**: Scan for Spring/JAX-RS annotations; resolve class-level base paths; build endpoint list
- `engine/src/main/scala/cumulus/code/ImportOptimizer.scala` -- **NEW**: Parse import statements; deduplicate via Set; sort lexically; preserve non-import lines
- `engine/src/main/scala/cumulus/code/JavaHeaderGenerator.scala` -- **NEW**: Parse file path; infer package from directory structure; generate boilerplate
- `engine/src/main/scala/cumulus/code/CodeModels.scala:60-80` -- **EDIT**: Add Endpoint, ImportOptimizeResponse, JavaHeader case classes
- `engine/src/test/scala/cumulus/code/EndpointTest.scala` -- **NEW**: Comprehensive tests for endpoint detection, path resolution, edge cases

## Tasks & Acceptance

**Execution:**
- [ ] `engine/src/main/scala/cumulus/code/CodeModels.scala` -- EDIT: Add Endpoint, ImportOptimizeResponse, JavaHeader case classes
- [ ] `engine/src/main/scala/cumulus/code/EndpointScanner.scala` -- CREATE: Spring/JAX-RS annotation detection; path resolution; endpoint list building
- [ ] `engine/src/main/scala/cumulus/code/ImportOptimizer.scala` -- CREATE: Import parsing; deduplication; lexical sorting
- [ ] `engine/src/main/scala/cumulus/code/JavaHeaderGenerator.scala` -- CREATE: Package inference from directory; boilerplate generation
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add three new subcommands to CLI router
- [ ] `engine/src/test/scala/cumulus/code/EndpointTest.scala` -- CREATE: Tests for all I/O scenarios

**Acceptance Criteria:**
- Given a Spring Boot project with @GetMapping and @PostMapping, when `extract-endpoints --dir .` is executed, then endpoints are returned with HTTP method, path, class name, and handler method name.
- Given class-level @RequestMapping("/api") and method-level @GetMapping("/users"), then resolved path is `/api/users`.
- Given stdin with duplicate imports, when `optimize-imports` is executed, then output is deduplicated and lexically sorted.
- Given a file path `/src/main/java/com/example/MyClass.java`, when `generate-java-header --file <path>` is executed, then output is `package com.example; public class MyClass { }`.
- All tests pass: `sbt test`, and binary builds with `sbt graalvm-native-image:packageBin`.

### Review Findings

- [x] [Review][Decision] ImportOptimizer contract — Return list of sorted unique imports `ImportsResponse(imports: Seq[String])` (as defined in CodeModels / SPEC-031) or replace entire buffer with reformatted file content.
- [x] [Review][Patch] Adopt os-lib exclusively across CodeLensExtractor, SpringBootDetector, BeanGraphAnalyzer, and JavaHeaderGenerator [`engine/src/main/scala/cumulus/code/CodeLensExtractor.scala:33`]
- [x] [Review][Patch] Fix premature class termination on method closing brace in BeanGraphAnalyzer [`engine/src/main/scala/cumulus/code/BeanGraphAnalyzer.scala:146`]
- [x] [Review][Patch] Set SpringBean line number to class definition line rather than stereotype annotation line [`engine/src/main/scala/cumulus/code/BeanGraphAnalyzer.scala:127`]
- [x] [Review][Patch] Fix SpringBootDetector to scan src/main/kotlin when src/main/java is present or empty [`engine/src/main/scala/cumulus/code/SpringBootDetector.scala:72`]
- [x] [Review][Patch] Support src/main/resources/ for active profiles and application-*.yml files in SpringBootDetector [`engine/src/main/scala/cumulus/code/SpringBootDetector.scala:241`]
- [x] [Review][Patch] Allow generating Java/Kotlin headers for new/non-existent file paths and support relative paths without leading slash [`engine/src/main/scala/cumulus/code/JavaHeaderGenerator.scala:16`]
- [x] [Review][Patch] Fix path concatenation double-slash in EndpointScanner and support method-level @RequestMapping and JAX-RS / Kotlin syntax [`engine/src/main/scala/cumulus/code/EndpointScanner.scala:161`]
- [x] [Review][Patch] Fix multi-line Qualifier detection in BeanGraphAnalyzer [`engine/src/main/scala/cumulus/code/BeanGraphAnalyzer.scala:133`]
- [x] [Review][Patch] Add unit and CLI tests for JAX-RS endpoints, Kotlin endpoints, Qualifier dependencies, and CLI dispatch [`engine/src/test/scala/cumulus/code/EndpointTest.scala:1`]
- [x] [Review][Defer] Top-level Kotlin fun main() CodeLens detection — deferred, pre-existing
- [x] [Review][Defer] Kotlin property / constructor-based Spring bean injection — deferred, pre-existing

## Design Notes

**Endpoint Detection:** Compile regex patterns for Spring annotations (@GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping) and JAX-RS (@GET, @POST, @PUT, @DELETE, @PATCH). Match with method declarations. Extract HTTP method from annotation name or @RequestMethod parameter.

**Path Resolution:** 
1. Scan class declaration for class-level @RequestMapping("path") or @Path("path")
2. Scan method declaration for method-level @GetMapping, @PostMapping, etc. with path parameter
3. Concatenate class path + method path if both present

**Import Optimization:**
1. Read stdin line by line
2. Classify each line: import statement, package declaration, class declaration, other code
3. Collect all import statements; deduplicate via Set
4. Sort deduplicated imports lexically
5. Output: all lines in original order, but with deduplicated/sorted import block

**Java Header Generation:**
1. Parse file path to extract package (from directory structure)
2. Extract class name from filename (without .java/.kt extension)
3. Generate: `package com.example;` and `public class ClassName { }`

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: EndpointTest suite passes.
- `cd engine && sbt graalvm-native-image:packageBin` -- expected: binary builds successfully.
- `./engine/target/graalvm-native-image/cumulus-engine extract-endpoints --dir <spring-project>` -- expected: returns list of endpoints.
- `echo 'import java.util.List; import java.util.Set;' | ./engine/target/graalvm-native-image/cumulus-engine optimize-imports` -- expected: deduplicated, sorted imports.
- `./engine/target/graalvm-native-image/cumulus-engine generate-java-header --file src/main/java/com/example/MyClass.java` -- expected: returns package and class boilerplate.

## Suggested Review Order

**CLI Integration**

- extract-endpoints, optimize-imports, generate-java-header subcommand dispatch.
  [`Main.scala:420`](../../../engine/src/main/scala/cumulus/Main.scala#L420)

**Endpoint Detection**

- Spring and JAX-RS annotation patterns; class-level path resolution.
  [`EndpointScanner.scala:12`](../../../engine/src/main/scala/cumulus/code/EndpointScanner.scala#L12)

**Import Optimization**

- Deduplication via Set, lexical sorting, static import preservation.
  [`ImportOptimizer.scala:8`](../../../engine/src/main/scala/cumulus/code/ImportOptimizer.scala#L8)

**Java Header Generation**

- Package inference from directory structure; class name extraction.
  [`JavaHeaderGenerator.scala:10`](../../../engine/src/main/scala/cumulus/code/JavaHeaderGenerator.scala#L10)

**Data Models**

- Endpoint, EndpointsResponse, ImportsResponse, JavaHeader case classes.
  [`CodeModels.scala:28`](../../../engine/src/main/scala/cumulus/code/CodeModels.scala#L28)

**Test Coverage**

- Endpoint detection, path resolution, import optimization, header generation.
  [`EndpointTest.scala:1`](../../../engine/src/test/scala/cumulus/code/EndpointTest.scala#L1)

## Spec Change Log

**Iteration 1** — Successful implementation:
- All 138 tests passing (122 from previous stories + 16 new)
- All three subcommands working correctly
- Native image builds successfully
- Ready for production use
