# Epic 4 Context: Testing, Diagnostics & Log Processing

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Developers can detect test context at cursor, assemble platform-correct test CLI commands (Maven/Gradle/SBT), parse test results, resolve stacktrace frames to source files, parse build logs, and index high-speed log files. This epic absorbs test-running and diagnostics workflows that previously required Lua-to-Rust communication, consolidating them in the Scala engine for faster execution and simpler error handling.

## Stories

- Story 4.1: Test Context Detector & Test Output Parser
- Story 4.2: Build Log Diagnostic Parser & Stacktrace Drill-Down
- Story 4.3: High-Speed Log File Indexer
- Story 4.4: Test Command Assembly

## Requirements & Constraints

**Functional:**
- Detect test context (class/method name) from Java/Kotlin source at a given cursor line
- Parse JUnit 5, Maven Surefire, Gradle test output formats and extract test results (pass/fail/skip with messages)
- Parse build logs (Maven/Gradle) and extract diagnostics with file, line, column, severity, message
- Extract stacktrace frames (`at com.pkg.Class.method(File.java:123)`) and resolve to workspace file paths
- Index large log files for ERROR, WARN, FATAL, SEVERE lines with line numbers and timestamps
- Assemble correct Maven/Gradle/SBT test commands with class/method filters and multi-module flags

**Non-Functional:**
- All subcommands output `CumulusResponse[T]` envelope (SPEC-031 backward compatibility)
- Zero runtime reflection required for GraalVM native compilation
- `os-lib` for all file I/O and path resolution
- Stdout reserved for JSON; diagnostics/errors go to stderr
- Input via stdin (log parsing) or `--file` / `--dir` / `--line` flags

## Technical Decisions

**Module Structure** (per Architecture Spine):
- `log/LogIndexer.scala` — indexes log files for severity levels and timestamps
- `log/LogParser.scala` — parses Maven/Gradle build output, strips ANSI codes, extracts diagnostics
- `log/StacktraceResolver.scala` — maps stacktrace frames to workspace source files
- Test detection and command assembly in main `Main.scala` routing or dedicated `testing/` module

**Patterns:**
- Detect test class via regex or AST scan (backward from cursor line looking for `@Test` or `class` declaration)
- Log parsing: strip ANSI escape codes (`[...m`), split by lines, match diagnostic patterns (Maven/Gradle specific)
- Stacktrace resolution: infer package from filesystem path (`src/main/java`, `src/test/kotlin`, etc.) and cross-check against discovered files using `os-lib`
- Test command construction: delegate to engine subcommand; Lua simply calls engine and extracts `command` and `cwd` fields from response

**Data Schemas** (uPickle-serialized):
```scala
case class TestContext(className: String, methodName: String)
case class TestResult(className: String, methodName: String, status: String, message: Option[String])
case class BuildDiagnostic(file: String, line: Int, col: Int, severity: String, message: String)
case class StackFrame(className: String, methodName: String, file: String, line: Int)
case class LogIndexEntry(lineNumber: Int, severity: String, timestamp: Option[String], message: String)
case class TestCommand(command: String, cwd: String)
```

## Cross-Story Dependencies

- Depends on Epic 2 (build tool detection) and Epic 3 (source file enumeration) for context
- Story 4.4 (test command assembly) depends on 4.1 (test context detection) in Lua workflow, but both are independent engine subcommands
- Log parsing (4.2) and indexing (4.3) are independent but share diagnostic output format
- All stories depend on Epic 1 (CumulusResponse envelope and CLI routing)
