# BMAD Sprint Implementation Checklist
## Cumulus Monorepo Architecture Transformation

**Project:** cumulus.nvim + cumulus.dotfiles → Single Monorepo  
**Duration:** 6 Sprints (12 weeks)  
**Start Date:** [Set by team]  
**Owner:** [Assign team lead]  
**Status:** Ready for Sprint Planning  

---

## Pre-Sprint Setup (Day 1)

### Repository & Tools
- [ ] Create feature branch: `feature/monorepo-consolidation`
- [ ] Setup project management tool (Jira/Linear/GitHub Projects)
- [ ] Create epics in tool matching EPICS_AND_STORIES.md
- [ ] Create stories and assign story points
- [ ] Setup CI/CD testing infrastructure
- [ ] Create Slack channel for sprint coordination
- [ ] Setup daily standup time (15 min)
- [ ] Create retrospective doc template

### Team Assignment
- [ ] Assign Sprint Lead
- [ ] Assign Epic owners (one per epic)
- [ ] Assign story owners (pair programming where needed)
- [ ] Define code review process (PR template, checklist)
- [ ] Setup continuous integration notifications

### Documentation
- [ ] Copy EPICS_AND_STORIES.md to project wiki
- [ ] Copy REFACTORING_PLAN.md to project wiki
- [ ] Copy SHARED_CONFIGURATION.md to project wiki
- [ ] Create SPRINT_1_DETAILS.md (daily breakdown)
- [ ] Setup progress tracking spreadsheet

---

# SPRINT 1: MONOREPO FOUNDATION
**Duration:** 2 weeks  
**Goal:** Consolidate repos, establish unified build system  
**Definition of Done:** Monorepo structure ready for bootstrap development

## Pre-Sprint 1 (Day 1-2)

### Kickoff Meeting
- [ ] Review EPICS_AND_STORIES.md with team
- [ ] Review REFACTORING_PLAN.md architecture decisions
- [ ] Explain monorepo benefits (single CI, easier installation)
- [ ] Walk through all 5 stories for Sprint 1
- [ ] Answer questions about blockers/dependencies
- [ ] Set success criteria for end of sprint

### Environment Setup
- [ ] All team members have repo access
- [ ] Create development branch from `feature/monorepo-consolidation`
- [ ] Setup local development environment
- [ ] Test that builds work locally: `sbt compile`
- [ ] Verify git history accessible

### Dependency Planning
- [ ] Confirm no blockers for Story 1.1 (no other branches affected)
- [ ] Story 1.2 depends on 1.1 (build.sbt after repo merged)
- [ ] Story 1.3 depends on 1.2 (shared plugins after build.sbt)
- [ ] Story 1.4 depends on 1.2 (tests after modules defined)
- [ ] Story 1.5 can run in parallel (documentation)

---

## STORY 1.1: Repository Consolidation

**Owner:** DevOps  
**Points:** 5  
**Days:** 2-3

### Task Checklist

#### Phase 1: Preparation (Day 1)
- [ ] Create working branch: `feature/1.1-repo-consolidation`
- [ ] Clone both repos locally for analysis
- [ ] Document cumulus.nvim current structure
- [ ] Document cumulus.dotfiles current structure
- [ ] Identify any uncommitted changes (should be clean)
- [ ] Backup git history from both repos
- [ ] Create migration guide skeleton

#### Phase 2: Repository Merge (Day 2)
- [ ] Create cumulus-nvim/ directory in cumulus.dotfiles
- [ ] Copy cumulus.nvim contents preserving structure
  - [ ] lua/ → cumulus-nvim/lua/
  - [ ] init.lua → cumulus-nvim/init.lua
  - [ ] bootstrap.sh → cumulus-nvim/bootstrap.sh
  - [ ] scripts/ → cumulus-nvim/scripts/
- [ ] Copy cumulus-nvim/engine/ directory (keep for now)
- [ ] Update .gitignore for combined structure
- [ ] Test that directory structure is correct
- [ ] Verify no files lost: `find cumulus-nvim/ | wc -l` matches original

#### Phase 3: Git History (Day 2-3)
- [ ] Create git subtree from cumulus.nvim preserving history
  - ```bash
    git subtree add --prefix=cumulus-nvim \
      https://github.com/petrolal/cumulus.nvim.git main
    ```
- [ ] Verify all commits from both repos present
- [ ] Test `git log cumulus-nvim/ | head -20` shows nvim history
- [ ] Test `git log -- README.md` shows dotfiles history
- [ ] Document how to view history for both parts

