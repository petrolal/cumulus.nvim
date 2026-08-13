---
title: 'Epic 1 Story 1.2: Protocol Envelope (CumulusResponse[T]) & CLI Router with Ping'
type: 'feature'
created: '2026-08-12'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'ac22fcb929d7716b9bf57f48e480dfaf49c10e7e'
context: ['_bmad-output/implementation-artifacts/epic-1-context.md', '_bmad-output/implementation-artifacts/spec-1-1-sbt-scala3-engine-init.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The engine has a build foundation but no standardized protocol for Neovim integration. Without a typed JSON envelope and subcommand router, callers cannot invoke engine capabilities or understand response structure.

**Approach:** Implement `CumulusResponse[T]` envelope (success/data/error/error_code), `CumulusError` enum with standard codes, and a pattern-matching CLI router in `Main.scala`. Verify with `ping` subcommand (returns success, no data) and unknown-command error handling.

## Boundaries & Constraints

**Always:**
- Use Scala 3 top-level `def` for protocol functions; case classes with `derives` for serialization.
- Zero runtime reflection — uPickle compile-time `macroRW` only. No Jackson, Gson, Play-JSON.
- Stdout reserved for JSON payloads only; stderr for logging/debug.
- Response envelope schema: `{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`.
- Error codes: `FILE_NOT_FOUND`, `PARSE_ERROR`, `INVALID_INPUT`, `NETWORK_ERROR`, `TIMEOUT`, `INTERNAL_ERROR`.
- Unknown subcommands return `INVALID_INPUT` error, not crash.
- No external CLI parsing library (no StructOpt, Scopt); direct `args: Array[String]` pattern matching.

**Ask First:**
- Adding subcommands beyond `ping`.
- Changing envelope schema or error codes.
- Adding logging framework (currently debug output to stderr is manual).

**Never:**
- Do not implement subcommand logic (e.g., actual "file list" or "metadata" operations) — that's Story 1.3+.
- Do not use reflection-based serializers.
- Do not mix stdout/stderr — JSON to stdout, debug to stderr only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ping subcommand | `cumulus-engine ping` | stdout: `{"success": true, "data": null, "error": null, "error_code": null}` | N/A |
| unknown subcommand | `cumulus-engine unknown-cmd` | stdout: `{"success": false, "data": null, "error": "Unknown subcommand", "error_code": "INVALID_INPUT"}` | Return error envelope, exit 0 |
| no subcommand | `cumulus-engine` (no args) | stdout: `{"success": false, "data": null, "error": "No subcommand provided", "error_code": "INVALID_INPUT"}` | Return error envelope, exit 0 |
| JSON serialization | CumulusResponse with data | JSON serializes all fields correctly | Fail build if macroRW derivation fails |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/protocol/Envelope.scala` -- CREATE: `CumulusResponse[T]` case class (success, data, error, error_code), `CumulusError` enum, and `macroRW` derivations
- `engine/src/main/scala/cumulus/Main.scala` -- MODIFY: Replace placeholder println with pattern-matching router on `args(0)` for `ping`; handle no-args and unknown-cmd cases
- `engine/build.sbt` -- VERIFY READ-ONLY: Already has uPickle dependency; no config changes needed

## Tasks & Acceptance

**Execution:**
- [ ] `engine/src/main/scala/cumulus/protocol/Envelope.scala` -- CREATE: `CumulusResponse[T]` case class with success, data (Option[T]), error (Option[String]), error_code (Option[String]) fields; `CumulusError` enum; `macroRW` derivations for both
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- MODIFY: Replace hardcoded println with CLI router matching `args(0)` for `ping` subcommand; return JSON envelope via `upickle.default.write` to stdout
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- MODIFY: Handle zero args and unknown subcommands by returning INVALID_INPUT error envelope to stdout, exit code 0

**Acceptance Criteria:**
- Given `cumulus-engine ping`, when the binary is invoked, then stdout contains valid JSON `{"success": true, "data": null, "error": null, "error_code": null}`
- Given `cumulus-engine unknown-cmd`, when the binary is invoked, then stdout contains valid JSON with `"success": false`, `"error_code": "INVALID_INPUT"`
- Given zero arguments, when `cumulus-engine` is invoked, then stdout contains valid JSON error envelope with INVALID_INPUT
- Given `CumulusResponse[T]` case class with uPickle `macroRW`, when `sbt compile` is run, then compilation succeeds with zero errors

## Design Notes

**Protocol Envelope Schema:**
The `CumulusResponse[T]` case class is generic over the data type `T`. All four fields are top-level: `success` (Boolean, indicates op success), `data` (Option[T], null if error or no data), `error` (Option[String], human message if failure), `error_code` (Option[String], machine code if failure).

**CLI Router Design:**
Direct pattern match on `args(0)` with Scala 3 match syntax:
```scala
val result = if args.isEmpty then errorEnvelope("No subcommand provided")
             else args(0) match
               case "ping" => successEnvelope(null)
               case _ => errorEnvelope("Unknown subcommand")
upickle.default.write(result) // prints to stdout
```

**Error Code Enum:**
Create a sealed enum or ADT `CumulusError` with six codes. Use `error_code.toString` when serializing.

**uPickle Macros:**
Derive Read/Write with `given RW[CumulusResponse[T]]: ReadWriter[CumulusResponse[T]] = macroRW` for the envelope. For specific data types (e.g., ping returns no data), use `Option.None`.

## Verification

**Commands:**
- `cd engine && sbt compile` -- expected: `[success]` with zero errors
- `cd engine && sbt run ping` -- expected: stdout contains `{"success":true,"data":null,"error":null,"error_code":null}` (or with whitespace)
- `cd engine && sbt run unknown` -- expected: stdout contains error envelope with `"error_code":"INVALID_INPUT"`
- `cd engine && sbt run` -- expected: stdout contains error envelope, INVALID_INPUT, zero-args case

**Manual checks:**
- Inspect `engine/src/main/scala/cumulus/protocol/Envelope.scala` for `macroRW` derivation syntax and no reflection usage.
- Verify no print statements go to stdout except JSON (check Main.scala stderr for debug output if added).

## Suggested Review Order

**Protocol Envelope & Serialization**

- Custom JSON serialization with Option-to-null mapping and safe deserialization guards
  [`Envelope.scala:23-50`](../../../engine/src/main/scala/cumulus/protocol/Envelope.scala#L23)

- CumulusError enum with six standardized error codes derived for JSON serialization
  [`Envelope.scala:7-13`](../../../engine/src/main/scala/cumulus/protocol/Envelope.scala#L7)

**CLI Router & Response Handling**

- Pattern-matching router for subcommands; ping uses envelope helper for consistency
  [`Main.scala:24-30`](../../../engine/src/main/scala/cumulus/Main.scala#L24)

- Response envelope helpers (success/error) with CumulusError enum integration
  [`Main.scala:8-22`](../../../engine/src/main/scala/cumulus/Main.scala#L8)
