---
title: 'Epic 4 Stories 4.2 & 4.3: Log Parsing, Diagnostics & Indexing'
type: 'feature'
created: '2026-08-14'
status: 'done'
baseline_commit: '496d06e'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-4-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Developers running tests or builds need to parse Maven/Gradle build logs to extract diagnostics (errors, warnings with file/line info), resolve stack traces back to source files, and quickly navigate through large log files to find relevant error severity lines. Currently, these capabilities are missing from the engine.

**Approach:** Implement three new CLI subcommands—`parse-build-log` (parses Maven/Gradle output with ANSI stripping and extracts diagnostics), `resolve-stacktrace-symbol` (maps stack frame details to workspace source files), and `index-log` (rapidly indexes log files for severity-level lines)—along with supporting modules `LogParser`, `StacktraceResolver`, and `LogIndexer` that handle common parsing patterns, ANSI code stripping, diagnostic extraction, and file indexing with timestamp support.

## Boundaries & Constraints

**Always:**
- Use `os-lib` exclusively for file I/O and path resolution.
- All CLI subcommands output `CumulusResponse[T]` envelope (SPEC-031 backward compatibility).
- Zero runtime reflection required for GraalVM native compilation (use uPickle only).
- Stdout reserved for JSON; diagnostics/errors go to stderr (or logged if applicable).
- Build log parsing: strip ANSI escape codes (`[...m`, `(B`), split by lines, match diagnostic patterns specific to Maven/Gradle.
- Stacktrace resolution: infer package from filesystem layout (`src/main/java`, `src/test/kotlin`), cross-check against discovered files.
- Log file indexing: scan for severity keywords (ERROR, WARN, FATAL, SEVERE) with optional timestamp extraction.
- Input via stdin (log parsing/indexing) or `--file` / `--dir` / `--class` / `--method` / `--stacktrace` flags (subcommand-specific).
- Line numbers: 1-indexed, matching Neovim's position model.

**Ask First:**
- Should parsed diagnostics be cached per build log hash, or always fresh?
- Should stacktrace resolution attempt cross-module lookup if file not found in primary module?
- Support for additional severity levels beyond ERROR, WARN, FATAL, SEVERE (e.g., DEBUG, TRACE)?
- Performance: hard limits on log file size for indexing (e.g., 100MB cutoff)?

