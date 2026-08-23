package cumulus.build

import munit.FunSuite
import os.Path

class CommandAssemblerTest extends FunSuite:

  val tmpDir = os.temp.dir(prefix = "cumulus-cmd-test-")

  override def afterAll(): Unit =
    try os.remove.all(tmpDir) catch case _: Exception => ()

  test("assemble Maven clean package without wrapper") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-DskipTests"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "mvn")
    assertEquals(cmd.args, Seq("clean", "package", "-DskipTests"))
    assertEquals(cmd.command, "mvn clean package -DskipTests")
    assertEquals(cmd.cwd, tmpDir.toString)
  }

  test("assemble Maven with wrapper in directory") {
    val mavenDir = tmpDir / "maven-project"
    os.makeDir.all(mavenDir)
    os.write(mavenDir / "mvnw", "#!/bin/sh\n")

    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-DskipTests"),
      dir = Some(mavenDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "./mvnw")
    assertEquals(cmd.args, Seq("clean", "package", "-DskipTests"))
    assertEquals(cmd.command, "./mvnw clean package -DskipTests")
  }

  test("assemble Maven test with target class and method") {
    val intent = CommandIntent(
      tool = "maven",
      action = "test",
      target = Some("FooTest#testBar"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.args, Seq("test", "-Dtest=FooTest#testBar"))
    assertEquals(cmd.command, "mvn test -Dtest=FooTest#testBar")
  }

  test("assemble Maven submodule target") {
    val intent = CommandIntent(
      tool = "maven",
      action = "clean",
      target = Some(":core"),
      flags = Seq("-q"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.args, Seq("clean", "-pl", ":core", "-q"))
    assertEquals(cmd.command, "mvn clean -pl :core -q")
  }

  test("assemble Gradle build with wrapper") {
    val gradleDir = tmpDir / "gradle-project"
    os.makeDir.all(gradleDir)
    os.write(gradleDir / "gradlew", "#!/bin/sh\n")

    val intent = CommandIntent(
      tool = "gradle",
      action = "build",
      flags = Seq("--offline"),
      dir = Some(gradleDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "./gradlew")
    assertEquals(cmd.args, Seq("build", "--offline"))
    assertEquals(cmd.command, "./gradlew build --offline")
  }

  test("assemble Gradle test with target") {
    val intent = CommandIntent(
      tool = "gradle",
      action = "test",
      target = Some("FooTest.testBar"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "gradle")
    assertEquals(cmd.args, Seq("test", "--tests", "FooTest.testBar"))
    assertEquals(cmd.command, "gradle test --tests FooTest.testBar")
  }

  test("assemble SBT compile and test") {
    val intent = CommandIntent(
      tool = "sbt",
      action = "test",
      target = Some("*FooTest -- -t testBar"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "sbt")
    assertEquals(cmd.args, Seq("testOnly *FooTest -- -t testBar"))
  }

  test("assemble Terraform plan with target directory") {
    val tfDir = tmpDir / "tf-project"
    val devDir = tfDir / "infra" / "dev"
    os.makeDir.all(devDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("infra/dev"),
      flags = Seq("-var-file=dev.tfvars"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "terraform")
    assertEquals(cmd.args, Seq("plan", "-var-file=dev.tfvars"))
    assertEquals(cmd.command, "terraform plan -var-file=dev.tfvars")
    assertEquals(cmd.cwd, devDir.toString)
  }

  test("assemble OpenTofu format with target file") {
    val intent = CommandIntent(
      tool = "tofu",
      action = "fmt",
      target = Some("main.tf"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "tofu")
    assertEquals(cmd.args, Seq("fmt", "main.tf"))
    assertEquals(cmd.command, "tofu fmt main.tf")
    assertEquals(cmd.cwd, tmpDir.toString)
  }

  test("assemble SAM local invoke and start-api") {
    val intentInvoke = CommandIntent(
      tool = "sam",
      action = "local-invoke",
      target = Some("MyLambdaFunction"),
      flags = Seq("--env-vars", "env.json"),
      dir = Some(tmpDir.toString)
    )
    val resInvoke = CommandAssembler.assemble(intentInvoke)
    assert(resInvoke.isRight)
    assertEquals(resInvoke.toOption.get.command, "sam local invoke MyLambdaFunction --env-vars env.json")

    val intentApi = CommandIntent(
      tool = "sam",
      action = "local-start-api",
      flags = Seq("--port", "3000"),
      dir = Some(tmpDir.toString)
    )
    val resApi = CommandAssembler.assemble(intentApi)
    assert(resApi.isRight)
    assertEquals(resApi.toOption.get.command, "sam local start-api --port 3000")
  }

  test("assemble CloudFormation validate") {
    val intent = CommandIntent(
      tool = "cloudformation",
      action = "validate",
      target = Some("template.yaml"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "aws")
    assertEquals(cmd.command, "aws cloudformation validate-template --template-body file://template.yaml")
  }

  test("assemble Ansible playbook run with flags and inventory") {
    val intent = CommandIntent(
      tool = "ansible",
      action = "run",
      target = Some("site.yml"),
      flags = Seq("--check", "-i", "inventory.ini"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "ansible-playbook")
    assertEquals(cmd.args, Seq("-i", "inventory.ini", "site.yml", "--check"))
    assertEquals(cmd.command, "ansible-playbook -i inventory.ini site.yml --check")
  }

  test("assemble Ansible syntax-check and lint") {
    val intentSyntax = CommandIntent(
      tool = "ansible",
      action = "syntax-check",
      target = Some("playbook.yml"),
      dir = Some(tmpDir.toString)
    )
    val resSyntax = CommandAssembler.assemble(intentSyntax)
    assert(resSyntax.isRight)
    assertEquals(resSyntax.toOption.get.command, "ansible-playbook --syntax-check playbook.yml")

    val intentLint = CommandIntent(
      tool = "ansible",
      action = "lint",
      target = Some("playbook.yml"),
      dir = Some(tmpDir.toString)
    )
    val resLint = CommandAssembler.assemble(intentLint)
    assert(resLint.isRight)
    assertEquals(resLint.toOption.get.executable, "ansible-lint")
    assertEquals(resLint.toOption.get.command, "ansible-lint playbook.yml")
  }

  test("assemble Docker build with target Dockerfile and flags") {
    val intent = CommandIntent(
      tool = "docker",
      action = "build",
      target = Some("Dockerfile"),
      flags = Seq("-t", "app:v1"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.executable, "docker")
    assertEquals(cmd.args, Seq("build", "-t", "app:v1", "-f", "Dockerfile", "."))
    assertEquals(cmd.command, "docker build -t app:v1 -f Dockerfile .")
  }

  test("assemble Helm lint and template") {
    val intentLint = CommandIntent(
      tool = "helm",
      action = "lint",
      dir = Some(tmpDir.toString)
    )
    val resLint = CommandAssembler.assemble(intentLint)
    assert(resLint.isRight)
    assertEquals(resLint.toOption.get.command, "helm lint .")

    val intentTemplate = CommandIntent(
      tool = "helm",
      action = "template",
      target = Some("my-chart"),
      flags = Seq("-f", "values.yaml"),
      dir = Some(tmpDir.toString)
    )
    val resTemplate = CommandAssembler.assemble(intentTemplate)
    assert(resTemplate.isRight)
    assertEquals(resTemplate.toOption.get.command, "helm template my-chart -f values.yaml")
  }

  test("environment variables and escaping spaces") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      flags = Seq("-var-file=dev env.tfvars"),
      env = Map("TF_VAR_region" -> "us-east-1"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.command, """terraform plan -var-file="dev env.tfvars"""")
    assertEquals(cmd.env, Map("TF_VAR_region" -> "us-east-1"))
  }

  test("unsupported tool returns error") {
    val intent = CommandIntent(
      tool = "unknown_tool",
      action = "run",
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Unsupported tool"))
  }

  test("shell injection vector: double quotes with metacharacters") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      // The input string contains actual quote characters that need escaping
      flags = Seq("\"; rm -rf /; echo \""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // The dangerous input should be escaped and quoted to prevent injection
    // Input: "; rm -rf /; echo "
    // Escape quotes: \"; rm -rf /; echo \"
    // Wrap in quotes: "\"; rm -rf /; echo \""
    assertEquals(cmd.command, "terraform plan \"\\\"; rm -rf /; echo \\\"\"")
  }

  test("shell injection vector: complex attack string") {
    // Note: This test shows that $(malicious_command) without spaces won't be wrapped.
    // This is a limitation of the spec which only escapes backslashes and quotes.
    // In practice, $() is still interpreted inside double quotes, so this is unsafe.
    // However, we follow the spec which doesn't mandate escaping for $ and ().
    val intent = CommandIntent(
      tool = "docker",
      action = "build",
      flags = Seq("$(malicious_command)"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Per spec: no spaces, no backslashes, no quotes -> no escaping needed
    assertEquals(cmd.command, "docker build $(malicious_command) .")
  }

  test("argument with backslashes only") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("path\\to\\file"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Backslashes should be escaped
    assertEquals(cmd.command, """mvn clean package "path\\to\\file"""")
  }

  test("argument with mixed backslashes and quotes") {
    val intent = CommandIntent(
      tool = "sbt",
      action = "build",
      flags = Seq("""test"; \evil\path"""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Both backslashes and quotes should be escaped
    // Backslashes: \ → \\, Quotes: " → \", Wrapped: "result"
    assertEquals(cmd.command, """sbt compile "test\"; \\evil\\path"""")
  }

  test("-key=\"value\" pattern with special characters in value") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("""-Dconf="my value with; bad chars\here\""""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Value should be escaped even with -key= prefix
    assertEquals(cmd.command, """mvn clean package -Dconf="my value with; bad chars\\here\\"""")
  }

  test("-key=\"value\" pattern without special characters remains unchanged") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-Dconf=simple_value"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Simple values without spaces or special chars should not be modified
    assertEquals(cmd.command, "mvn clean package -Dconf=simple_value")
  }

  test("empty string argument is handled safely") {
    val intent = CommandIntent(
      tool = "docker",
      action = "run",
      flags = Seq(""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Empty strings should not cause issues
    assert(cmd.command.contains("docker run"))
  }

  test("argument with only quotes") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      flags = Seq("\""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Pure quote should be escaped and wrapped: " -> \" -> "\""
    assertEquals(cmd.command, "terraform plan \"\\\"\"")
  }

  test("normal argument without special characters remains unchanged") {
    val intent = CommandIntent(
      tool = "gradle",
      action = "build",
      flags = Seq("--offline", "-q"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Clean arguments should remain unchanged
    assertEquals(cmd.command, "gradle build --offline -q")
  }

  test("already quoted argument gets internal quotes escaped") {
    val intent = CommandIntent(
      tool = "docker",
      action = "run",
      flags = Seq("""-e VAR="already quoted""""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Argument with -key="value" pattern: value is unquoted before escaping, then re-wrapped
    // Input: -e VAR="already quoted"
    // Key: -e VAR, Value: "already quoted" → unquote to: already quoted → escape → "already quoted"
    // Result: -e VAR="already quoted"
    assertEquals(cmd.command, """docker run -e VAR="already quoted"""")
  }

  test("shell injection: command substitution with backticks") {
    val intent = CommandIntent(
      tool = "ansible",
      action = "run",
      flags = Seq("`whoami`"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Backticks don't require escaping per spec (only backslashes and quotes)
    // and have no spaces, so they pass through unchanged
    assertEquals(cmd.command, "ansible-playbook `whoami`")
  }

  test("shell injection: pipe and redirection") {
    val intent = CommandIntent(
      tool = "helm",
      action = "lint",
      flags = Seq("| cat /etc/passwd"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Pipe with space should be quoted to prevent shell interpretation
    assertEquals(cmd.command, """helm lint . "| cat /etc/passwd"""")
  }

  test("multiple arguments with mixed special characters") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-Dfile=my file.txt", "-Dpath=C:\\Users\\Admin\\file"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Each should have values properly escaped and quoted
    // -Dfile=my file.txt has space in value -> -Dfile="my file.txt"
    // -Dpath=C:\Users\Admin\file has backslashes in value -> needs escaping to C:\\Users\\Admin\\file
    // Verify the full command
    assert(cmd.command.startsWith("mvn clean package"))
    assert(cmd.command.contains("-Dfile=\"my file.txt\""))
    assert(cmd.command.contains("-Dpath=\"C") && cmd.command.contains("Users\\\\Admin\\\\file\""))
  }

  test("backslash escaping order: backslash must come before quote escaping") {
    // This test verifies that the escaping order (backslash first, then quotes) is correct
    // Input: a\b"c should become: "a\\b\"c"
    val intent = CommandIntent(
      tool = "docker",
      action = "build",
      flags = Seq("""a\b"c"""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // If backslash escaping happens first: \ → \\, then " → \"
    // Result should be: "a\\b\"c"
    assertEquals(cmd.command, """docker build "a\\b\"c" .""")
  }

  // Path traversal security tests for Terraform
  test("Terraform: reject path traversal attempt ../../../etc/passwd") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("../../../etc/passwd"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Path traversal detected"))
  }

  test("Terraform: reject deeply nested traversal ../../../../../../tmp/file") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("../../../../../../tmp/file"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Path traversal detected"))
  }

  test("Terraform: reject absolute path /etc/passwd") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("/etc/passwd"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Path traversal detected"))
  }

  test("Terraform: accept valid in-project relative path") {
    val tfDir = tmpDir / "tf-valid"
    val infraDir = tfDir / "infra" / "dev"
    os.makeDir.all(infraDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("infra/dev"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.cwd, infraDir.toString)
    assertEquals(cmd.executable, "terraform")
    assertEquals(cmd.args, Seq("plan"))
  }

  test("Terraform: accept valid deep in-project path") {
    val tfDir = tmpDir / "tf-deep"
    val deepDir = tfDir / "subdir" / "deep" / "terraform"
    os.makeDir.all(deepDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "init",
      target = Some("subdir/deep/terraform"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.cwd, deepDir.toString)
  }

  test("Terraform: reject symlink pointing outside project") {
    val tfDir = tmpDir / "tf-symlink-out"
    os.makeDir.all(tfDir)

    // Create external target outside project
    val externalDir = tmpDir / "external"
    os.makeDir.all(externalDir)

    // Create symlink inside project pointing outside
    val symlinkPath = tfDir / "external-link"
    try
      os.symlink(symlinkPath, externalDir)

      val intent = CommandIntent(
        tool = "terraform",
        action = "plan",
        target = Some("external-link"),
        dir = Some(tfDir.toString)
      )
      val result = CommandAssembler.assemble(intent)
      assert(result.isLeft)
      assert(result.left.toOption.get.contains("Path traversal detected"))
    catch
      case _: Exception =>
        // Skip test if symlink creation fails (e.g., on Windows)
        ()
  }

  test("Terraform: accept symlink pointing inside project") {
    val tfDir = tmpDir / "tf-symlink-in"
    val targetDir = tfDir / "actual" / "target"
    os.makeDir.all(targetDir)

    val symlinkPath = tfDir / "link-to-target"
    try
      os.symlink(symlinkPath, targetDir)

      val intent = CommandIntent(
        tool = "terraform",
        action = "plan",
        target = Some("link-to-target"),
        dir = Some(tfDir.toString)
      )
      val result = CommandAssembler.assemble(intent)
      assert(result.isRight)
      val cmd = result.toOption.get
      // Should resolve to the actual target directory
      assertEquals(cmd.cwd, targetDir.toString)
    catch
      case _: Exception =>
        // Skip test if symlink creation fails (e.g., on Windows)
        ()
  }

  test("Terraform: terraform file with traversal sequences in name is allowed") {
    val tfDir = tmpDir / "tf-files"
    os.makeDir.all(tfDir)

    // Even if filename contains traversal patterns, terraform files are passed as-is
    val intent = CommandIntent(
      tool = "terraform",
      action = "fmt",
      target = Some("../../../main.tf"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    // Terraform files (.tf) are passed directly without path validation
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.cwd, tfDir.toString)
    assert(cmd.args.contains("../../../main.tf"))
  }

  test("Terraform: empty target works as before") {
    val tfDir = tmpDir / "tf-empty"
    os.makeDir.all(tfDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = None,
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.cwd, tfDir.toString)
  }

  test("Terraform: path with ./ prefix is normalized and validated") {
    val tfDir = tmpDir / "tf-dotslash"
    val infraDir = tfDir / "infra"
    os.makeDir.all(infraDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("./infra"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.cwd, infraDir.toString)
  }

  test("Terraform: reject ../../sensitive-file traversal") {
    val tfDir = tmpDir / "tf-sensitive"
    os.makeDir.all(tfDir)

    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("../../sensitive-file"),
      dir = Some(tfDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Path traversal detected"))
  }

  test("OpenTofu (tofu): reject path traversal") {
    val intent = CommandIntent(
      tool = "tofu",
      action = "plan",
      target = Some("../../../etc/shadow"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isLeft)
    assert(result.left.toOption.get.contains("Path traversal detected"))
  }

  test("Terraform: tofu file extensions bypass traversal check") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "fmt",
      target = Some("../../../config.tofu"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    // .tofu files are passed directly without validation
    assert(result.isRight)
    val cmd = result.toOption.get
    assert(cmd.args.contains("../../../config.tofu"))
  }

  test("Terraform: tfvars file extensions bypass traversal check") {
    val intent = CommandIntent(
      tool = "terraform",
      action = "plan",
      target = Some("../../../dev.tfvars"),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    // .tfvars files are passed directly without validation
    assert(result.isRight)
    val cmd = result.toOption.get
    assert(cmd.args.contains("../../../dev.tfvars"))
  }

  test("Maven: -key=\" (single quote value) is properly escaped") {
    val intent = CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-Dconf=\""),
      dir = Some(tmpDir.toString)
    )
    val result = CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    // Single quote value should be escaped and preserved, not converted to empty string
    // Input: -Dconf=" → should become: -Dconf="\"" (escaped literal quote)
    assertEquals(cmd.command, "mvn clean package -Dconf=\"\\\"\"")
  }
