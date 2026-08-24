# Coursier CLI Management

## Overview

The `cn` command is a **Scala-based CLI application** distributed via Coursier (like cumulus.dotfiles).

This provides:
- ✅ Single binary for all platforms (via JVM)
- ✅ Easy installation with `cs install`
- ✅ Automatic updates via Coursier
- ✅ Proper versioning and releases
- ✅ Follows cumulus.dotfiles architecture pattern

---

## Architecture

### Build Structure
```
engine/
├── build.sbt              # Multi-module build
├── cumulus-cli/           # CLI application module
│   ├── src/main/scala/    # CLI source code
│   └── coursier.json      # Coursier metadata
└── project/
    └── plugins.sbt        # sbt-assembly for JAR creation
```

### CLI Implementation
- **Language:** Scala 3.5.2
- **Main Class:** `cumulus.cli.CumulusCli`
- **Entry Points:**
  - `cn install` - Full installation
  - `cn update` - Update everything
  - `cn` - Launch nvim
  - `cn --help` - Show help

---

## Development

### Build the CLI JAR locally
```bash
cd engine
sbt "cli/assembly"
# Output: cumulus-cli/target/scala-3.5.2/cumulus-cli.jar
```

### Run locally
```bash
java -jar engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar --help
```

### Test commands
```bash
java -jar engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar --version
java -jar engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar
```

---

## Installation & Distribution

### Option 1: Bootstrap (Automatic)
User runs:
```bash
bash bootstrap.sh
cn install
```

**What happens:**
1. `bootstrap.sh` detects Coursier (if available)
2. Builds CLI JAR locally with `sbt cli/assembly`
3. Installs via `cs install` if Coursier available
4. Falls back to shell script launcher if not

### Option 2: Manual Installation
```bash
# Install Coursier first
curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz | gzip -d > ~/cs
chmod +x ~/cs
export PATH="~/cs:$PATH"

# Install cn from JAR
cs install \
  --force \
  --directory ~/.local/bin \
  file://path/to/cumulus-cli.jar \
  --main-class cumulus.cli.CumulusCli \
  --output cn
```

### Option 3: From GitHub Releases (Future)
Once published to releases:
```bash
cs install \
  --force \
  --directory ~/.local/bin \
  petrolal/cumulus-cli \
  --output cn
```

---

## Publishing to Maven Central

To make `cn` installable via `cs install petrolal/cumulus-cli`:

1. **Sign up for Sonatype Central**
   - Go to https://central.sonatype.com/
   - Create account and verify namespace

2. **Configure GPG keys** (already in build.sbt):
   ```scala
   usePgpKeyHex("C7A30CAF507B01B9F4BED6C3D79966B7698B8A7D")
   ```

3. **Set credentials** in `~/.sbt/1.0/sonatype.sbt`:
   ```scala
   credentials += Credentials(
     "Sonatype Central",
     "central.sonatype.com",
     "petrolal",
     "YOUR_TOKEN"
   )
   ```

4. **Publish**:
   ```bash
   cd engine
   sbt "cli/publishSigned"
   ```

5. **Create GitHub Release** with JAR artifact
   - Tag: `cli-v0.1.0`
   - Attach: `cumulus-cli.jar`
   - Workflow `.github/workflows/publish-cli.yml` automates this

---

## Coursier Launch Configuration

### Current Setup
- Manual JAR with main class specified
- Works with `cs install file:// --main-class`

### Future: Maven Central + cs.app
Once published to Maven Central, create `cs.app` file:
```
#!/bin/bash
# Launcher for cumulus-cli
cs launch "io.github.petrolal:cumulus-cli_3:0.1.0" -- "$@"
```

Then users can:
```bash
cs install petrolal/cumulus-cli
cn install
```

---

## CI/CD Pipeline

### GitHub Actions Workflow
**File:** `.github/workflows/publish-cli.yml`

Triggered on:
- Tag push: `cli-v*`
- Manual trigger via workflow_dispatch

**Steps:**
1. Checkout code
2. Setup JDK 21
3. Build CLI JAR: `sbt cli/assembly`
4. Create GitHub Release with JAR artifact

### To publish:
```bash
git tag cli-v0.1.1
git push origin cli-v0.1.1
# GitHub Actions automatically builds and releases
```

---

## Dependencies

### Runtime (in JAR)
- **os-lib** (0.9.3) - Scala OS operations
- Scala 3 standard library

### Build-time (sbt plugins)
- **sbt-assembly** - Create fat JAR
- **sbt-buildinfo** - Version metadata
- **sbt-ci-release** - Sonatype publishing

---

## Troubleshooting

### JAR won't build
```bash
cd engine
sbt clean
sbt "cli/assembly"
```

### Coursier install fails
```bash
# Verify JAR exists
ls -lh engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar

# Try manual install
cs install \
  --force \
  --directory ~/.local/bin \
  file://$(pwd)/engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar \
  --main-class cumulus.cli.CumulusCli \
  --output cn
```

### cn command not found
```bash
# Reload PATH
source ~/.bashrc
# Or restart shell
```

---

## Future Improvements

- [ ] Publish to Maven Central
- [ ] Create cs.app launcher configuration
- [ ] Add native image compilation for faster startup
- [ ] Support custom config locations
- [ ] Add `cn version` with upgrade checking
- [ ] Add `cn doctor` for health checks
- [ ] Support plugin installation via `cn`

---

## Related Files

- `bootstrap.sh` - Uses Coursier when available
- `engine/build.sbt` - Build configuration
- `engine/cumulus-cli/src/main/scala/cumulus/cli/CumulusCli.scala` - CLI source
- `.github/workflows/publish-cli.yml` - CI/CD pipeline