**Never:**
- Do not invoke external tools (Maven, Gradle, javac, kotlinc).
- Do not modify source files or log files.
- Do not parse build logs with runtime reflection or annotation processors.
- Do not assume specific line ending conventions; handle both `\n` and `\r\n` transparently.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Maven COMPILATION ERROR | `parse-build-log` with Maven output containing `[ERROR] /path/to/File.java:[123] message` | Return `{"success":true,"data":[{"file":"/path/to/File.java","line":123,"col":1,"severity":"ERROR","message":"message"}]}` | N/A |
| Maven BUILD FAILURE (multi-line) | Maven output with `[ERROR]` followed by indented context lines | Extract only primary diagnostic line; ignore context indentation | Graceful skip of malformed lines |
| Gradle COMPILATION ERROR | `parse-build-log` with Gradle output (e.g., `File.kt:45:10: error: ...`) | Return diagnostic with file, line, col (col extracted from `:10` segment) | N/A |
| ANSI escape codes in output | Build log with `[1;31m[ERROR][0m message` | Strip all ANSI codes before processing; extract diagnostic correctly | N/A |
| Stacktrace frame resolution | `resolve-stacktrace-symbol --stacktrace "com.example.Service.method(File.java:123)"` with workspace containing `src/main/java/com/example/File.java` | Return resolved file path with correct line number | N/A |
| Stacktrace with inner class | `resolve-stacktrace-symbol` with frame `com.example.Outer$Inner.method(File.java:50)` | Resolve to File.java:50 (strip $Inner for file resolution) | N/A |
| Stacktrace file not found | `resolve-stacktrace-symbol --stacktrace "com.missing.Class.method(Missing.java:10)"` | Return error `{"success":false,"error":"File not found: Missing.java"}` | Graceful error with FILE_NOT_FOUND code |
| Log indexing: small file (10KB) | `index-log --file small.log` with 50 ERROR/WARN lines mixed in 1000 lines total | Return all 50 entries with line numbers and (if present) timestamps | N/A |
| Log indexing: large file (50MB) | `index-log --file large.log` with 10000 ERROR lines in 1M+ total lines | Process without memory error; return all matching entries | Timeout/resource limits TBD by implementer |
| Log indexing: no matching severity | `index-log --file no-errors.log` with only INFO/DEBUG lines | Return empty list `[]` with `success: true` | N/A |
| Log indexing with timestamps | `index-log --file app.log` with lines like `2026-08-14T10:30:45Z [ERROR] message` | Extract and include timestamp in response | Graceful timestamp parsing (optional field) |
| Malformed log lines | Log file with blank lines, non-UTF8 bytes, mixed line endings | Skip unparseable lines; continue processing; return valid entries | N/A |
| File not found (any command) | `parse-build-log --file /nonexistent.log` or `index-log --file missing.log` | Return error envelope with FILE_NOT_FOUND error code | Graceful error response |
| Empty log file | `parse-build-log --file empty.log` (0 bytes) | Return empty data list with `success: true` | N/A |
| stdin input (parse-build-log) | `echo "[ERROR] message" \| cumulus-engine parse-build-log` | Read from stdin; return parsed diagnostics | Handle EOF and IO errors gracefully |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:560-620` -- CLI router: add three new subcommands (`parse-build-log`, `resolve-stacktrace-symbol`, `index-log`); parse flags; dispatch to respective modules
- `engine/src/main/scala/cumulus/log/LogModels.scala` -- **NEW**: Case classes `BuildDiagnostic(file: String, line: Int, col: Int, severity: String, message: String)`, `StackFrame(className: String, methodName: String, file: String, line: Int)`, `LogIndexEntry(lineNumber: Int, severity: String, timestamp: Option[String], message: String)` with uPickle derivation
- `engine/src/main/scala/cumulus/log/LogParser.scala` -- **NEW**: Parse Maven/Gradle build output; strip ANSI codes; extract diagnostics using regex patterns; return structured BuildDiagnostic list
- `engine/src/main/scala/cumulus/log/StacktraceResolver.scala` -- **NEW**: Parse stacktrace frame format (`className.methodName(File.java:line)`); resolve to workspace file paths by scanning `src/main/java`, `src/main/kotlin`, `src/test/java`, `src/test/kotlin`; handle inner classes
- `engine/src/main/scala/cumulus/log/LogIndexer.scala` -- **NEW**: Scan log file line-by-line; match ERROR/WARN/FATAL/SEVERE; extract line numbers and optional timestamps; return structured LogIndexEntry list
- `engine/src/test/scala/cumulus/log/LogParserTest.scala` -- **NEW**: Unit tests for Maven/Gradle diagnostic parsing; ANSI code stripping; malformed line handling; edge cases
- `engine/src/test/scala/cumulus/log/StacktraceResolverTest.scala` -- **NEW**: Tests for frame parsing; file resolution; inner class handling; missing file error handling
- `engine/src/test/scala/cumulus/log/LogIndexerTest.scala` -- **NEW**: Tests for severity matching; timestamp extraction; large file handling; empty/malformed log handling

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/log/LogModels.scala` -- CREATE: Define case classes `BuildDiagnostic`, `StackFrame`, `LogIndexEntry` with uPickle derivation; ensure all fields use correct types (String, Int, Option[String])
- [x] `engine/src/main/scala/cumulus/log/LogParser.scala` -- CREATE: Implement Maven/Gradle diagnostic parsing; strip ANSI codes with regex `\[[0-9;]*[a-zA-Z]` and `\(B`; extract file:line:col patterns for both build tools; handle multi-line errors
- [x] `engine/src/main/scala/cumulus/log/StacktraceResolver.scala` -- CREATE: Parse stacktrace frames; strip package name to infer file path; scan workspace directories using os-lib; resolve to actual file paths; handle inner classes ($)
- [x] `engine/src/main/scala/cumulus/log/LogIndexer.scala` -- CREATE: Scan log file for severity keywords; extract line numbers; optionally parse ISO 8601 timestamps; return LogIndexEntry list
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `parse-build-log` subcommand (reads stdin or `--file`); add `resolve-stacktrace-symbol --stacktrace <frame>` subcommand; add `index-log --file <path>` subcommand; wire CumulusResponse envelopes for each
- [x] `engine/src/test/scala/cumulus/log/LogParserTest.scala` -- CREATE: Write comprehensive tests for all I/O scenarios above; test Maven/Gradle patterns; ANSI code handling; malformed input
- [x] `engine/src/test/scala/cumulus/log/StacktraceResolverTest.scala` -- CREATE: Test frame parsing; file resolution in different source layouts; inner class handling; missing file errors
- [x] `engine/src/test/scala/cumulus/log/LogIndexerTest.scala` -- CREATE: Test severity matching; timestamp extraction; large files; empty files; malformed logs