#### Phase 4: Migration Guide (Day 3)
- [ ] Write MIGRATION_GUIDE.md for existing users
  - [ ] Old cumulus.nvim repo is deprecated
  - [ ] How to migrate existing installations
  - [ ] Where to find things in new structure
  - [ ] Update git remotes
- [ ] Update main README.md for monorepo
- [ ] Add backward compatibility notes

#### Phase 5: Verification (Day 3)
- [ ] `git clone <repo> && cd cumulus-nvim && ls` shows full structure
- [ ] Git history intact: `git log cumulus-nvim/ | wc -l` > 100 commits
- [ ] No files missing vs original repos
- [ ] Create pull request and request review
- [ ] All PR checks pass (if CI configured)

#### Phase 6: Sign-Off
- [ ] Team reviews and approves PR
- [ ] Merge to feature branch
- [ ] Celebrate! 🎉 First story done

### Definition of Done
- ✅ cumulus.nvim merged into cumulus.dotfiles/cumulus-nvim/
- ✅ Git history preserved from both repos
- ✅ All files present and accounted for
- ✅ MIGRATION_GUIDE.md created
- ✅ README updated
- ✅ PR merged to feature branch

---

## STORY 1.2: Unified Build Configuration

**Owner:** Build/Infrastructure  
**Points:** 8  
**Days:** 4-5  
**Depends On:** Story 1.1 ✅

### Task Checklist

#### Phase 1: Analysis (Day 1)
- [ ] Review current engine/build.sbt structure
- [ ] Review cumulus-cli/build.sbt structure
- [ ] Review cumulus-nvim/ (has no build, just config)
- [ ] Identify common settings across both
- [ ] Identify platform-specific settings needed
- [ ] Document module dependencies

#### Phase 2: Root build.sbt Creation (Day 2)
- [ ] Create new build.sbt at monorepo root
- [ ] Define three modules:
  ```scala
  lazy val cumulusLsp = (project in file("cumulus-lsp"))
  lazy val cumulusCli = (project in file("cumulus-cli"))
  lazy val cumulusNvim = (project in file("cumulus-nvim"))
  lazy val root = (project in file("."))
    .aggregate(cumulusLsp, cumulusCli, cumulusNvim)
  ```
- [ ] Set common properties:
  - [ ] ThisBuild / scalaVersion := "3.5.2"
  - [ ] ThisBuild / organization := "io.github.petrolal"
  - [ ] Common library versions
  - [ ] Common compiler options
- [ ] Configure assembly defaults
- [ ] Configure NativeImagePlugin defaults
- [ ] Test compilation: `sbt compile`

#### Phase 3: Module-Specific Configs (Day 2-3)
- [ ] cumulus-lsp/build.sbt:
  - [ ] Enable NativeImagePlugin
  - [ ] Set mainClass := "cumulus.lsp.Main"
  - [ ] Configure nativeImageOptions
  - [ ] Keep existing dependencies
- [ ] cumulus-cli/build.sbt:
  - [ ] Enable AssemblyPlugin
  - [ ] Set mainClass := "cumulus.cli.CumulusCli"
  - [ ] Configure assembly settings
- [ ] cumulus-nvim/build.sbt:
  - [ ] Create minimal config (config only, no build)
  - [ ] Or omit entirely if not needed
- [ ] Move engine/ → cumulus-lsp/ (if not already done)

#### Phase 4: Cross-Platform Support (Day 3)
- [ ] Add OS detection logic
  ```scala
  val osName = scala.sys.props("os.name").toLowerCase
  val isLinux = osName.contains("linux")
  val isMac = osName.contains("mac")
  ```
- [ ] Add platform-specific nativeImageOptions
  - [ ] Linux: standard options
  - [ ] macOS: macOS-specific flags
  - [ ] Windows: Windows-specific flags (prepare for future)
- [ ] Test compilation on Linux: `sbt compile`
- [ ] Document platform support in build.sbt comments

#### Phase 5: Build Testing (Day 4)
- [ ] Test full build: `sbt compile`
- [ ] Test LSP build: `sbt cumulusLsp/compile`
- [ ] Test CLI build: `sbt cumulusCli/compile`
- [ ] Test assembly: `sbt cumulusLsp/assembly`
- [ ] Verify output JAR: `ls -lh cumulus-lsp/target/*.jar`
- [ ] Test native image: `sbt cumulusLsp/nativeImage` (if time permits)

