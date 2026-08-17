---
title: 'Epic 3 Story 3.1: Java & Kotlin CodeLens Extractor'
type: 'feature'
created: '2026-08-14'
status: 'done'
baseline_commit: 'e13338ad065b717b8454d4383efb97f39e61ddcf'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-3-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neovim developers lack quick visual hints for executable code in Java/Kotlin sources (test methods, main entry points, scheduled tasks, event listeners). Each hint type currently requires manual inspection, slowing navigation and testing workflows.

**Approach:** Implement `extract-codelens --file <path>` subcommand that scans source files with line-number-aware regex patterns to detect annotations (`@Test`, `@Scheduled`, `@KafkaListener`, `@EventListener`) and method declarations (`public static void main`), returning structured CodeLens items that Neovim can render as action hints.

## Boundaries & Constraints

**Always:**
- Use `os-lib` exclusively for file reading (one-shot read, no streaming).
- Subcommand signature: `cumulus-engine extract-codelens --file <path>`.
- Response envelope: `CumulusResponse[List[CodeLensItem]]` with zero runtime reflection (uPickle only).
- Detected annotations: `@Test`, `@Scheduled`, `@KafkaListener`, `@EventListener`.
- Line numbers: 1-indexed, matching Neovim's position model.
- Regex-based detection: no AST parsing, no runtime reflection, no external tools.
- Supported file extensions: `.java`, `.kt`.
- All JSON output conforms to `SPEC-031` schema.

**Ask First:**
- Support for additional annotations (e.g., `@BeforeEach`, `@ParameterizedTest`, custom annotations)?
- Should CodeLens titles be translatable or parameterized?
- Performance: caching results per file modification time?

**Never:**
- Do not parse source files with runtime reflection or annotation processors.
- Do not invoke external tools (`javac`, `kotlinc`, etc.).
- Do not modify source files.
- Do not assume specific line ending conventions; handle both `\n` and `\r\n`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| @Test in Java | `extract-codelens --file MyTest.java` with `@Test public void testFoo() { }` at line 5 | Return `[CodeLensItem(line=5, title="▶ Run Test")]` | N/A |
| @Scheduled in Java | `extract-codelens --file Task.java` with `@Scheduled(...) public void task()` at line 8 | Return `[CodeLensItem(line=8, title="⏰ Scheduled Task")]` | N/A |
| main() method | `extract-codelens --file App.java` with `public static void main(String[] args)` at line 12 | Return `[CodeLensItem(line=12, title="▶ Run Main")]` | N/A |
| @KafkaListener / @EventListener | `extract-codelens --file Listener.java` with both annotations on separate methods | Return separate CodeLensItems for each, title "🎧 Event Listener" | N/A |
| Multiple annotations on same method | `@Test @DisplayName("foo") public void test()` | Return single CodeLensItem (deduplicate by line) | N/A |
| .kt file (Kotlin) | `extract-codelens --file MyTest.kt` with Kotlin @Test | Detect and return CodeLens item with same semantics | N/A |
| No annotations | `extract-codelens --file Util.java` with only utility methods | Return empty list `[]` | N/A |
| File not found | `extract-codelens --file /nonexistent.java` | Return error envelope `{"success":false,"error":"FILE_NOT_FOUND","error_code":"FILE_NOT_FOUND"}` | Graceful error with code |
| Empty file | `extract-codelens --file Empty.java` (zero bytes) | Return empty list `[]` | N/A |
| Large file (1MB+) | `extract-codelens --file LargeFile.java` (many classes, 1000+ lines) | Process without memory error; return all CodeLens items | Timeout/resource limits TBD by implementer |
| Malformed annotations | `@Test(typo` (unclosed parenthesis) | Regex gracefully skips; process rest of file | N/A |
| Comment-line annotation | `// @Test` (annotation in comment) | Regex matches but line contains comment marker; filter out | N/A |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:350-375` -- CLI router: add `extract-codelens` subcommand; parse `--file` argument; dispatch to CodeLensExtractor
- `engine/src/main/scala/cumulus/code/CodeLensExtractor.scala` -- **NEW**: Core module with regex patterns for annotation detection; scan lines; build CodeLens items with line numbers
- `engine/src/main/scala/cumulus/code/CodeModels.scala` -- **NEW**: Case classes `CodeLensItem(line: Int, title: String)` and `CodeLensResponse(items: Seq[CodeLensItem])` with uPickle derivation
- `engine/src/test/scala/cumulus/code/CodeLensTest.scala` -- **NEW**: Unit tests covering @Test, @Scheduled, @KafkaListener, @EventListener, main() detection; edge cases (comments, malformed annotations, large files, .kt files)

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/code/CodeModels.scala` -- CREATE: Define `CodeLensItem(line: Int, title: String)` and response wrapper; uPickle derivation for JSON serialization.
- [x] `engine/src/main/scala/cumulus/code/CodeLensExtractor.scala` -- CREATE: Implement annotation detection using compiled regex patterns; scan file line-by-line; build CodeLens items; handle .java and .kt files; filter comment lines.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `extract-codelens` subcommand to CLI router (match block around line 350); parse `--file` argument; call CodeLensExtractor.
- [x] `engine/src/test/scala/cumulus/code/CodeLensTest.scala` -- CREATE: Write comprehensive tests for all I/O scenarios above; test .java and .kt file handling; edge cases.