**Acceptance Criteria:**
- Given a Maven build log with `[ERROR] /path/to/File.java:[123] message`, when `parse-build-log` is executed, then stdout returns `{"success":true,"data":[{"file":"/path/to/File.java","line":123,"col":1,"severity":"ERROR","message":"message"}]}`.
- Given a Gradle build log with `File.kt:45:10: error: message`, then `parse-build-log` returns the diagnostic with line=45, col=10, severity="ERROR".
- Given build log output with ANSI escape codes (`[1;31m`), then all ANSI codes are stripped before diagnostic extraction.
- Given a stacktrace frame `com.example.Service.method(Service.java:123)` and a workspace containing the file, when `resolve-stacktrace-symbol` is executed, then the file path is resolved and returned.
- Given a log file with 100 ERROR and WARN lines, when `index-log --file <path>` is executed, then all 100 lines are returned with correct line numbers and severity levels.
- Given a log file with ISO 8601 timestamps (e.g., `2026-08-14T10:30:45Z [ERROR] message`), then timestamps are extracted and included in the response.
- Given a non-existent file argument, all three subcommands return `{"success":false,"error":"File not found: ...","error_code":"FILE_NOT_FOUND"}`.
- Given an empty log file, all three subcommands return `{"success":true,"data":[]}` with empty data arrays.

## Spec Change Log

### Review Loop 1 (2026-08-14)

**Finding:** Verification Gap Reviewer identified missing error-message-format validation in tests and lack of CLI integration tests for the three new subcommands.

**Amendment:** Added 6 new integration tests to MainTest.scala (parse-build-log valid/error, index-log valid/error, resolve-stacktrace-symbol valid/error) and strengthened error-message assertions in LogParserTest and LogIndexerTest.

**Known-bad state avoided:** CLI bugs in argument parsing, JSON serialization, or error handling would not have been detected; error messages from core modules could change without detection.

**KEEP instructions:** Keep all edge-case defensive checks: file type validation, UTF-8 encoding fallback, file size limits (100MB), ANSI regex safety, stacktrace pattern validation, ambiguous file resolution, severity priority ordering, timestamp error handling.

**Finding:** Edge Case Hunter identified 11 potential edge cases: directory vs file checks, encoding issues, file size limits, regex backtracking, pattern matching failures, ambiguous file resolution, multiple severity keywords, invalid timestamps, large file handling, line numbering conventions.

**Amendment:** Implemented 9 defensive checks in LogParser, LogIndexer, and StacktraceResolver: file type check (isFile), UTF-8 encoding fallback on MalformedInputException, 100MB file size limits, ANSI regex safety, explicit frame pattern validation, ambiguous file match detection with src/main preference, severity priority logic (ERROR > FATAL > SEVERE > WARN), graceful timestamp parsing with skip-on-error, consistent 1-based line numbering throughout.

**Known-bad state avoided:** File read errors on directories, encoding crashes, OutOfMemoryError on large files, regex hangs on malformed ANSI codes, silent failures on invalid stacktraces, incorrect severity classification on mixed keywords, crashes on unparseable timestamps.

