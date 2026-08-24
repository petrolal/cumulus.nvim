# Sprint Quick Reference for Agents

**Use this to quickly find stories by sprint, and check dependencies.**

---

## Sprint 1: Monorepo Foundation (26 pts, Weeks 1-2)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 1.1 | Repository Consolidation | 5 | DevOps | — | Ready |
| 1.2 | Unified Build Configuration | 8 | Build | 1.1 | Ready |
| 1.3 | Shared Build Infrastructure | 5 | Build | 1.2 | Ready |
| 1.4 | Test Infrastructure Unification | 5 | QA | 1.2 | Ready |
| 1.5 | Documentation Structure Update | 3 | Docs | 1.1 | Ready |

**What agents should know:**
- This is the **foundation** — nothing else can start until 1.1 completes
- 1.2 blocks 1.3 and 1.4 — so sequence: 1.1 → 1.2 → {1.3, 1.4} in parallel, 1.5 can start after 1.1
- Success = Monorepo compiles, all tests pass, docs updated

---

## Sprint 2: Bootstrap & Installation (34 pts, Weeks 3-4)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 2.1 | Enhanced Bootstrap Architecture | 8 | DevOps | E1 | Ready |
| 2.2 | Engine Building & Installation | 5 | Build | 2.1 | Ready |
| 2.3 | Frontend Installation & Linking | 5 | Frontend | 2.1 | Ready |
| 2.4 | Plugin Synchronization | 3 | Frontend | 2.3 | Ready |
| 2.5 | Health Check & Verification | 5 | QA | 2.4 | Ready |
| 2.6 | Desktop Environment Setup | 5 | Desktop | 2.1 | Ready |
| 3.1 | Shared Configuration Root | 3 | Infra | 2.1 | Ready |

**What agents should know:**
- Start 2.1 first (depends on E1 completion)
- Then can start: 2.2, 2.3, 2.6 in parallel (all depend on 2.1)
- 2.4 depends on 2.3, 2.5 depends on 2.4
- 3.1 is the start of E3, can begin immediately after 2.1
- Success = `bash bootstrap.sh` works end-to-end, IDE launches

---

## Sprint 3: Configuration System (21 pts, Weeks 5-6)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 3.2 | Neovim Configuration Linking | 3 | Frontend | 3.1 | Ready |
| 3.3 | LSP Configuration & Defaults | 5 | Backend | 3.1 | Ready |
| 3.4 | Theme System Integration | 5 | UI/Design | 3.1, 2.6 | Ready |
| 3.5 | Plugin Configuration Unification | 3 | Frontend | 3.2 | Ready |

