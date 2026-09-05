# IntelliJ IDEA Ultimate → tetravim.nvim parity map

What IDEA Ultimate supports **out of the box (no extra plugin)**, and how
tetravim.nvim covers it with native Neovim LSP / Tree-sitter / Mason tooling.

Legend: ✅ full LSP + Tree-sitter · 🟡 Tree-sitter / syntax only (no OSS server)
· ➖ not covered (no OSS equivalent) · 🔷 handled by an existing spec

## Languages

| IDEA bundles | tetravim | Spec file | Server / tool |
| --- | --- | --- | --- |
| Java | 🔷 | `lsp-java.lua`, `ftplugin/java.lua` | `jdtls` (+ java-debug, java-test) |
| Kotlin | 🔷 | `lsp-kotlin.lua` | `kotlin_language_server` |
| Groovy | 🔷 | `lsp-groovy.lua` | `groovyls` |
| Scala | 🔷 | `lsp-scala.lua` | `nvim-metals` |
| JavaScript / TypeScript / JSX / TSX | 🔷 | `lsp-typescript.lua` | `ts_ls` |
| Python | ✅ | `lsp-python.lua` | `basedpyright` + `ruff` |
| SQL | ✅ | `lsp-sql.lua` (+ `tools-dadbod.lua`) | `sqlls` + vim-dadbod |
| HTML / XHTML | 🔷 | `lsp-html.lua` | `html`, `superhtml` |
| CSS / SCSS / LESS / Sass | 🔷 | `lsp-css.lua` | `cssls` |
| JSON / JSON5 | 🔷 | `lsp-devops.lua` | `jsonls` (+ SchemaStore) |
| YAML (+ GitHub Actions / GitLab CI) | 🔷 | `lsp-yaml-ci.lua` | `yamlls`, `gh-actions-language-server` |
| XML / XSD / XSLT / DTD | 🔷 | `lsp-devops.lua` | `lemminx` |
| TOML | 🔷 | `lsp-toml.lua` | `taplo` |
| Markdown | ✅ | `lsp-markdown.lua`, `editor-markdown.lua` | `marksman` + render-markdown + preview |
| Shell script | 🔷 | `lsp-devops.lua` | `bashls`, `shellcheck`, `shfmt` |
| Properties | 🔷 | (jdtls / built-in ft) | — |
| RegExp | 🟡 | built-in | Neovim highlighting; no LSP exists |
| EditorConfig | 🟡 | built-in ft | no OSS LSP |
| Protocol Buffers / gRPC | 🔷 | `lsp-proto.lua`, `grpcui.lua` | `protols`, `buf`, `grpcurl` |
| Terraform / HCL | 🔷 | `cloud-terraform.lua` | `terraformls`, `tflint` |
| Dockerfile | 🔷 | `cloud-containers-k8s.lua` | `dockerfile-language-server`, `hadolint` |
| Kubernetes / Helm | 🔷 | `cloud-containers-k8s.lua` | `helm-ls` |
| Ansible | 🔷 | `cloud-cloudformation-ansible.lua` | `ansible-language-server`, `ansible-lint` |
| Lua (IDE config) | 🔷 | `lsp-lua.lua` | `lua_ls` |
| Deno | ✅ | `lsp-deno.lua` | `denols` (runtime-provided; deno.json-gated) |
| Prisma | ✅ | `lsp-prisma.lua` | `prismals` |

### Not bundled by IDEA Ultimate either (need a JetBrains plugin) — out of scope here

Go, Rust, PHP, Ruby, C/C++, Dart, GraphQL, Perl, Elixir, Clojure, Haskell.

## Frameworks

| IDEA bundles | tetravim | Notes |
| --- | --- | --- |
| Spring / Spring Boot / Data / Security / Batch | 🔷 | served by `jdtls` + `tetravim.util.spring*`; DAP via `ftplugin/java.lua` |
| Jakarta EE / Java EE, Hibernate/JPA | 🔷 | `jdtls` semantic model |
| Micronaut / Quarkus / Ktor / Helidon | 🔷 | `jdtls` / `kotlin_language_server` — no separate server |
| JUnit / TestNG (JVM test UI) | 🔷 | `tools-test.lua`, `neotest-java` |
| Node.js / React | 🔷 | `ts_ls` |
| Angular | ✅ | `lsp-web-frameworks.lua` → `angularls` |
| Vue | ✅ | `lsp-web-frameworks.lua` → `vue_ls` (Volar, hybrid off) |
| Svelte | ✅ | `lsp-web-frameworks.lua` → `svelte` |
| Astro | ✅ | `lsp-web-frameworks.lua` → `astro` |
| ESLint (always-on) | ✅ | `lsp-web-tooling.lua` → `eslint` (+ fix-all on save) |
| Tailwind CSS | ✅ | `lsp-web-tooling.lua` → `tailwindcss` |

## Template engines

| IDEA bundles | tetravim | Coverage |
| --- | --- | --- |
| Emmet (all HTML-ish buffers) | ✅ | `lsp-web-tooling.lua` → `emmet_language_server` |
| Handlebars / Mustache | 🟡 | built-in ft + Tree-sitter + emmet |
| Pug / Jade | 🟡 | `lang-templates.lua` → Tree-sitter `pug` + emmet |
| EJS / ERB | 🟡 | `.ejs`→`eruby` ft + Tree-sitter `embedded_template` + emmet |
| Jinja2 / Django | ✅ | `lang-templates.lua` → `htmldjango` ft + `djlint` (format + lint) + emmet |
| Thymeleaf | 🔷 | plain `.html`: `html` LSP + emmet |
| FreeMarker (`.ftl`) | 🟡 | `lang-templates.lua` registers `freemarker` ft (no OSS server/parser) |
| Velocity (`.vm`) | 🟡 | `lang-templates.lua` registers `velocity` ft (no OSS server/parser) |
| JSP / JSTL | ➖ | built-in `jsp` ft only — no OSS server or parser |

## Databases (query tooling)

vim-dadbod (`tools-dadbod.lua`) + `sqlls` cover connection management, schema
browsing, query execution and completion for the JDBC-style dialects IDEA's
Database plugin targets (PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, SQLite,
H2, …). Datasource auto-discovery from Spring `application.*` is
`tetravim.util.db`.

## DevOps / API

| IDEA bundles | tetravim |
| --- | --- |
| HTTP Client (`.http`) | 🔷 `tools-http.lua` (kulala) |
| OpenAPI / Swagger | 🔷 `tetravim.util.openapi` |
| Docker / Compose | 🔷 `cloud-containers-k8s.lua` |
| Kubernetes / Helm | 🔷 `cloud-containers-k8s.lua` |
| Terraform | 🔷 `cloud-terraform.lua` |
| Database tools | 🔷 `tools-dadbod.lua` + `lsp-sql.lua` |

## Install

All servers/tools are in `tools-mason.lua`'s `ensure_installed` and are fetched
by `mason-tool-installer` on first `VimEnter`. Force a sync with
`:MasonToolsInstall`. `deno` is the one exception — install the Deno runtime
separately and `lsp-deno.lua` picks it up automatically.

Verify with `:checkhealth tetravim` → *IDE-Parity Language Servers* section.
