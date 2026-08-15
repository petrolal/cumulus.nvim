---
title: 'Epic 4 Stories 4.1 & 4.4: Test Detection & Command Assembly'
type: 'feature'
created: '2026-08-14'
status: 'done'
baseline_commit: 'd162fb9'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-4-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** JVM developers in Neovim need to detect test context at cursor and assemble platform-correct test CLI commands efficiently. Currently, test command assembly (Maven/Gradle/SBT) and test output parsing require Lua business logic or external tool coordination. This is slow and error-prone.

**Approach:** Implement three Scala engine subcommands: `detect-test-context --file <path> --line <n>` to find test class/method at cursor, `parse-test-output` (via stdin) to parse test results (JUnit/Maven/Gradle), and `assemble-test-command --tool maven|gradle|sbt --class <name> --method <name> --dir <path>` to generate correct platform-specific test CLI commands. All return `CumulusResponse[T]` envelope. This moves test detection and command assembly into the engine, eliminating Lua business logic.

## Boundaries & Constraints

**Always:**
- All subcommands return `CumulusResponse[T]` envelope (SPEC-031 backward compatibility)
- Use `os-lib` exclusively for file I/O and directory traversal
- Zero runtime reflection (uPickle `derives ReadWriter` only, no Jackson/Gson/reflection)
- Stdout reserved for JSON; all debug/error text to stderr
- Subcommand names and signatures must match SPEC-031 exactly:
  - `detect-test-context --file <path> --line <n>` → `TestContext(class_name, method_name)`
  - `parse-test-output` (via stdin) → `TestResult[]` (class_name, method_name, status, message)
  - `assemble-test-command --tool maven|gradle|sbt --class <name> --method <name> --dir <path>` → `TestCommand(command, cwd)`
- Data models: all `case class` with `derives ReadWriter` (uPickle compile-time serialization)
- Detect test methods: `@Test`, `@ParameterizedTest`, `@RepeatedTest` (JUnit 5) in both Java and Kotlin files
- Test output parsing: support JUnit 5 XML, Maven Surefire, and Gradle test output formats

**Ask First:**
- For test command assembly: should multi-module projects auto-detect the correct module, or require explicit `--module` flag?
- Should `detect-test-context` return all test methods in a file, or only the one enclosing the cursor line?

**Never:**
- Do not invoke external tools (mvn, gradle, sbt, javac)
- Do not modify source files
- Do not use runtime reflection for annotation detection
- Do not hardcode file paths; use flag parameters
- Do not cache results across invocations (each call is independent)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Detect test at cursor (Java) | `--file src/test/java/MyTest.java --line 42` where line 42 is in `@Test void myTest()` method | Return `{"class_name":"MyTest","method_name":"myTest"}` | INVALID_INPUT if no @Test method at/before line |
| Detect test in Kotlin | `--file src/test/kotlin/MyTest.kt --line 50` with `@Test fun myTest()` | Return `{"class_name":"MyTest","method_name":"myTest"}` | N/A (support both .java and .kt) |
| Detect parameterized test | Cursor on `@ParameterizedTest` or `@RepeatedTest` method | Detect and return test class/method | N/A (support all JUnit 5 test annotations) |
| Parse JUnit 5 XML | Maven Surefire output via stdin with XML test results | Extract and return `[TestResult(..., status="PASSED", message=null)]` | PARSE_ERROR if XML malformed |
| Parse test with failure | Test output including failure message and stack trace | Return `status="FAILED"` with message extracted | N/A (partial parse OK) |
| Parse Gradle test output | Gradle `gradle test` output via stdin with `MyTest > myTest PASSED` | Extract test results with status (PASSED/FAILED/SKIPPED) | N/A (flexible format matching) |
| Multiple test methods in file | File with 3 `@Test` methods, cursor on line 35 (middle method) | Return the enclosing test method at cursor line | N/A (return single result per cursor position) |
| Assemble Maven single-module | `--tool maven --class FooTest --method testBar --dir .` in single-module project | Return `{"command":"mvn test -Dtest=FooTest#testBar","cwd":"/project/root"}` | N/A (always return valid command) |
| Assemble Gradle multi-module | `--tool gradle --class FooTest --method testBar --dir subproject/` with multi-module structure | Detect module context, return `gradle :subproject:test --tests FooTest.testBar` | N/A (auto-detect module from dir) |
| Assemble SBT test | `--tool sbt --class FooTest --method testBar --dir .` | Return `{"command":"sbt \"testOnly *FooTest -- -t testBar\"","cwd":"/project/root"}` | N/A (always return valid command) |
| Use Maven wrapper | `--tool maven --class Test --method foo --dir /project` with `./mvnw` present | Prefer wrapper: `command":"./mvnw test -Dtest=Test#foo"` | N/A (fallback to `mvn` if no wrapper) |
| No tests in file | `--file src/main/java/MyClass.java --line 10` | Return error `INVALID_INPUT` (no test methods in non-test source) | N/A |