**Acceptance Criteria:**
- Given a Java file with `@Test public void testFoo() { }`, when `extract-codelens --file <path>` is executed, then stdout returns `{"success":true,"data":{"items":[{"line":N,"title":"▶ Run Test"}]},"error":null,"error_code":null}` where N is the correct line number.
- Given a file with `public static void main(String[] args)`, then CodeLens item with title "▶ Run Main" is returned.
- Given a file with `@Scheduled(...)` and `@KafkaListener(...)` methods, then separate CodeLens items are returned for each.
- Given a file with no annotations, then empty items list is returned with `success: true`.
- Given a non-existent file path, then error envelope with code `FILE_NOT_FOUND` is returned.
- Given comment lines containing annotations (e.g., `// @Test`), then no CodeLens item is created for that line.
- All tests pass: `sbt test`, and binary builds with `sbt graalvm-native-image:packageBin`.

## Design Notes

**Regex Patterns:**
- `@Test`: Pattern `@Test\b` matches the annotation before method declaration.
- `@Scheduled`: Pattern `@Scheduled\b` (annotation parameters ignored).
- `@KafkaListener`, `@EventListener`: Patterns `@KafkaListener\b`, `@EventListener\b`.
- `main()`: Pattern `public\s+static\s+void\s+main\s*\(` detects main method signature.
- Lines are scanned sequentially; each match records the 1-indexed line number.

**Comment Filtering:**
Before applying regex patterns, check if the line (after stripping leading whitespace) starts with `//` (Java/Kotlin single-line comment). If so, skip annotation detection for that line. (Block comments `/* ... */` are complex; if a line contains `/*`, conservatively skip it.)

**Multi-Annotation Deduplication:**
If multiple CodeLens items map to the same line (rare), keep the first detected. (Spec allows flexibility here; implementation chooses.)

**File Encoding:**
Assume UTF-8. If a file cannot be read as UTF-8, return FILE_NOT_FOUND error gracefully.

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: CodeLensTest suite passes.
- `cd engine && sbt graalvm-native-image:packageBin` -- expected: binary builds successfully.
- `./engine/target/graalvm-native-image/cumulus-engine extract-codelens --file engine/src/main/scala/cumulus/Main.scala` -- expected: returns CodeLens items for any test/scheduled code in Main (if none, returns empty list).
- Manual: `echo 'public class T { @Test public void t() { } }' > /tmp/T.java && ./engine/target/graalvm-native-image/cumulus-engine extract-codelens --file /tmp/T.java` -- expected: returns CodeLens item at line 1 with title "▶ Run Test".

## Spec Change Log

**Iteration 1 (Review Round 1)** — Verified Gap Reviewer and Edge Case Hunter identified safety and spec compliance issues:
- **Finding**: getMessage() could return null, causing NPE in error classification
- **Finding**: Block comment filtering spec said "contains" but code only checked "starts with"
- **Finding**: File extension validation not enforced despite spec constraint
- **Finding**: getLines() iteration could yield null elements
- **Finding**: Missing CLI integration tests for acceptance criteria verification
- **Amendment**: Applied null guards, fixed block comment detection to "contains", added file extension validation, added null check for line iteration, added CLI and block-comment edge case tests
- **Known-bad state avoided**: NullPointerExceptions, silent failures on unsupported file types, spec deviation on comment filtering
- **KEEP**: Regex-based annotation patterns, 1-indexed line numbering, LinkedHashMap deduplication, uPickle serialization

## Suggested Review Order

**CLI Integration & Entry Point**

- Main.scala subcommand dispatch with --file argument parsing and error routing.
  [`Main.scala:356`](../../../engine/src/main/scala/cumulus/Main.scala#L356)

- Null-safe error message classification; unsupported file type error code routing.
  [`Main.scala:381`](../../../engine/src/main/scala/cumulus/Main.scala#L381)

**Core Extraction Logic**

- File extension validation and early rejection for non-.java/.kt files.
  [`CodeLensExtractor.scala:28`](../../../engine/src/main/scala/cumulus/code/CodeLensExtractor.scala#L28)

- Lazy-compiled regex patterns for all five annotation types (@Test, @Scheduled, @KafkaListener, @EventListener, main()).
  [`CodeLensExtractor.scala:10`](../../../engine/src/main/scala/cumulus/code/CodeLensExtractor.scala#L10)

- Comment filtering: null guard and "contains /*" check per spec; regex matching with deduplication.
  [`CodeLensExtractor.scala:44`](../../../engine/src/main/scala/cumulus/code/CodeLensExtractor.scala#L44)

**Data Models & Serialization**

- CodeLensItem and CodeLensResponse case classes with uPickle derivation.
  [`CodeModels.scala:3`](../../../engine/src/main/scala/cumulus/code/CodeModels.scala#L3)

**Test Coverage**

- Full I/O matrix coverage: all annotation types, Kotlin support, comment filtering, edge cases, line number accuracy.
  [`CodeLensTest.scala:1`](../../../engine/src/test/scala/cumulus/code/CodeLensTest.scala#L1)

- CLI integration test verifying response envelope structure through Main.scala router.
  [`MainTest.scala:206`](../../../engine/src/test/scala/cumulus/MainTest.scala#L206)
