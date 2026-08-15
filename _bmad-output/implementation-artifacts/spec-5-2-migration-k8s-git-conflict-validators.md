---
title: 'Epic 5 Story 5.2: Flyway Migration, Kubernetes Manifest & Git Conflict Validators'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: 'dd786ad'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-5-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neovim JVM and cloud-native developers need fast, zero-reflection validation of Flyway database migrations, Kubernetes YAML manifests, and Git conflict marker detection directly within the Scala engine.

**Approach:** Implement `MigrationValidator`, `K8sValidator`, and `ConflictParser` in Scala 3 with uPickle compile-time serialization, exposed via `cumulus-engine validate-migrations`, `validate-k8s-manifest`, and `parse-git-conflicts` CLI subcommands returning standardized `CumulusResponse[T]` envelopes.

## Boundaries & Constraints

**Always:**
- All subcommands must return standard `CumulusResponse[T]` envelope (`{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`).
- Zero runtime reflection: use `uPickle` compile-time macros (`derives ReadWriter` or `macroRW`).
- Stdout is strictly reserved for JSON payloads; all errors and diagnostics go to stderr or error envelope fields.
- All filesystem traversals and file I/O must strictly use `os-lib` (NFR7).
- `validate-migrations` accepts `--dir <path>` (defaulting to current directory `.`) and returns `Seq[MigrationIssue]` with `file` (String), `line` (Option[Int]), `severity` (String, "ERROR" | "WARN"), and `message` (String).
- `validate-k8s-manifest` accepts input via stdin or `--file <path>` and returns `Seq[K8sValidationIssue]` with `line` (Int, 1-indexed), `col` (Option[Int]), `severity` (String, "ERROR"), and `message` (String).
- `parse-git-conflicts` accepts input via stdin or `--file <path>` and returns `Seq[ConflictBlock]` with `start_line` (Int, 1-indexed), `sep_line` (Int, 1-indexed), `end_line` (Int, 1-indexed), `current_header` (String), and `incoming_header` (String).

**Ask First:**
- N/A

**Never:**
- Never use runtime-reflection serializers (Jackson, Gson, SnakeYAML).
- Never modify input files or repository source trees.
- Never write non-JSON text to stdout.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Flyway duplicate versions | Directory with `V1__init.sql` and `V1__dup.sql` | `MigrationIssue` with `severity: "ERROR"` and message containing `Duplicate Flyway migration version: V1` | N/A |
| Flyway invalid naming | Directory with `bad_name.sql` | `MigrationIssue` with `severity: "WARN"` and message containing `does not match standard Flyway convention` | N/A |
| Flyway repeatable & undo migrations | `R__views.sql` and `U1__undo.sql` | Valid parsed files; repeatable migrations skip version uniqueness check | N/A |
| Flyway non-existent directory | `validate-migrations --dir /nonexistent` | `{"success":true,"data":[]}` | Gracefully returns empty list |
| K8s missing apiVersion | Manifest with `kind: Deployment` without `apiVersion:` | `K8sValidationIssue` at line 1 with message `Missing top-level 'apiVersion' field in Kubernetes manifest` | N/A |
| K8s missing kind | Manifest with `apiVersion: v1` without `kind:` | `K8sValidationIssue` at line 1 with message `Missing top-level 'kind' field in Kubernetes manifest` | N/A |
| K8s valid manifest | Manifest with both `apiVersion: v1` and `kind: Pod` | `{"success":true,"data":[]}` | N/A |
| K8s non-k8s YAML | Content lacking both `apiVersion` and `kind` | `{"success":true,"data":[]}` | N/A |
| Git conflict standard block | Buffer with `<<<<<<< HEAD\nfoo\n=======\nbar\n>>>>>>> feature` | `ConflictBlock(start_line = 1, sep_line = 3, end_line = 5, current_header = "HEAD", incoming_header = "feature")` | N/A |
| Git conflict bare markers | `<<<<<<<\nfoo\n=======\nbar\n>>>>>>>` | `ConflictBlock` with `current_header = "HEAD"` and `incoming_header = "INCOMING"` | Graceful fallback headers |
| Git conflict multiple blocks | Buffer containing multiple conflict sections | Returns sequence of all completed conflict blocks | N/A |
| Git conflict empty / clean file | Content with no conflict markers | `{"success":true,"data":[]}` | Returns empty list |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `MigrationIssue` and `K8sValidationIssue` data models with uPickle `ReadWriter`.
- `engine/src/main/scala/cumulus/git/GitModels.scala` -- Create `ConflictBlock` model with uPickle `ReadWriter`.
- `engine/src/main/scala/cumulus/devops/MigrationValidator.scala` -- Flyway migration directory scanner enforcing version uniqueness and naming conventions via `os-lib`.
- `engine/src/main/scala/cumulus/devops/K8sValidator.scala` -- Kubernetes YAML manifest validator checking top-level `apiVersion` and `kind` declarations.
- `engine/src/main/scala/cumulus/git/ConflictParser.scala` -- Git merge conflict marker parser extracting line ranges and headers.
- `engine/src/main/scala/cumulus/Main.scala` -- CLI routing for `validate-migrations`, `validate-k8s-manifest`, and `parse-git-conflicts`.
- `engine/src/test/scala/cumulus/devops/MigrationValidatorTest.scala` -- Unit tests for Flyway migration validation logic.
- `engine/src/test/scala/cumulus/devops/K8sValidatorTest.scala` -- Unit tests for Kubernetes manifest validation.
- `engine/src/test/scala/cumulus/git/ConflictParserTest.scala` -- Unit tests for Git conflict marker parsing.
- `engine/src/test/scala/cumulus/MainTest.scala` -- CLI end-to-end integration tests for Story 5.2 subcommands.

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `MigrationIssue` (`file: String`, `line: Option[Int]`, `severity: String`, `message: String`) and `K8sValidationIssue` (`line: Int`, `col: Option[Int]`, `message: String`, `severity: String`) case classes deriving `ReadWriter`.
- [x] `engine/src/main/scala/cumulus/git/GitModels.scala` -- Create `ConflictBlock` (`start_line: Int`, `sep_line: Int`, `end_line: Int`, `current_header: String`, `incoming_header: String`) deriving `ReadWriter`.
- [x] `engine/src/main/scala/cumulus/devops/MigrationValidator.scala` -- Implement `MigrationValidator.validateMigrationsDir(dir: String): CumulusResponse[Seq[MigrationIssue]]` and `validateMigrations(dirPath: os.Path): Seq[MigrationIssue]`.
- [x] `engine/src/main/scala/cumulus/devops/K8sValidator.scala` -- Implement `K8sValidator.validateK8sManifest(content: String): CumulusResponse[Seq[K8sValidationIssue]]` and file variant.
- [x] `engine/src/main/scala/cumulus/git/ConflictParser.scala` -- Implement `ConflictParser.parseGitConflicts(content: String): CumulusResponse[Seq[ConflictBlock]]` and file variant.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- Wire `validate-migrations`, `validate-k8s-manifest`, and `parse-git-conflicts` into CLI router.
- [x] `engine/src/test/scala/cumulus/devops/MigrationValidatorTest.scala` -- Test Flyway migration validation rules and directory handling.
- [x] `engine/src/test/scala/cumulus/devops/K8sValidatorTest.scala` -- Test Kubernetes manifest validation rules via string and file.
- [x] `engine/src/test/scala/cumulus/git/ConflictParserTest.scala` -- Test Git conflict marker extraction with line positions and headers.
- [x] `engine/src/test/scala/cumulus/MainTest.scala` -- Add CLI integration tests for all 3 subcommands.