</frozen-after-approval>

## Code Map

**Story 4.1: Test Context Detection & Test Output Parsing**
- `engine/src/main/scala/cumulus/testing/TestContextDetector.scala` -- **NEW** (50–70 lines): Scan Java/Kotlin source for `@Test`, `@ParameterizedTest`, `@RepeatedTest` annotations; locate test class/method by backward scan from cursor line; extract class name from file scope
- `engine/src/main/scala/cumulus/testing/TestOutputParser.scala` -- **NEW** (80–120 lines): Parse JUnit 5 XML, Maven Surefire text, and Gradle test output; extract TestResult(class_name, method_name, status, message)
- `engine/src/main/scala/cumulus/testing/TestModels.scala` -- **NEW** (15–25 lines): Define `TestContext(class_name: String, method_name: String)`, `TestResult(class_name, method_name, status, message)` with `derives ReadWriter`
- `engine/src/main/scala/cumulus/Main.scala:470-490` -- EDIT: Add `detect-test-context` (parse args, call TestContextDetector, wrap in CumulusResponse) and `parse-test-output` (read stdin, call TestOutputParser, return results) subcommand handlers

**Story 4.4: Test Command Assembly**
- `engine/src/main/scala/cumulus/testing/TestCommandAssembler.scala` -- **NEW** (100–150 lines): Generate Maven/Gradle/SBT test CLI commands; detect multi-module projects via directory scan; prefer wrapper executables (mvnw, gradlew)
- `engine/src/main/scala/cumulus/testing/TestModels.scala:26-30` -- EDIT: Add `TestCommand(command: String, cwd: String)` with `derives ReadWriter`
- `engine/src/main/scala/cumulus/Main.scala:500-520` -- EDIT: Add `assemble-test-command` (parse args --tool --class --method --dir, call TestCommandAssembler, return TestCommand) subcommand handler

**Testing & Verification**
- `engine/src/test/scala/cumulus/testing/TestingTest.scala` -- **NEW** (200–250 lines): Comprehensive munit tests for all I/O scenarios from edge-case matrix (test detection, output parsing, test command assembly)

## Tasks & Acceptance

**Execution:**

**Story 4.1 (Test Context Detection & Output Parsing):**
- [x] `engine/src/main/scala/cumulus/testing/TestModels.scala` -- CREATE: Define `TestContext(class_name, method_name)` and `TestResult(class_name, method_name, status, message)` case classes with `derives ReadWriter`
- [x] `engine/src/main/scala/cumulus/testing/TestContextDetector.scala` -- CREATE: Implement `def detectTestContext(filePath: String, lineNumber: Int): Either[String, TestContext]` to scan for `@Test` annotations and locate enclosing test method via backward search
- [x] `engine/src/main/scala/cumulus/testing/TestOutputParser.scala` -- CREATE: Implement `def parseTestOutput(input: String): Either[String, Seq[TestResult]]` supporting JUnit 5 XML and Maven/Gradle text formats
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `detect-test-context` (line 481) and `parse-test-output` (line 504) subcommand handlers with CumulusResponse wrapping

