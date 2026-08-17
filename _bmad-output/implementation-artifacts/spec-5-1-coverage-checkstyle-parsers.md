---
title: 'Epic 5 Story 5.1: JaCoCo Coverage & Checkstyle Report Parsers (scala-xml)'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: '8c2aeaf'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-5-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neovim JVM developers and DevOps workflows require fast parsing of JaCoCo XML code coverage reports and Checkstyle XML audit reports without regex fragility or heavy external tool execution.

**Approach:** Implement `CoverageParser` and `CheckstyleParser` in Scala using `scala-xml` (`org.scala-lang.modules::scala-xml`) with XPath-like node selectors (`\` and `\\`), exposed via `cumulus-engine parse-coverage` and `parse-checkstyle` CLI subcommands returning standardized `CumulusResponse[T]` envelopes.

## Boundaries & Constraints

**Always:**
- All subcommands must return standard `CumulusResponse[T]` envelope (`{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`).
- Zero runtime reflection: use `uPickle` compile-time macros (`derives ReadWriter` or `macroRW`).
- Stdout is strictly reserved for JSON payloads; all errors and diagnostics go to stderr.
- All XML parsing must strictly use `scala-xml` (`scala.xml.XML`) with node traversal (`\` and `\\`). No regex-based XML parsing or SAX event streaming (NFR6).
- Use `os-lib` for filesystem path validation and file reading (NFR7).
- `parse-coverage` accepts `--file <path>` (e.g. `jacoco.xml`) and outputs `Seq[CoverageEntry]` where each entry contains `file` (package-qualified relative path, e.g. `com/example/App.java`), `covered_lines` (Seq of line numbers where `ci > 0`), and `missed_lines` (Seq of line numbers where `ci == 0 && mi > 0`).
- `parse-checkstyle` accepts input via stdin or `--file <path>` and outputs `Seq[CheckstyleDiagnostic]` where each entry contains `file` (String), `line` (Int, default 1), `col` (Option[Int]), `severity` (String, uppercase), and `message` (String).

**Ask First:**
- If an XML document contains multiple top-level root elements or unknown wrapper tags, should parser gracefully skip or return `PARSE_ERROR`? (Default: return `PARSE_ERROR` on malformed XML).

**Never:**
- Never use regex to parse XML structure (NFR6).
- Never use runtime-reflection serializers (Jackson, Gson, snakeyaml).
- Never modify input XML files or source files.
- Never write non-JSON text to stdout.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| JaCoCo XML standard report | `parse-coverage --file target/site/jacoco/jacoco.xml` with `<package name="com/example"><sourcefile name="App.java"><line nr="10" ci="5" mi="0"/><line nr="15" ci="0" mi="2"/></sourcefile></package>` | `{"success":true,"data":[{"file":"com/example/App.java","covered_lines":[10],"missed_lines":[15]}]}` | N/A |
| JaCoCo default package | `<package name=""><sourcefile name="Root.java"><line nr="1" ci="1" mi="0"/></sourcefile></package>` | `{"success":true,"data":[{"file":"Root.java","covered_lines":[1],"missed_lines":[]}]}` | N/A |
| JaCoCo missing file | `parse-coverage --file /nonexistent/jacoco.xml` | `{"success":false,"data":null,"error":"File not found: ...","error_code":"FILE_NOT_FOUND"}` | FILE_NOT_FOUND code |
| JaCoCo malformed XML | `parse-coverage --file bad.xml` containing invalid XML syntax | `{"success":false,"data":null,"error":"XML parse error: ...","error_code":"PARSE_ERROR"}` | PARSE_ERROR code |
| Checkstyle report via stdin | `parse-checkstyle` with stdin: `<checkstyle version="10.0"><file name="/src/App.java"><error line="15" column="5" severity="warning" message="Missing Javadoc."/></file></checkstyle>` | `{"success":true,"data":[{"file":"/src/App.java","line":15,"col":5,"severity":"WARNING","message":"Missing Javadoc."}]}` | N/A |
| Checkstyle report via `--file` | `parse-checkstyle --file checkstyle-result.xml` | `{"success":true,"data":[{"file":"...","line":...,"col":...,"severity":"...","message":"..."}]}` | N/A |
| Checkstyle missing column | `<file name="Foo.java"><error line="20" severity="error" message="Syntax error"/></file>` | Diagnostic with `col = null` (Option None), `line = 20`, `severity = "ERROR"` | Gracefully handles absent column |
| Checkstyle empty files | `<checkstyle><file name="Clean.java"/></checkstyle>` | `{"success":true,"data":[]}` | Empty list with success: true |
| Empty input to checkstyle | Empty stdin / file with 0 bytes | `{"success":true,"data":[]}` | Returns empty list gracefully |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Case classes for `CoverageEntry` and `CheckstyleDiagnostic` with uPickle `ReadWriter` derivations matching SPEC-031 schemas.
- `engine/src/main/scala/cumulus/devops/CoverageParser.scala` -- JaCoCo XML report parser utilizing `scala-xml` traversal (`report \ "package"`, `pkg \ "sourcefile"`, `sf \ "line"`).
- `engine/src/main/scala/cumulus/devops/CheckstyleParser.scala` -- Checkstyle XML report parser utilizing `scala-xml` traversal (`checkstyle \ "file"`, `f \ "error"`).
- `engine/src/main/scala/cumulus/Main.scala` -- Subcommand routing for `parse-coverage` and `parse-checkstyle`.
- `engine/src/test/scala/cumulus/devops/CoverageParserTest.scala` -- Unit tests covering JaCoCo XML parsing, package handling, covered/missed line filtering, error handling.
- `engine/src/test/scala/cumulus/devops/CheckstyleParserTest.scala` -- Unit tests covering Checkstyle XML parsing via stdin and file, severity uppercase mapping, column options.
- `engine/src/test/scala/cumulus/MainTest.scala` -- CLI integration tests for `parse-coverage` and `parse-checkstyle`.

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Create data models `CoverageEntry` (`file: String`, `covered_lines: Seq[Int]`, `missed_lines: Seq[Int]`) and `CheckstyleDiagnostic` (`file: String`, `line: Int`, `col: Option[Int]`, `severity: String`, `message: String`) with uPickle `ReadWriter`.
- [x] `engine/src/main/scala/cumulus/devops/CoverageParser.scala` -- Implement `CoverageParser.parseCoverage(filePath: String): CumulusResponse[Seq[CoverageEntry]]` and `CoverageParser.parseJacocoXml(xmlContent: String): Seq[CoverageEntry]` using `scala.xml.XML` and `os-lib`.
- [x] `engine/src/main/scala/cumulus/devops/CheckstyleParser.scala` -- Implement `CheckstyleParser.parseCheckstyle(content: String): CumulusResponse[Seq[CheckstyleDiagnostic]]` and `CheckstyleParser.parseCheckstyleFile(filePath: String): CumulusResponse[Seq[CheckstyleDiagnostic]]` using `scala.xml.XML` and `os-lib`.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- Wire `parse-coverage` and `parse-checkstyle` subcommands into CLI pattern matcher with argument and stdin support.
- [x] `engine/src/test/scala/cumulus/devops/CoverageParserTest.scala` -- Create comprehensive unit tests for JaCoCo XML report parser.
- [x] `engine/src/test/scala/cumulus/devops/CheckstyleParserTest.scala` -- Create comprehensive unit tests for Checkstyle XML audit report parser.
- [x] `engine/src/test/scala/cumulus/MainTest.scala` -- Add CLI end-to-end test cases for `parse-coverage` and `parse-checkstyle`.

**Acceptance Criteria:**
- Given a valid JaCoCo XML report file, when `cumulus-engine parse-coverage --file <path>` is executed, then stdout returns `CumulusResponse[Seq[CoverageEntry]]` with covered and missed line numbers grouped per package-qualified source file.
- Given a Checkstyle XML report via stdin or `--file <path>`, when `cumulus-engine parse-checkstyle` is executed, then stdout returns `CumulusResponse[Seq[CheckstyleDiagnostic]]` with file paths, line, optional column, severity, and messages.
- Given non-existent or malformed XML files, error envelopes with `FILE_NOT_FOUND` or `PARSE_ERROR` are returned without throwing unhandled exceptions.
- All test suites pass via `sbt test`.

## Spec Change Log

_None._

## Design Notes

- **XML Traversal with scala-xml**:
  ```scala
  val xml = XML.loadString(xmlContent)
  for
    pkg <- xml \ "package"
    pkgName = (pkg \ "@name").text
    sf <- pkg \ "sourcefile"
    sfName = (sf \ "@name").text
    filePath = if pkgName.isEmpty then sfName else s"$pkgName/$sfName"
  yield ...
  ```
- **Option Serialization for Columns**:
  In `CheckstyleDiagnostic`, `col: Option[Int]` serializes to `Some(n) -> n` and `None -> null` seamlessly via uPickle macros and custom `CumulusResponse` envelope mapping.

## Verification

**Commands:**
- `sbt "testOnly cumulus.devops.*"` -- expected: All CoverageParser and CheckstyleParser unit tests pass.
- `sbt "testOnly cumulus.MainTest"` -- expected: All CLI integration tests pass.
- `sbt test` -- expected: All 224+ tests pass cleanly.

## Suggested Review Order

**DevOps Data Models & Envelope**

- Core data structures with compile-time zero-reflection serialization
  [`DevopsModels.scala:1`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L1)

**XML Parsers**

- JaCoCo XML report parser extracting covered and missed lines via `scala-xml`
  [`CoverageParser.scala:14`](../../engine/src/main/scala/cumulus/devops/CoverageParser.scala#L14)

- Checkstyle XML audit report parser extracting diagnostics and rules via `scala-xml`
  [`CheckstyleParser.scala:14`](../../engine/src/main/scala/cumulus/devops/CheckstyleParser.scala#L14)

**CLI Subcommand Routing**

- Subcommand dispatch for `parse-coverage` and `parse-checkstyle` with argument and stdin support
  [`Main.scala:605`](../../engine/src/main/scala/cumulus/Main.scala#L605)

**Test Suites**

- Unit tests for JaCoCo XML coverage parsing and edge cases
  [`CoverageParserTest.scala:8`](../../engine/src/test/scala/cumulus/devops/CoverageParserTest.scala#L8)

- Unit tests for Checkstyle XML audit parsing and edge cases
  [`CheckstyleParserTest.scala:8`](../../engine/src/test/scala/cumulus/devops/CheckstyleParserTest.scala#L8)

- CLI end-to-end integration tests for devops subcommands
  [`MainTest.scala:362`](../../engine/src/test/scala/cumulus/MainTest.scala#L362)

