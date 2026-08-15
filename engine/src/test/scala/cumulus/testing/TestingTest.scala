package cumulus.testing

import munit.FunSuite
import java.nio.file.{Files, Paths}
import os.Path

class TestingTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("testing-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    Files.createDirectories(filePath.getParent)
    Files.write(filePath, content.getBytes("UTF-8"))
    filePath.toString

  // ===== Test Context Detection (Java) =====

  test("TestContextDetector: @Test annotation in Java file at line 42") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class MyTest {
  private int x;
  public void setup() {}

  @Test
  public void testFoo() {
    // This is line 42 or nearby
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val result = TestContextDetector.detectTestContext(filePath, 7)
      assert(result.isRight)
      val context = result.toOption.get
      assert(context.class_name == "MyTest")
      assert(context.method_name == "testFoo")
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestContextDetector: @ParameterizedTest annotation") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class MyTest {
  @ParameterizedTest
  public void testWithParams(String param) {
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val result = TestContextDetector.detectTestContext(filePath, 4)
      assert(result.isRight)
      val context = result.toOption.get
      assert(context.class_name == "MyTest")
      assert(context.method_name == "testWithParams")
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestContextDetector: @RepeatedTest annotation") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class MyTest {
  @RepeatedTest(5)
  public void testRepeated() {
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val result = TestContextDetector.detectTestContext(filePath, 4)
      assert(result.isRight)
      val context = result.toOption.get
      assert(context.class_name == "MyTest")
      assert(context.method_name == "testRepeated")
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestContextDetector: Kotlin test file") {
    val dir = createTempDir()
    try
      val kotlinContent = """package com.example

class MyTest {
  @Test
  fun myTest() {
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.kt", kotlinContent)
      val result = TestContextDetector.detectTestContext(filePath, 4)
      assert(result.isRight)
      val context = result.toOption.get
      assert(context.class_name == "MyTest")
      assert(context.method_name == "myTest")
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestContextDetector: Multiple test methods, cursor on middle one") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class MyTest {
  @Test
  public void testFirst() {
  }

  @Test
  public void testSecond() {
  }

  @Test
  public void testThird() {
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      // Line 9 is testSecond
      val result = TestContextDetector.detectTestContext(filePath, 9)
      assert(result.isRight)
      val context = result.toOption.get
      assert(context.class_name == "MyTest")
      assert(context.method_name == "testSecond")
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestContextDetector: File not found") {
    val result = TestContextDetector.detectTestContext("/nonexistent/file.java", 1)
    assert(result.isLeft)
    assert(result.toOption.isEmpty)
  }

  test("TestContextDetector: Non-test file") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class MyClass {
  public void foo() {
  }
}""".stripMargin
      val filePath = createTempFile(dir, "MyClass.java", javaContent)
      val result = TestContextDetector.detectTestContext(filePath, 4)
      assert(result.isLeft)
      assert(result.toOption.isEmpty)
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  // ===== Test Output Parsing =====

  test("TestOutputParser: JUnit 5 XML format with PASSED status") {
    val xmlInput = """<?xml version="1.0" encoding="UTF-8"?>
<testcase classname="MyTest" name="testFoo" time="0.001"/>
""".stripMargin
    val result = TestOutputParser.parseTestOutput(xmlInput)
    assert(result.isRight)
    val results = result.toOption.get
    assert(results.length == 1)
    assert(results(0).class_name == "MyTest")
    assert(results(0).method_name == "testFoo")
    assert(results(0).status == "PASSED")
    assert(results(0).message.isEmpty)
  }

  test("TestOutputParser: JUnit 5 XML with FAILED status") {
    val xmlInput = """<?xml version="1.0" encoding="UTF-8"?>
<testcase classname="MyTest" name="testBar">
  <failure>Expected 5 but got 4</failure>
</testcase>
""".stripMargin
    val result = TestOutputParser.parseTestOutput(xmlInput)
    assert(result.isRight)
    val results = result.toOption.get
    assert(results.length == 1)
    assert(results(0).status == "FAILED")
    assert(results(0).message.isDefined)
    assert(results(0).message.get.contains("Expected"))
  }

  test("TestOutputParser: JUnit 5 XML with SKIPPED status") {
    val xmlInput = """<?xml version="1.0" encoding="UTF-8"?>
<testcase classname="MyTest" name="testSkipped">
  <skipped>Not ready yet</skipped>
</testcase>
""".stripMargin
    val result = TestOutputParser.parseTestOutput(xmlInput)
    assert(result.isRight)
    val results = result.toOption.get
    assert(results(0).status == "SKIPPED")
  }

  test("TestOutputParser: Gradle test output format") {
    val gradleOutput = """MyTest > testFoo PASSED
AnotherTest > testBar FAILED
ThirdTest > testSkipped SKIPPED
""".stripMargin
    val result = TestOutputParser.parseTestOutput(gradleOutput)
    assert(result.isRight)
    val results = result.toOption.get
    assert(results.length == 3)
    assert(results(0).status == "PASSED")
    assert(results(1).status == "FAILED")
    assert(results(2).status == "SKIPPED")
  }

  test("TestOutputParser: Empty input") {
    val result = TestOutputParser.parseTestOutput("")
    assert(result.isRight)
    val results = result.toOption.get
    assert(results.isEmpty)
  }

  test("TestOutputParser: Multiple JUnit test cases") {
    val xmlInput = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite>
  <testcase classname="MyTest" name="test1"/>
  <testcase classname="MyTest" name="test2">
    <failure>Error message</failure>
  </testcase>
  <testcase classname="OtherTest" name="test3">
    <skipped/>
  </testcase>
</testsuite>
""".stripMargin
    val result = TestOutputParser.parseTestOutput(xmlInput)
    assert(result.isRight)
    val results = result.toOption.get
    assert(results.length == 3)
    assert(results(0).status == "PASSED")
    assert(results(1).status == "FAILED")
    assert(results(2).status == "SKIPPED")
  }

  // ===== Test Command Assembly =====

  test("TestCommandAssembler: Maven single-module") {
    val dir = createTempDir()
    try
      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <groupId>com.example</groupId>
  <artifactId>my-project</artifactId>
  <version>1.0</version>
</project>
"""
      createTempFile(dir, "pom.xml", pomContent)
      val result = TestCommandAssembler.assembleTestCommand("maven", "FooTest", "testBar", dir)
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("mvn"))
      assert(command.command.contains("test"))
      assert(command.command.contains("FooTest#testBar"))
      assert(command.cwd.nonEmpty)
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Gradle single-module") {
    val dir = createTempDir()
    try
      val buildContent = """plugins {
  id 'java'
}
"""
      createTempFile(dir, "build.gradle", buildContent)
      val result = TestCommandAssembler.assembleTestCommand("gradle", "FooTest", "testBar", dir)
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("gradle"))
      assert(command.command.contains("test"))
      assert(command.command.contains("FooTest.testBar"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: SBT") {
    val dir = createTempDir()
    try
      val buildContent = """name := "my-project"
"""
      createTempFile(dir, "build.sbt", buildContent)
      val result = TestCommandAssembler.assembleTestCommand("sbt", "FooTest", "testBar", dir)
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("sbt"))
      assert(command.command.contains("testOnly"))
      assert(command.command.contains("FooTest"))
      assert(command.command.contains("testBar"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Maven with wrapper") {
    val dir = createTempDir()
    try
      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <groupId>com.example</groupId>
  <artifactId>my-project</artifactId>
  <version>1.0</version>
</project>
"""
      createTempFile(dir, "pom.xml", pomContent)
      // Create mvnw wrapper
      createTempFile(dir, "mvnw", "#!/bin/bash\nmvn \"$@\"")
      val result = TestCommandAssembler.assembleTestCommand("maven", "FooTest", "testBar", dir)
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("./mvnw"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Gradle with wrapper") {
    val dir = createTempDir()
    try
      val buildContent = """plugins {
  id 'java'
}
"""
      createTempFile(dir, "build.gradle", buildContent)
      // Create gradlew wrapper
      createTempFile(dir, "gradlew", "#!/bin/bash\ngradle \"$@\"")
      val result = TestCommandAssembler.assembleTestCommand("gradle", "FooTest", "testBar", dir)
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("./gradlew"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Invalid tool") {
    val dir = createTempDir()
    try
      createTempFile(dir, "pom.xml", "<project/>")
      val result = TestCommandAssembler.assembleTestCommand("unsupported", "FooTest", "testBar", dir)
      assert(result.isLeft)
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Directory not found") {
    val result = TestCommandAssembler.assembleTestCommand("maven", "FooTest", "testBar", "/nonexistent/dir")
    assert(result.isLeft)
  }

  test("TestCommandAssembler: Maven multi-module detection") {
    val dir = createTempDir()
    try
      // Create parent pom with modules
      val parentPom = """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <groupId>com.example</groupId>
  <artifactId>parent</artifactId>
  <packaging>pom</packaging>
  <modules>
    <module>submodule</module>
  </modules>
</project>
"""
      createTempFile(dir, "pom.xml", parentPom)

      // Create submodule pom
      val submodulePom = """<?xml version="1.0" encoding="UTF-8"?>
<project>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>parent</artifactId>
  </parent>
  <artifactId>submodule</artifactId>
</project>
"""
      createTempFile(dir, "submodule/pom.xml", submodulePom)

      val result = TestCommandAssembler.assembleTestCommand("maven", "FooTest", "testBar", s"$dir/submodule")
      assert(result.isRight)
      val command = result.toOption.get
      // For multi-module, should include -pl flag
      assert(command.command.contains("mvn") || command.command.contains("./mvnw"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }

  test("TestCommandAssembler: Gradle multi-module detection") {
    val dir = createTempDir()
    try
      val settingsContent = """rootProject.name = 'multi-module'
include ':submodule'
"""
      createTempFile(dir, "settings.gradle", settingsContent)
      createTempFile(dir, "build.gradle", "")
      createTempFile(dir, "submodule/build.gradle", "")

      val result = TestCommandAssembler.assembleTestCommand("gradle", "FooTest", "testBar", s"$dir/submodule")
      assert(result.isRight)
      val command = result.toOption.get
      assert(command.command.contains("gradle") || command.command.contains("./gradlew"))
      // Should generate a valid gradle test command
      assert(command.command.contains("test"))
      assert(command.command.contains("FooTest.testBar"))
    finally
      try
        os.remove.all(Path(dir))
      catch
        case e: Exception =>
          System.err.println(s"Warning: Failed to clean up test directory $dir: ${e.getMessage}")
  }
