
- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Add Scala compiler flags (-feature, -deprecation, -unchecked, -explain) to enforce best practices
  evidence: Blind hunter identified missing compiler options for code quality enforcement

- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Configure test framework settings (munit parallelization, timeout, reporters)
  evidence: munit dependency added but no test configuration defined

- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Resolve sbt-native-packager plugin availability for GraalVM native image builds
  evidence: Spec references non-existent versions; Story 1.2 CLI work will need native binary support

- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Add sbt-assembly or fat JAR packaging for distributable artifacts
  evidence: No deployment packaging configured

- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Add sbt-scalafmt for code formatting enforcement
  evidence: Code style consistency not automated

- source_spec: `spec-1-1-sbt-scala3-engine-init.md`
  summary: Add sbt-wartremover for static analysis and anti-pattern detection
  evidence: Linting coverage missing

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Implement extensible plugin goal registry for Maven parser
  evidence: Blind hunter review identified hardcoded Spring Boot/Quarkus/Surefire/Exec plugin list; future stories may need additional plugins

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Implement Maven property and variable resolution in POM parsing
  evidence: POM files contain property references (${project.version}, etc.) that are not resolved; affects consistency of extracted goals

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Implement parent POM inheritance for goals and modules
  evidence: Maven's <parent> inheritance pattern not handled; multi-module projects with parent POMs may have incomplete goal extraction

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Implement Maven profiles support in goal and module extraction
  evidence: POM <profiles> elements that activate conditionally are not scanned; profile-specific goals/modules missed

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Implement circular dependency detection in multi-module build ordering
  evidence: Edge case not handled; circular module dependencies could cause infinite loops in build order computation (story 2.3)

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Add comprehensive POM structure validation
  evidence: Only file existence checked; no validation that file is well-formed POM with expected root elements

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Add XML encoding declaration handling for robustness
  evidence: <?xml encoding="..."> declarations may vary; parser assumes UTF-8 implicitly

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Add timeout and resource limits for Maven POM parsing
  evidence: Malformed or extremely large POMs could cause hangs or excessive memory consumption

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Enhance error response messages with parsing context and line numbers
  evidence: Error codes (FILE_NOT_FOUND, PARSE_ERROR) lack detailed context for debugging user errors

- source_spec: `spec-2-1-maven-pom-parser.md`
  summary: Add integration tests for Main.main() CLI routing of parse-pom and parse-modules
  evidence: Verification gap reviewer found acceptance criteria define CLI behavior but no tests verify end-to-end routing through Main.main(); MavenParser unit tests pass but CLI integration untested

- source_spec: none
  summary: Story 3.2 — Spring Boot Debug Config & Bean Dependency Graph (detect-springboot-app, parse-spring-beans)
  evidence: Split from Epic 3 multi-goal scope; 3.1 (CodeLens) prioritized first to establish source scanning foundation

- source_spec: none
  summary: Story 3.3 — REST Endpoint Extractor, Import Optimizer & Java Header Generator (extract-endpoints, optimize-imports, generate-java-header)
  evidence: Split from Epic 3 multi-goal scope; 3.1 (CodeLens) prioritized first to establish source scanning foundation

- source_spec: `spec-3-1-codelens-extractor.md`
  summary: Add logging infrastructure for CodeLens extraction operations
  evidence: Blind Hunter review identified lack of logging for debugging and audit trails; pre-existing gap not specific to CodeLens

- source_spec: `spec-3-1-codelens-extractor.md`
  summary: Centralize error message constants across all Main.scala subcommands
  evidence: Blind Hunter review flagged hardcoded error strings; applies to all CLI handlers, not just CodeLens

- source_spec: `spec-3-1-codelens-extractor.md`
  summary: Consolidate given ReadWriter definitions to prevent duplication
  evidence: Inline ReadWriter definitions appear in every subcommand handler; should be centralized for maintainability

- source_spec: `spec-3-1-codelens-extractor.md`
  summary: Add path security validation (traversal, symbolic link) to file access operations
  evidence: Verification Gap Reviewer identified missing validation; applies to all file-reading subcommands (parse-pom, extract-codelens, etc.)
