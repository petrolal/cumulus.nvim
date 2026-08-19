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