**Story 4.4 (Test Command Assembly):**
- [x] `engine/src/main/scala/cumulus/testing/TestCommandAssembler.scala` -- CREATE: Implement `def assembleTestCommand(tool: String, className: String, methodName: String, dirPath: String): Either[String, TestCommand]` to generate Maven/Gradle/SBT test commands with multi-module detection and wrapper preference
- [x] `engine/src/main/scala/cumulus/testing/TestModels.scala` -- EDIT: Add `TestCommand(command, cwd)` case class with `derives ReadWriter`
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `assemble-test-command` (line 542) subcommand handler parsing --tool --class --method --dir flags

**Testing:**
- [x] `engine/src/test/scala/cumulus/testing/TestingTest.scala` -- CREATE: Comprehensive munit FunSuite tests covering all I/O edge cases (test detection in Java/Kotlin, test parsing formats, test command assembly for Maven/Gradle/SBT with/without multi-module)
- [x] `sbt test` -- All existing + new tests pass (160 total)
- [x] Code compiles successfully and all 160 tests pass

**Acceptance Criteria:**
- Given a Java test file with `@Test void myTest()` at line 42, when `detect-test-context --file <path> --line 42` is executed, then stdout returns `{"success":true,"data":{"class_name":"MyTest","method_name":"myTest"},"error":null,"error_code":null}`
- Given Gradle test output via stdin with `MyTest > myTest PASSED`, when `parse-test-output` is executed, then stdout returns test results with status=PASSED and message=null
- Given test output with failure, when `parse-test-output` is executed, then status=FAILED with message extracted from stack trace
- Given `--tool maven --class FooTest --method testBar --dir .` in single-module Maven project, when `assemble-test-command` is executed, then command is `mvn test -Dtest=FooTest#testBar`
- Given `--tool gradle --class FooTest --method testBar --dir subproject` with `./gradlew` present, when `assemble-test-command` is executed, then command is `./gradlew :subproject:test --tests FooTest.testBar`
- Given `--tool sbt --class FooTest --method testBar --dir .`, when `assemble-test-command` is executed, then command is `sbt "testOnly *FooTest -- -t testBar"`
- All tests pass: `sbt test`
- Binary builds successfully: `sbt graalvm-native-image:packageBin`

## Spec Change Log

**Iteration 1 — Code Review & Patch Loop (2026-08-14)**
- **Findings**: Blind Hunter (15) + Edge Case Hunter (2) reviewed; Verification Gap Reviewer found no gaps
- **Patches applied**: 6 patch-level fixes applied successfully
  1. Standardized parse-test-output response handling (consistent error codes via CumulusError enum)
  2. Removed unused regex patterns (mavenTestLinePattern, failurePattern)
  3. Explicit UTF-8 encoding for all source file operations
  4. Broad exception handling in parse-test-output (all Exception types, not just IOException)
  5. Maven test output truncation handling (flush pending failure blocks)
  6. Test cleanup verification (prevent file accumulation on cleanup failure)
- **Known-bad state avoided**: Inconsistent error codes, silent exception handling, truncated test results, test file accumulation
- **Keep instructions**: All 160 tests pass; error response format remains SPEC-031 compliant; CumulusResponse envelope unchanged

## Design Notes

**Test Context Detection Pattern:**
1. Use lazy val compiled regex patterns: `@Test`, `@ParameterizedTest`, `@RepeatedTest` (JUnit 5)
2. Scan file line-by-line backward from cursor line to locate nearest `@Test` annotation preceding the cursor
3. Extract enclosing test method name from line pattern (e.g., `void myTest()` or `fun myTest()`)
4. Extract class name by scanning forward from file start until first `class` or `record` keyword