#### Phase 6: Documentation (Day 4)
- [ ] Update build documentation
- [ ] Document module structure
- [ ] Document build commands for each module
- [ ] Add troubleshooting guide for common build issues

#### Phase 7: Sign-Off (Day 5)
- [ ] Create PR with unified build.sbt
- [ ] Request build/infrastructure review
- [ ] All tests pass
- [ ] Merge to feature branch

### Definition of Done
- ✅ Single root build.sbt with three modules
- ✅ cumulus-lsp builds correctly
- ✅ cumulus-cli builds correctly
- ✅ cumulus-nvim minimal config present
- ✅ Cross-platform support added
- ✅ All builds pass locally and in CI

---

## STORY 1.3: Shared Build Infrastructure

**Owner:** Build/Infrastructure  
**Points:** 5  
**Days:** 3-4  
**Depends On:** Story 1.2 ✅

### Task Checklist

#### Phase 1: Consolidate Plugins (Day 1)
- [ ] Review engine/project/plugins.sbt
- [ ] Review any cumulus-cli plugin requirements
- [ ] Create consolidated project/plugins.sbt
  - [ ] addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.5.0")
  - [ ] addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
  - [ ] addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.2")
  - [ ] addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")
- [ ] Test plugin loading: `sbt plugins`

#### Phase 2: Version Management (Day 1-2)
- [ ] Define common library versions in root build.sbt
  ```scala
  val catsVersion = "2.9.0"
  val scalatestVersion = "3.2.15"
  val upickleVersion = "3.1.0"
  val osLibVersion = "0.9.3"
  ```
- [ ] Use versions in all modules
- [ ] Add version override capability (if needed)
- [ ] Document version strategy in build.sbt comments

#### Phase 3: Assembly Configuration (Day 2)
- [ ] Create assembly settings template in root
  ```scala
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
  ```
- [ ] Apply to cumulus-lsp
- [ ] Apply to cumulus-cli
- [ ] Test assembly generation for both

#### Phase 4: Build Info Setup (Day 2-3)
- [ ] Configure BuildInfoPlugin for version tracking
- [ ] Generate build info with:
  - [ ] Project name
  - [ ] Version from git tags
  - [ ] Build timestamp
  - [ ] Git commit hash
- [ ] Test build info generation: `sbt compile` then check generated files

#### Phase 5: Testing & Verification (Day 3)
- [ ] Full build: `sbt compile test`
- [ ] LSP native image build: `sbt cumulusLsp/nativeImage`
- [ ] CLI assembly: `sbt cumulusCli/assembly`
- [ ] Verify all plugins load
- [ ] Verify all dependencies resolved

#### Phase 6: Sign-Off (Day 4)
- [ ] Create PR with shared infrastructure
- [ ] Code review and approval
- [ ] Merge to feature branch

### Definition of Done
- ✅ Unified project/plugins.sbt
- ✅ Common library versions defined
- ✅ Assembly settings configured
- ✅ Build info tracking enabled
- ✅ All builds pass

---

## STORY 1.4: Test Infrastructure Unification

**Owner:** QA/Testing  
**Points:** 5  
**Days:** 3-4  
**Depends On:** Story 1.2 ✅

### Task Checklist

#### Phase 1: Inventory Tests (Day 1)
- [ ] List all test files in cumulus-lsp:
  ```bash
  find cumulus-lsp/src/test -name "*.scala" | wc -l
  ```
- [ ] List all test files in cumulus-cli:
  ```bash
  find cumulus-cli/src/test -name "*.scala" | wc -l
  ```
- [ ] Document test frameworks used (munit, scalatest, etc)
- [ ] Document test coverage tools

#### Phase 2: Configure Multi-Module Testing (Day 1-2)
- [ ] Ensure test sources discovered: `sbt Test/sources`
- [ ] Run cumulus-lsp tests: `sbt cumulusLsp/test`
- [ ] Run cumulus-cli tests: `sbt cumulusCli/test`
- [ ] Run all tests: `sbt test`
- [ ] Fix any test discovery issues

#### Phase 3: Coverage Reporting (Day 2)
- [ ] Add coverage plugin to project/plugins.sbt
  ```scala
  addSbtPlugin("org.scoverage" % "sbt-scoverage" % "x.x.x")
  ```
