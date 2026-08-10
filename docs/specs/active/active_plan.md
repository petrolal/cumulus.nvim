# Active Plan – Pending Specs Implementation

## Backlog
- Specs pending migration are listed in `docs/specs/review/`:
  - `003‑compliance‑remediation.md`
  - `007‑test‑runner.md`
  - `011‑offline‑mode.md`
  - `013‑code‑inspections.md`
  - `016‑rust‑helper‑migration.md`
  - `017‑rust‑multimodule‑stacktrace.md`
  - `018‑endpoints‑extractor.md`
  - `019‑coverage‑parser.md`
  - `020‑migration‑validator.md`
  - `021‑spring‑beans.md`
  - `022‑log‑indexer.md`
  - `023‑import‑optimizer.md`
  - `024‑k8s‑validator.md`
  - `025‑git‑conflicts.md`

## Review
- After each Rust module and Lua bridge are added, run `luac -p` on the Lua file and `cargo check` on the Rust code.
- Update the checklist in the spec file, moving it from `review/` to `active/` once the implementation is verified.

## Active
- **Context**
  - The subagent quota has been exhausted, preventing immediate execution of the teamwork subagents.
  - A timer has been scheduled for **9 897 seconds** (≈ 2 h 45 m) to notify when the quota resets.
- **Plan Steps**
  1. **Wait for quota reset** – The timer set via `schedule` will fire with the prompt:
     > "Quota reset reached. Ready to retry subagent invocations for pending specs."
  2. **On timer notification** – Re‑invoke the teamwork subagents one‑by‑one for each spec in the **Backlog** (see above).
  3. **Verification** – After each batch completes, run `bash scripts/validate.sh` and ensure all checks pass.
  4. **Completion** – When all specs have moved to `docs/specs/completed/` and validation succeeds, mark the overall task as **DONE**.

**Notes**
- This plan follows the **Rust migration idea**: each spec is implemented by adding a dedicated Rust module and corresponding Lua bridge, then validated before promotion.
- No subscription upgrade is required; the plan works within the current quota limits.
- If the quota does not reset as expected, consider upgrading the subscription or handling the remaining specs manually.
