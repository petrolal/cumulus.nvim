stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
  - step-03-create-stories
  - step-04-final-validation
inputDocuments:
  - /home/petrolal/cumulus.nvim/_bmad-output/planning-artifacts/prds/prd-cumulus.nvim-2026-08-25/prd.md
  - /home/petrolal/cumulus.nvim/_bmad-output/planning-artifacts/architecture/architecture-cumulus.nvim-2026-08-25/ARCHITECTURE-SPINE.md
---

# cumulus.nvim - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for cumulus.nvim, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: [F-01] Java & Kotlin Support - Full LSP-backed intelligence via nvim-jdtls and kotlin-language-server
FR2: [F-02] Spring Boot Integration - Auto-discovery of REST endpoints and bean visualization via spring-boot.nvim
FR3: [F-03] Neovim-Native Project Generator - Dedicated Telescope/UI layer to initialize new Maven and Gradle projects from scratch
FR4: [F-04] Build System Integration - Intelligent detection and handling of Maven and Gradle lifecycle phases via keybinds
FR5: [F-05] Testing Suite - JUnit output parsing, nearest-test execution, and JaCoCo coverage overlays
FR6: [F-06] Refactoring - Project-wide safe renaming, moving, and extraction powered by native LSPs
FR7: [F-07] DAP Integration - Advanced debugging setup with nvim-dap (conditional breakpoints, hotswap)
FR8: [F-08] Continuous Profiling - Integration with async-profiler to generate and view CPU/Memory flamegraphs
FR9: [F-09] Infrastructure as Code - Validation for Kubernetes manifests and Helm charts via yamlls and helm_ls
FR10: [F-10] Database Tooling - Embedded database exploration and Flyway script validation using vim-dadbod
FR11: [F-11] Thematic Cloud Workspaces - Dynamic multi-cloud theme switching (AWS, Azure, GCP, OCI)

### NonFunctional Requirements

NFR1: [NFR-01] Performance & Async UI - Editor UI must remain completely unblocked; all LSP/background operations strictly async
NFR2: [NFR-02] Resource Limits - Hard memory limits enforced on heavy LSPs (JDTLS heap capped at 2GB) to prevent OOM
NFR3: [NFR-03] Hardware Profile - Assumes a modern development machine with 32GB+ RAM
NFR4: [NFR-04] Network Profile - Assumes standard internet access for downloading dependencies
NFR5: [NFR-05] Installation - Must support a 100% headless automated installation and validation script

### Additional Requirements

- [AD-01] Event-Driven UI Paradigm: Core logic and external executors must emit Neovim autocommands rather than directly calling UI render functions.
- [AD-02] Decentralized Plugin Orchestration: Plugin configurations must be isolated by filetype or specific command via Lazy.nvim.
- [AD-03] Headless External Tooling: Heavy external commands must run headlessly via vim.system and pipe to native UI.
- [AD-04] Stateless Project Context: Cumulus maintains zero internal cache; context is queried on the fly from disk or LSP.

### UX Design Requirements

N/A

### FR Coverage Map

FR1: Epic 1 - Java & Kotlin Support
FR2: Epic 3 - Spring Boot Integration
FR3: Epic 1 - Neovim-Native Project Generator
FR4: Epic 1 - Build System Integration
FR5: Epic 2 - Testing Suite
FR6: Epic 1 - Refactoring
FR7: Epic 2 - DAP Integration
FR8: Epic 2 - Continuous Profiling
FR9: Epic 3 - Infrastructure as Code
FR10: Epic 3 - Database Tooling
FR11: Epic 3 - Thematic Cloud Workspaces

## Epic List

### Epic 1: Core IDE Foundation (Code, Build, Refactor)
Goal: You can bootstrap a new Maven/Gradle project from scratch, write Java/Kotlin code with zero latency, execute builds, and perform project-wide refactoring natively.
**FRs covered:** FR1, FR3, FR4, FR6

### Epic 2: Quality Assurance (Test, Debug, Profile)
Goal: You can run specific JUnit tests from the gutter, debug running JVMs with hotswap capabilities, and continuously profile CPU/memory directly in the editor.
**FRs covered:** FR5, FR7, FR8

### Epic 3: Cloud Native & Enterprise Tooling
Goal: You can visualize Spring Boot beans/endpoints, query local databases, validate Kubernetes/Helm manifests, and dynamically swap multi-cloud themes.
**FRs covered:** FR2, FR9, FR10, FR11

## Epic 1: Core IDE Foundation (Code, Build, Refactor)

Goal: You can bootstrap a new Maven/Gradle project from scratch, write Java/Kotlin code with zero latency, execute builds, and perform project-wide refactoring natively.

### Story 1.1: Neovim-Native Project Generator

As a backend developer,
I want a dedicated UI picker to initialize new Maven and Gradle projects from scratch,
So that I can quickly scaffold Spring Boot applications (e.g., via Spring Initializr) without leaving Neovim.

**Acceptance Criteria:**

**Given** the user is in an empty workspace
**When** they execute the project generation command
**Then** a Telescope UI picker opens allowing selection of archetype, language, and build system
**And** upon selection, the project structure is downloaded and scaffolded asynchronously without blocking the UI

### Story 1.2: Java & Kotlin Language Server Integration