- [ ] Configure coverage for both modules
- [ ] Generate coverage report: `sbt clean coverage test coverageReport`
- [ ] Verify reports generated in target/scala-*/scoverage-report/

#### Phase 4: Parallel Test Execution (Day 2-3)
- [ ] Configure parallel test execution
  ```scala
  Test / parallelExecution := true
  Test / fork := true
  ```
- [ ] Test parallel run: `sbt test`
- [ ] Measure time difference (should be faster)
- [ ] Verify no test conflicts in parallel mode

#### Phase 5: CI Integration (Day 3)
- [ ] Update CI configuration (.github/workflows or equivalent)
  - [ ] Run `sbt test` as test step
  - [ ] Collect coverage reports
  - [ ] Upload to coverage service (if configured)
- [ ] Test CI workflow locally (act or equivalent)
- [ ] Verify all tests pass in CI

#### Phase 6: Documentation (Day 3-4)
- [ ] Create TEST_GUIDE.md
  - [ ] How to run all tests
  - [ ] How to run specific module tests
  - [ ] How to run with coverage
  - [ ] How to view coverage reports

#### Phase 7: Sign-Off (Day 4)
- [ ] Create PR with test infrastructure
- [ ] All tests passing
- [ ] Coverage reports generated
- [ ] Merge to feature branch

### Definition of Done
- ✅ All cumulus-lsp tests pass
- ✅ All cumulus-cli tests pass
- ✅ Full `sbt test` works
- ✅ Coverage reporting enabled
- ✅ Parallel execution configured
- ✅ CI integration complete

---

## STORY 1.5: Documentation Structure Update

**Owner:** Documentation  
**Points:** 3  
**Days:** 2-3  
**Depends On:** Story 1.1 ✅

### Task Checklist

#### Phase 1: Create Core Documentation (Day 1)
- [ ] Create MONOREPO_STRUCTURE.md
  ```
  - Directory layout explained
  - Module purposes
  - What goes where
  - Why structure is this way
  ```
- [ ] Create DEVELOPER_GUIDE.md
  ```
  - How to build locally
  - How to run tests
  - How to contribute
  - Development workflow
  ```
- [ ] Update main README.md
  ```
  - Single installation instruction
  - Link to new guides
  - Quick start
  ```

#### Phase 2: Migration Documentation (Day 1-2)
- [ ] Create MIGRATION_GUIDE.md (if not already done in 1.1)
  ```
  - Old cumulus.nvim repo is deprecated
  - How to migrate
  - What changed and where
  - Update git remotes
  ```
- [ ] Add deprecation notice to old cumulus.nvim repo (if you have access)

#### Phase 3: Architecture Documentation (Day 2)
- [ ] Create ARCHITECTURE.md
  ```
  - Monorepo structure diagram
  - Module relationships
  - Data flow
  - Dependency graph
  ```
- [ ] Create ASCII architecture diagram
- [ ] Link to EPICS_AND_STORIES.md for context

#### Phase 4: Path References Update (Day 2-3)
- [ ] Search all docs for old paths:
  ```bash
  grep -r "cumulus.nvim/engine" docs/
  grep -r "nvim/engine" docs/
  grep -r "\.nvim.*repo" docs/
  ```
- [ ] Update all references to new paths
  ```
  cumulus.nvim/engine → cumulus-lsp
  cumulus.nvim/build.sbt → root build.sbt
  etc.
  ```
- [ ] Verify all links work
- [ ] Update table of contents in README

#### Phase 5: Sign-Off (Day 3)
- [ ] Create PR with updated documentation
- [ ] Review for clarity
- [ ] All links work (test locally)
- [ ] Merge to feature branch

### Definition of Done
- ✅ MONOREPO_STRUCTURE.md created
- ✅ DEVELOPER_GUIDE.md created
- ✅ MIGRATION_GUIDE.md created
- ✅ ARCHITECTURE.md created
- ✅ README.md updated
- ✅ All paths updated
- ✅ All links work

---

## Sprint 1 End-of-Sprint Checklist

### Code Review & Merge
- [ ] All 5 stories have approved PRs
- [ ] All CI checks pass
- [ ] Code review checklist complete for each story
- [ ] No conflicts with other branches
- [ ] All PRs merged to feature branch

### Testing
- [ ] Full build works: `sbt compile`
- [ ] All tests pass: `sbt test`
- [ ] Git history intact and queryable
- [ ] Directory structure verified

### Documentation
- [ ] All guides created
- [ ] All paths updated
- [ ] README reflects monorepo
- [ ] Migration guide available