**Test Output Parser Format Support:**
1. **JUnit 5 XML**: Parse `<testcase>` elements; extract `name` attribute for method, `classname` for class; check for `<failure>` child for status
2. **Maven Surefire text**: Match lines like `Tests run: 5, Failures: 0, Errors: 0, Skipped: 1`; parse individual test outcomes from `FAILURE` blocks with file/line
3. **Gradle test output**: Match lines like `MyTest > testMethod PASSED / FAILED / SKIPPED`; extract from Gradle summary format

**Test Command Assembly:**
- **Maven** (single-module): `mvn test -Dtest=ClassName#methodName`
- **Maven** (multi-module): `mvn -pl :moduleName test -Dtest=ClassName#methodName` (detect via pom.xml scanning upward)
- **Gradle** (single-module): `gradle test --tests ClassName.methodName`
- **Gradle** (multi-module): `gradle :moduleName:test --tests ClassName.methodName` (detect via settings.gradle or directory structure)
- **SBT**: `sbt "testOnly *ClassName -- -t methodName"`
- **Wrapper preference**: Check for `./mvnw`, `./gradlew` in project root; use if present, else fall back to `mvn`/`gradle`
- **Return format**: Always return valid command with `cwd` (project root discovered by walking up for build file)

## Suggested Review Order

**CLI Routing & Entry Points**

- Three new subcommands dispatch via pattern matching; test-workflow coordination
  [`Main.scala:481`](../../../engine/src/main/scala/cumulus/Main.scala#L481)

**Test Context Detection**

- Scan Java/Kotlin files backward from cursor; extract test class/method via regex
  [`TestContextDetector.scala:277`](../../../engine/src/main/scala/cumulus/testing/TestContextDetector.scala#L277)

**Test Output Parsing**

- Support JUnit 5 XML, Maven Surefire, Gradle formats; handle truncation gracefully
  [`TestOutputParser.scala:425`](../../../engine/src/main/scala/cumulus/testing/TestOutputParser.scala#L425)

**Test Command Assembly**

- Generate Maven/Gradle/SBT commands; auto-detect modules and prefer wrappers
  [`TestCommandAssembler.scala:133`](../../../engine/src/main/scala/cumulus/testing/TestCommandAssembler.scala#L133)

**Data Models**

- Shared case classes with uPickle serialization; TestContext, TestResult, TestCommand
  [`TestModels.scala:373`](../../../engine/src/main/scala/cumulus/testing/TestModels.scala#L373)

**Test Coverage**

- 20+ munit tests covering all I/O scenarios, edge cases, and error paths
  [`TestingTest.scala:596`](../../../engine/src/test/scala/cumulus/testing/TestingTest.scala#L596)

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all 138+ tests pass, including new TestingTest suite
- `cd engine && sbt graalvm-native-image:packageBin` -- expected: binary builds successfully with <10ms startup
- `./engine/target/graalvm-native-image/cumulus-engine detect-test-context --file src/test/java/FooTest.java --line 42` -- expected: returns `{"success":true,"data":{"class_name":"FooTest","method_name":"testBar"}}`
- `gradle test 2>&1 | ./engine/target/graalvm-native-image/cumulus-engine parse-test-output` -- expected: test results with status and optional messages
- `mvn test 2>&1 | ./engine/target/graalvm-native-image/cumulus-engine parse-test-output` -- expected: Maven Surefire results parsed
- `./engine/target/graalvm-native-image/cumulus-engine assemble-test-command --tool maven --class FooTest --method testBar --dir .` -- expected: returns `{"success":true,"data":{"command":"mvn test -Dtest=FooTest#testBar","cwd":"/project"}}`
- `./engine/target/graalvm-native-image/cumulus-engine assemble-test-command --tool gradle --class FooTest --method testBar --dir subproject` -- expected: returns Gradle test command with module
- `./engine/target/graalvm-native-image/cumulus-engine assemble-test-command --tool sbt --class FooTest --method testBar --dir .` -- expected: returns SBT test command
