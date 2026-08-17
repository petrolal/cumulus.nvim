# Epic 3 Context: Code Intelligence & Framework Analysis

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

JVM developers receive instant Java/Kotlin CodeLens hints, Spring Boot debug configurations, Spring Bean dependency graphs, REST endpoint extraction, import optimization, and Java header generation — all using scala-xml for XML parsing where applicable. This epic enables deep code intelligence that integrates seamlessly with Neovim's LSP ecosystem.

## Stories

- Story 3.1: Java & Kotlin CodeLens Extractor
- Story 3.2: Spring Boot Debug Config & Bean Dependency Graph
- Story 3.3: REST Endpoint Extractor, Import Optimizer & Java Header Generator

## Requirements & Constraints

**Functional Requirements:**
- Implement `extract-codelens --file <path>` to detect `@Test`, `main`, `@Scheduled`, and event listener annotations (`@KafkaListener`, `@EventListener`) in Java/Kotlin files, returning CodeLens items with line numbers and action titles.
- Implement `detect-springboot-app --dir .` to identify the Spring Boot main class, active profiles, JVM debug arguments, and project metadata from source and configuration files.
- Implement `parse-spring-beans --dir .` to extract Spring Bean definitions using reflection on annotations (`@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`), including dependency injection metadata (`@Autowired`, `@Inject` fields).
- Implement `extract-endpoints --dir .` to discover REST endpoints with HTTP method, path (resolving class-level `@RequestMapping` base paths), class name, and handler method. Support both Spring (`@GetMapping`, `@PostMapping`, etc.) and JAX-RS (`@Path`, `@GET`, etc.) annotations.
- Implement `optimize-imports` (via stdin) to deduplicate import statements, sort lexically using `SortedSet`, and output formatted import block after package declaration.
- Implement `generate-java-header --file <path>` to infer package name from directory structure (`/src/main/java/`, `/src/`) and generate `package ...;` and `public class ClassName { }` boilerplate.
- All output uses `CumulusResponse[T]` envelope with zero runtime reflection.

**Non-Functional Requirements:**
- Filesystem operations must use `os-lib` for clean, recursive directory traversal.
- CodeLens line numbers must be accurate and 1-indexed to match Neovim's position model.
- Spring Bean analysis must handle all supported stereotypes and correctly track dependency chains.
- Import optimization must preserve non-import lines and maintain correct formatting.

## Technical Decisions

- **Annotation Detection via Regex:** Source code scanning uses compiled regex patterns to identify annotations (`@Test`, `@Scheduled`, `@RequestMapping`, etc.) without runtime reflection. Patterns are applied line-by-line for streaming efficiency.
- **Scala 3 Idiomatic Code (AD-1):** All modules use top-level definitions, case classes, and functional composition.
- **Zero-Reflection Serialization (AD-2):** Data structures for CodeLens items, Spring metadata, endpoints, and bean definitions use uPickle macros. No reflection-based serializers.
- **CLI Router Extensibility (AD-3):** New subcommands integrated via pattern-matching in `cumulus.Main`.
- **Directory Recursion Strategy:** For source discovery (`detect-springboot-app`, `parse-spring-beans`, `extract-endpoints`), scan `src/main/java/`, `src/main/kotlin/`, and `src/test/java/` directories using `os-lib`.
- **Profile Resolution:** Active profiles extracted from `application.yml` / `application.properties` using simple key-value parsing (not full YAML/properties parser).

## Cross-Story Dependencies

**Internal (this epic):**
- Story 3.1 (CodeLens) is independent and can be built first.
- Story 3.2 (Spring Boot detection) is independent but benefits from Story 3.1's file scanning patterns.
- Story 3.3 (Endpoints & Imports) depends on file scanning infrastructure from Stories 3.1 and 3.2.

**Downstream (future epics):**
- Epic 4 (Testing) may call `detect-springboot-app` to identify test context in Spring Boot projects.
- Epic 6 (Lua Bridge) refactors `lua/cumulus/util/` files to delegate code analysis to engine subcommands from this epic.

**Upstream (already satisfied):**
- Epic 1 (Engine Foundation) — CLI router and protocol envelope complete.
- Epic 2 (Build Tooling) — workspace discovery and build tool detection available for locating source directories.

## Module Structure (Planned)

- `engine/src/main/scala/cumulus/code/CodeLensExtractor.scala` — Annotation detection via regex patterns.
- `engine/src/main/scala/cumulus/code/SpringBootDetector.scala` — Main class and profile discovery.
- `engine/src/main/scala/cumulus/code/BeanGraphAnalyzer.scala` — Spring Bean and dependency tracking.
- `engine/src/main/scala/cumulus/code/EndpointScanner.scala` — REST endpoint discovery for Spring/JAX-RS.
- `engine/src/main/scala/cumulus/code/ImportOptimizer.scala` — Import deduplication and sorting.
- `engine/src/main/scala/cumulus/code/JavaHeaderGenerator.scala` — Package inference and class boilerplate.
- `engine/src/main/scala/cumulus/data/CodeModels.scala` — Case classes for CodeLens, Spring metadata, endpoints, and imports.