**Acceptance Criteria:**
- Given a directory of SQL scripts, when `cumulus-engine validate-migrations --dir <path>` runs, then duplicate version numbers are flagged as `ERROR` and non-standard filenames are flagged as `WARN`.
- Given a Kubernetes YAML buffer via stdin or `--file`, when `cumulus-engine validate-k8s-manifest` runs, missing `apiVersion` or `kind` fields are reported with line number and severity `ERROR`.
- Given a file with Git merge conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`), when `cumulus-engine parse-git-conflicts` runs, start line, separator line, end line, current header, and incoming header are correctly extracted.
- All test suites pass via `sbt test`.

## Spec Change Log

_None._

## Design Notes

- **Flyway Regex Patterns**:
  - Versioned migration: `^(V|U)(\d+(?:\.\d+)*)__([A-Za-z0-9_]+)\.sql$`
  - Repeatable migration: `^R__([A-Za-z0-9_]+)\.sql$`
- **Git Conflict Marker Parsing**:
  - `<<<<<<< ` prefix or `<<<<<<<` exact (header defaults to `"HEAD"`)
  - `=======` prefix
  - `>>>>>>> ` prefix or `>>>>>>>` exact (header defaults to `"INCOMING"`)
  - 1-indexed line numbers matching Neovim buffer coordinates.

## Verification

**Commands:**
- `sbt "testOnly cumulus.devops.MigrationValidatorTest"` -- expected: All migration validation tests pass.
- `sbt "testOnly cumulus.devops.K8sValidatorTest"` -- expected: All K8s manifest validation tests pass.
- `sbt "testOnly cumulus.git.ConflictParserTest"` -- expected: All conflict parser tests pass.
- `sbt "testOnly cumulus.MainTest"` -- expected: CLI integration tests pass.
- `sbt test` -- expected: All unit and integration tests pass cleanly.

## Suggested Review Order

**DevOps & Git Data Models**

- Core data models with compile-time zero-reflection serialization via uPickle macros
  [`DevopsModels.scala:31`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L31)

- Git conflict block model with start, separator, and end line markers
  [`GitModels.scala:1`](../../engine/src/main/scala/cumulus/git/GitModels.scala#L1)

**Validators & Parsers**

- Flyway database migration directory validator enforcing version uniqueness and naming
  [`MigrationValidator.scala:7`](../../engine/src/main/scala/cumulus/devops/MigrationValidator.scala#L7)

- Kubernetes YAML manifest validator checking top-level apiVersion and kind declarations
  [`K8sValidator.scala:7`](../../engine/src/main/scala/cumulus/devops/K8sValidator.scala#L7)

- Git merge conflict parser extracting 1-indexed conflict blocks and headers
  [`ConflictParser.scala:7`](../../engine/src/main/scala/cumulus/git/ConflictParser.scala#L7)

**CLI Subcommand Routing**

- Subcommand dispatch for validate-migrations, validate-k8s-manifest, and parse-git-conflicts
  [`Main.scala:630`](../../engine/src/main/scala/cumulus/Main.scala#L630)

**Test Suites**

- Unit tests for Flyway migration validation rules and directory handling
  [`MigrationValidatorTest.scala:6`](../../engine/src/test/scala/cumulus/devops/MigrationValidatorTest.scala#L6)

- Unit tests for Kubernetes manifest validation across single and multi-document YAML
  [`K8sValidatorTest.scala:6`](../../engine/src/test/scala/cumulus/devops/K8sValidatorTest.scala#L6)

- Unit tests for Git conflict marker extraction and fallback headers
  [`ConflictParserTest.scala:6`](../../engine/src/test/scala/cumulus/git/ConflictParserTest.scala#L6)

- CLI end-to-end integration tests for all Story 5.2 subcommands
  [`MainTest.scala:403`](../../engine/src/test/scala/cumulus/MainTest.scala#L403)
