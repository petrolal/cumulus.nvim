
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