**What agents should know:**
- 3.1 must complete before any of these (it's from Sprint 2)
- 3.2 must complete before 3.5
- 3.4 depends on both 3.1 and 2.6 (from Sprint 2)
- Success = ~/.cumulus/ is single source, changes apply automatically

---

## Sprint 4: JVM Languages (24 pts, Weeks 7-8)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 4.1 | Java Language Server Setup | 8 | Language Support | E3 | Ready |
| 4.2 | Kotlin Language Server Setup | 8 | Language Support | E3 | Ready |
| 4.3 | Scala & Groovy Support | 8 | Language Support | E3 | Ready |

**What agents should know:**
- All 3 depend on E3 (Sprint 3 must complete first)
- These can run in **full parallel** — no interdependencies
- Success = All JVM LSPs working; completion, navigation verified

---

## Sprint 5: IDE Features & Cloud Tools (21 pts, Weeks 9-10)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 4.4 | Cloud Native Tool Support | 8 | Cloud Tools | E3 | Ready |
| 4.5 | IntelliJ-Compatible Keybindings | 5 | Frontend | 4.1 | Ready |
| 4.6 | Code Navigation & Refactoring | 8 | Navigation | 4.1, 4.2 | Ready |

**What agents should know:**
- 4.4 only depends on E3 (can start immediately)
- 4.5 depends on 4.1 (from Sprint 4)
- 4.6 depends on 4.1 and 4.2 (from Sprint 4)
- 4.4 can start in parallel with 4.5 and 4.6
- Success = IDE has IntelliJ-parity features; keybindings work; cloud tools integrated

---

## Sprint 6: Platform & Distribution (21 pts, Weeks 11-12)

| Story ID | Title | Points | Owner | Depends | Status |
|----------|-------|--------|-------|---------|--------|
| 5.1 | macOS Platform Support | 8 | Platform Eng | E2 | Ready |
| 5.2 | GitHub Releases & Distribution | 8 | DevOps | E1-E4 | Ready |
| 5.3 | Update Mechanism | 5 | Updates | 5.2 | Ready |

**What agents should know:**
- 5.1 depends on E2 (Sprint 2), can start immediately
- 5.2 depends on E1-E4 (all must complete first)
- 5.3 depends on 5.2
- 5.1 can run in parallel with 5.2 (if E2 complete and 5.2 waits for E1-E4)
- Success = Pre-built binaries work; macOS verified; updates atomic

---

## Dependency Chain (Critical Path)

```
Sprint 1 (E1)
  ↓ Blocks everything
Sprint 2 (E2, E3.1)
  ↓ Blocks E3, E5.1
Sprint 3 (E3.2-E3.5)
  ↓ Blocks E4
Sprint 4 (E4.1-E4.3)
  ↓ Blocks E4.4-E4.6 partially
Sprint 5 (E4.4-E4.6)
  ↓ Blocks E5.2 (together with E1-E4)
Sprint 6 (E5)
```

---

## Quick Lookup by Story ID

**Story not in this list?** Check EPICS_AND_STORIES.md for full details.

### By Sprint
- **Sprint 1:** 1.1, 1.2, 1.3, 1.4, 1.5
- **Sprint 2:** 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1
- **Sprint 3:** 3.2, 3.3, 3.4, 3.5
- **Sprint 4:** 4.1, 4.2, 4.3
- **Sprint 5:** 4.4, 4.5, 4.6
- **Sprint 6:** 5.1, 5.2, 5.3

### By Owner
- **DevOps:** 1.1, 2.1, 2.2, 5.2
- **Build/Infrastructure:** 1.2, 1.3, 2.2
- **Frontend:** 2.3, 2.4, 3.2, 3.5, 4.5
- **Backend:** 3.3, 4.1, 4.2, 4.3
- **Cloud Tools:** 4.4
- **QA:** 1.4, 2.5
- **Desktop:** 2.6, 3.4
- **Documentation:** 1.5
- **Platform Eng:** 5.1
- **Navigation:** 4.6
- **UI/Design:** 3.4

---

## For Agents: How to Use This

### When Starting a Sprint
1. Look up the sprint in the table above
2. Note all story IDs and their dependencies
3. Check if all **Depends** stories are marked **Ready**
4. If not ready, check previous sprint status
5. Start stories with **no dependencies** first

### When Executing a Story
1. Find the story in this table
2. Check **Depends** column for blockers
3. Read full narrative in EPICS_AND_STORIES.md
4. Reference daily tasks in SPRINT_IMPLEMENTATION_CHECKLIST.md
5. Report status against **acceptance criteria** (not tasks)

### When Blocked
1. Check the **Depends** column
2. Verify blocking story is marked **Ready** or **Complete**
3. If blocking story not started, escalate: "Sprint N, Story X.Y blocks us"
4. Use the critical path diagram to find workarounds

### When Updating Status
Use this format:
```
Sprint 2, Story 2.3 (Frontend Installation & Linking)
Status: In Progress (Day 2 of 3)
Progress: 40% — Cloned cumulus-nvim, created config dirs, working on symlinks
Blockers: None
ETA: On track (complete by end of day)
```

---

## Common Questions

**Q: Can I start Sprint 3 before Sprint 2 finishes?**
A: Only story 3.1 is in Sprint 2. If story 2.1 completes (day 1), start 3.1 immediately. But don't start 3.2-3.5 until Sprint 3 officially begins.

**Q: What if my team is slow on Sprint 1?**
A: Everything blocks on Sprint 1 completion. Escalate immediately. See velocity tracking in SPRINT_IMPLEMENTATION_CHECKLIST.md.

**Q: Are Sprint 4 and 5 stories truly parallel?**
A: Yes. 4.1, 4.2, 4.3 have no interdependencies. But 4.5 and 4.6 depend on 4.1 and 4.2 completing.

**Q: What's the fastest path through all 6 sprints?**
A: Critical path: 1.1 → 1.2 → 2.1 → 3.1 → 4.1 → 5.2 → 6.2. Everything else is parallel or sequential within same sprint.

---

**Document Status:** 🟢 Ready for agent use  
**Last Updated:** 2026-08-24  
**Pairs With:** AGENTS.md (architecture), EPICS_AND_STORIES.md (narratives), SPRINT_IMPLEMENTATION_CHECKLIST.md (daily execution)