As a backend developer,
I want full LSP-backed intelligence for Java and Kotlin using nvim-jdtls and kotlin-language-server,
So that I have enterprise-grade autocompletion and navigation.

**Acceptance Criteria:**

**Given** a valid Maven or Gradle Java/Kotlin project
**When** a .java or .kt file is opened
**Then** the respective language server attaches strictly for that buffer
**And** auto-completion, go-to-definition, and signature help respond in under 200ms
**And** the JDTLS heap memory is strictly capped to prevent OOM crashes

### Story 1.3: Build System Lifecycle Execution

As a backend developer,
I want intelligent keybinds to trigger Maven and Gradle lifecycle phases,
So that I can compile and build the project natively.

**Acceptance Criteria:**

**Given** a Maven or Gradle project is open
**When** the user triggers the build keymap
**Then** Cumulus automatically detects the build system and executes the build command headlessly (vim.system)
**And** the build output is piped asynchronously to the Quickfix list or a native notification UI without spawning raw terminal splits

### Story 1.4: Workspace-Wide Refactoring

As a backend developer,
I want to safely rename, move, and extract code across the entire workspace,
So that I can confidently maintain large enterprise architectures.

**Acceptance Criteria:**

**Given** a project with multiple interconnected classes
**When** the user executes a rename command on a public class
**Then** the LSP orchestrates the rename across all files in the workspace
**And** the changes are applied cleanly without requiring a manual file-watcher restart

## Epic 2: Quality Assurance (Test, Debug, Profile)

Goal: You can run specific JUnit tests from the gutter, debug running JVMs with hotswap capabilities, and continuously profile CPU/memory directly in the editor.

### Story 2.1: Nearest-Test Execution & Coverage

As a backend developer,
I want to run individual JUnit tests directly from the editor and view coverage overlays,
So that I can quickly verify fixes without running the entire test suite.

**Acceptance Criteria:**

**Given** a Java or Kotlin test file is open
**When** the user triggers the test command on a specific `@Test` method
**Then** only that specific test is compiled and executed
**And** success/failure indicators appear natively in the gutter
**And** JaCoCo coverage data can be toggled to highlight covered/uncovered lines in the buffer

### Story 2.2: Advanced DAP Integration & Hotswap

As a backend developer,
I want to attach a debugger to a running JVM with hotswap capabilities,
So that I can inspect state and apply minor code changes without restarting the application.

**Acceptance Criteria:**

**Given** a Spring Boot or backend JVM application is running in debug mode
**When** the user triggers the DAP attach command
**Then** nvim-dap successfully connects to the JVM process
**And** conditional breakpoints halt execution, allowing local variable inspection
**And** compiling a modified class triggers a JVM hotswap natively through the debug protocol

### Story 2.3: Continuous Profiling Integration

As a backend developer,
I want to generate and view CPU/Memory flamegraphs directly from Neovim,
So that I can identify performance bottlenecks without switching to external profiling tools.

**Acceptance Criteria:**

**Given** a running JVM application
**When** the user triggers the profiling command
**Then** async-profiler attaches in the background without blocking the editor thread
**And** upon completion, an interactive flamegraph or call-tree is displayed inside Neovim (or opened in a native viewer automatically)

## Epic 3: Cloud Native & Enterprise Tooling

Goal: You can visualize Spring Boot beans/endpoints, query local databases, validate Kubernetes/Helm manifests, and dynamically swap multi-cloud themes.

### Story 3.1: Spring Boot Endpoint & Bean Intelligence

As a backend developer,
I want to automatically discover and navigate REST endpoints and injected beans,
So that I can understand complex Spring Boot application contexts instantly.

**Acceptance Criteria:**

**Given** a Spring Boot project is open
**When** the user triggers the Spring endpoint command
**Then** a Telescope picker displays all mapped REST endpoints and their HTTP verbs
**And** selecting an endpoint jumps the cursor directly to the controller method definition

### Story 3.2: Database Tooling & Flyway Validation

As a backend developer,
I want to explore embedded databases and run SQL queries natively,
So that I can validate Flyway migrations and test data without a separate DB client.

**Acceptance Criteria:**

**Given** a running local Postgres/embedded database
**When** the user opens the vim-dadbod-ui drawer
**Then** they can browse tables and schemas natively
**And** executing a SQL query inside a .sql buffer outputs the result directly to a Neovim split pane

### Story 3.3: Infrastructure as Code (K8s/Helm) Validation

As a backend developer,
I want intelligent validation for Kubernetes and Helm manifests,
So that I catch deployment syntax errors before pushing to the cluster.

**Acceptance Criteria:**

**Given** a Kubernetes .yaml or Helm chart template
**When** the file is opened
**Then** yamlls and helm_ls attach and provide inline diagnostics
**And** missing required fields or schema violations are highlighted natively in the buffer

### Story 3.4: Dynamic Multi-Cloud Theming

As a backend developer,
I want to dynamically switch the Neovim colorscheme to match different cloud provider themes,
So that I have clear visual context of my deployment target (AWS, Azure, GCP, OCI).

**Acceptance Criteria:**

**Given** the Cumulus editor is running
**When** the user executes a theme toggle command
**Then** the global Neovim colorscheme and statusline update immediately to reflect the chosen cloud identity