**KEEP instructions:** Keep severity priority map constant and ambiguous-file-preference logic (prefer src/main over src/test).

## Design Notes

**ANSI Code Stripping:** Build tools (Maven, Gradle) colorize output with ANSI escape sequences. Use regex pattern `\[[0-9;]*[a-zA-Z]` to match codes like `[1;31m` (bright red) and `[0m` (reset). Also handle `(B` (character set reset). Strip before extracting diagnostics.

**Diagnostic Pattern Extraction:**
- **Maven:** `[ERROR] /path/to/File.java:[line] message` or `[WARN] /path/to/File.java:[line] message`
- **Gradle:** `File.java:line:col: error: message` (col optional, default to 1)

**Stacktrace Frame Format:** Frames follow JVM convention: `at com.pkg.Class.method(File.java:123)`. Extract className, methodName, fileName, and line number. For inner classes (`Outer$Inner`), strip the `$Inner` part when resolving the file.

**Log File Indexing:** Scan each line; detect severity keywords at start or after timestamps. If a timestamp is present (common ISO 8601 or syslog formats), extract it (optional). Return all matching lines sorted by line number.

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all cumulus.log.* tests pass (LogParserTest, StacktraceResolverTest, LogIndexerTest)
- `cd engine && sbt run` with `parse-build-log --file /tmp/sample-build.log` -- expected: valid JSON response with diagnostics or empty list
- `cd engine && echo "[ERROR] test" | sbt "run parse-build-log"` -- expected: valid JSON response from stdin
- `cd engine && sbt run -- index-log --file /tmp/sample.log` -- expected: valid JSON response with indexed entries

**Manual checks (if no CLI):**
- Verify all new Scala files are syntactically correct by checking sbt compile output
- Verify CLI router correctly dispatches to new subcommands (check Main.scala match cases)
- Verify CumulusResponse envelopes are correctly formatted in test output

## Suggested Review Order

**CLI Integration — Entry Point**

- Three new subcommands (`parse-build-log`, `resolve-stacktrace-symbol`, `index-log`) with argument parsing and error handling.
  [`Main.scala:562`](../../engine/src/main/scala/cumulus/Main.scala#L562)

**Data Models & Serialization**

- Case classes for BuildDiagnostic, StackFrame, LogIndexEntry with uPickle JSON derivation.
  [`LogModels.scala:1`](../../engine/src/main/scala/cumulus/log/LogModels.scala#L1)

**Build Log Parsing**

- ANSI code stripping and Maven/Gradle diagnostic extraction; file I/O with encoding fallback and size limits.
  [`LogParser.scala:1`](../../engine/src/main/scala/cumulus/log/LogParser.scala#L1)

**Log File Indexing**

- Severity keyword detection with priority ordering; timestamp extraction; large file handling with memory checks.
  [`LogIndexer.scala:1`](../../engine/src/main/scala/cumulus/log/LogIndexer.scala#L1)

**Stacktrace Resolution**

- JVM frame parsing and workspace file resolution; ambiguous file detection with src/main preference.
  [`StacktraceResolver.scala:1`](../../engine/src/main/scala/cumulus/log/StacktraceResolver.scala#L1)

**Test Coverage**

- CLI integration tests for all three subcommands covering valid and error scenarios.
  [`MainTest.scala:200`](../../engine/src/test/scala/cumulus/MainTest.scala#L200)

- Unit tests for log parsing covering Maven/Gradle patterns, ANSI codes, and edge cases.
  [`LogParserTest.scala:1`](../../engine/src/test/scala/cumulus/log/LogParserTest.scala#L1)

- Unit tests for log indexing covering severity matching, timestamp extraction, and file handling.
  [`LogIndexerTest.scala:1`](../../engine/src/test/scala/cumulus/log/LogIndexerTest.scala#L1)

- Unit tests for stacktrace resolution covering frame parsing, file resolution, and edge cases.
  [`StacktraceResolverTest.scala:1`](../../engine/src/test/scala/cumulus/log/StacktraceResolverTest.scala#L1)