### Retrospective
- [ ] Team standup on achievements
- [ ] Blockers discussed and resolved
- [ ] Velocity recorded (25 points completed)
- [ ] Lessons learned documented
- [ ] Next sprint dependencies reviewed

### Sign-Off
- [ ] Sprint Lead approves Sprint 1 complete
- [ ] Create pull request: `feature/monorepo-consolidation` → `feature/sprint-2`
- [ ] Schedule Sprint 2 kickoff

---

# SPRINT 2: UNIFIED INSTALLER & BOOTSTRAP
**Duration:** 2 weeks  
**Goal:** Create one-shot installation with bootstrap script  
**Definition of Done:** bash bootstrap.sh installs full IDE on Linux

## Pre-Sprint 2 (Day 1)

### Kickoff Meeting
- [ ] Review Sprint 1 retrospective
- [ ] Review 6 stories for Sprint 2
- [ ] Explain one-shot installer vision
- [ ] Clarify blockers and dependencies
- [ ] Assign story owners

### Sprint 2 Setup
- [ ] Create feature branch: `feature/sprint-2-bootstrap`
- [ ] Create SPRINT_2_DETAILS.md with daily breakdown
- [ ] Update progress tracking spreadsheet

### Story Dependencies
- [ ] Story 2.1: Enhanced Bootstrap Script Architecture (foundation)
- [ ] Stories 2.2-2.6: Depend on 2.1 framework

---

## [Continue with Sprint 2-6 detailed checklists...]

**[Stories 2.2 through 5.3 follow same pattern with Task Checklist format]**

---

# Sprint Coordination Framework

## Daily Standup (15 min)
- [ ] What did I complete yesterday?
- [ ] What am I working on today?
- [ ] What blockers do I have?
- [ ] Any help needed?

## Weekly Sync (30 min)
- [ ] Review weekly progress toward sprint goal
- [ ] Discuss any emerging blockers
- [ ] Adjust sprint scope if needed (only with approval)
- [ ] Celebrate weekly wins

## Code Review Process
- [ ] Create PR with clear description
- [ ] Reference story (e.g., "Fixes Story 1.1")
- [ ] Include before/after (if applicable)
- [ ] Link to CI/test results
- [ ] Request review from Epic owner
- [ ] Address feedback
- [ ] Merge when approved

## Testing Checklist (per story)
- [ ] Local build passes
- [ ] Local tests pass
- [ ] CI passes
- [ ] Feature tested manually
- [ ] Edge cases tested
- [ ] Documentation updated

## Definition of Done Checklist
Before marking story complete:
- [ ] Code merged to feature branch
- [ ] Tests passing
- [ ] Documentation updated
- [ ] Code reviewed and approved
- [ ] No known blockers
- [ ] Sprint Lead sign-off

---

# Progress Tracking

## Velocity Tracking
```
Sprint 1: 25 points (5 stories)
Sprint 2: 35 points (6 stories)
Sprint 3: 25 points (5 stories)
Sprint 4: 40 points (6 stories)
Sprint 5: 35 points (6 stories)
Sprint 6: 30 points (3 stories + buffer)
────────────────────────────
Total:   190 points
```

## Risk & Mitigation Log
| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Build time increases | Medium | Medium | Pre-built binaries, caching |
| Complex bootstrap breaks | Low | High | Extensive testing, error handling |
| Git history loss | Low | High | Test merge thoroughly |
| Platform differences (Linux/macOS) | Medium | Medium | Test matrix in CI |

## Escalation Path
- **Story blocker** → Talk to Epic owner
- **Epic blocker** → Talk to Sprint Lead
- **Cross-epic blocker** → Schedule urgent sync with all leads
- **External blocker** → Document and escalate to project leadership

---

# Sign-Off

## Sprint Completion Criteria
- ✅ All planned stories completed
- ✅ All acceptance criteria met
- ✅ All tests passing
- ✅ Documentation updated
- ✅ No critical blockers outstanding

## Release Readiness (After Sprint 6)
- ✅ Full monorepo working
- ✅ One-shot installer functional
- ✅ All IDE features verified
- ✅ Multi-platform prepared
- ✅ Release notes ready
- ✅ Migration guide published

---

**Document Status:** Ready to Use  
**Next Action:** Print/share with team, begin Sprint 1  
**Created:** 2026-08-24  
**Sprint Master:** [Assign]
