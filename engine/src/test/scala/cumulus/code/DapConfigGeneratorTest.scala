package cumulus.code

import munit.FunSuite
import java.nio.file.{Files, Paths}
import os.Path

class DapConfigGeneratorTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("dap-test-").toString

  private def writeFile(dir: String, path: String, content: String): Unit =
    val filePath = Paths.get(dir, path)
    Files.createDirectories(filePath.getParent)
    Files.write(filePath, content.getBytes("UTF-8"))

  test("DapConfigGenerator: Spring Boot Maven project generates launch and attach configs") {
    val dir = createTempDir()
    try
      writeFile(dir, "pom.xml",
        """<?xml version="1.0"?>
          |<project>
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>boot-maven-demo</artifactId>
          |  <name>Boot Maven Demo</name>
          |</project>""".stripMargin)

      writeFile(dir, "src/main/java/com/example/BootApp.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class BootApp {
          |  public static void main(String[] args) {}
          |}""".stripMargin)

      val response = DapConfigGenerator.generateDapConfig(dir)
      assert(response.success)
      assert(response.data.isDefined)

      val result = response.data.get
      assertEquals(result.launch.`type`, "java")
      assertEquals(result.launch.name, "Spring Boot: Boot Maven Demo")
      assertEquals(result.launch.request, "launch")
      assertEquals(result.launch.mainClass, Some("com.example.BootApp"))
      assertEquals(result.launch.projectName, Some("Boot Maven Demo"))
      assertEquals(result.launch.preLaunchTask, Some("maven: clean package"))
      assert(result.launch.vmArgs.isDefined)
      assert(result.launch.vmArgs.get.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"))

      assertEquals(result.attach.`type`, "java")
      assertEquals(result.attach.request, "attach")
      assertEquals(result.attach.hostName, Some("localhost"))
      assertEquals(result.attach.port, Some(5005))
      assertEquals(result.attach.projectName, Some("Boot Maven Demo"))

      assertEquals(result.configurations.length, 2)
      assertEquals(result.configurations.head.name, result.launch.name)
      assertEquals(result.configurations(1).name, result.attach.name)
    finally
      os.remove.all(Path(dir))
  }

  test("DapConfigGenerator: Spring Boot Gradle project generates gradle: clean build preLaunchTask") {
    val dir = createTempDir()
    try
      writeFile(dir, "build.gradle", "rootProject.name = 'boot-gradle-service'\n")
      writeFile(dir, "src/main/kotlin/com/example/GradleApp.kt",
        """package com.example
          |import org.springframework.boot.autoconfigure.SpringBootApplication
          |
          |@SpringBootApplication
          |class GradleApp
          |""".stripMargin)

      val response = DapConfigGenerator.generateDapConfig(dir)
      assert(response.success)
      assert(response.data.isDefined)

      val result = response.data.get
      assertEquals(result.launch.projectName, Some("boot-gradle-service"))
      assertEquals(result.launch.mainClass, Some("com.example.GradleApp"))
      assertEquals(result.launch.preLaunchTask, Some("gradle: clean build"))
    finally
      os.remove.all(Path(dir))
  }

  test("DapConfigGenerator: extracts Spring active profiles into SPRING_PROFILES_ACTIVE env") {
    val dir = createTempDir()
    try
      writeFile(dir, "pom.xml", "<project><artifactId>profile-demo</artifactId></project>")
      writeFile(dir, "src/main/java/com/example/ProfileApp.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class ProfileApp {}""".stripMargin)
      writeFile(dir, "src/main/resources/application.yml",
        """spring:
          |  profiles:
          |    active: dev,local
          |""".stripMargin)

      val response = DapConfigGenerator.generateDapConfig(dir)
      assert(response.success)
      val result = response.data.get
      assertEquals(result.launch.env.get("SPRING_PROFILES_ACTIVE"), Some("dev,local"))
    finally
      os.remove.all(Path(dir))
  }

  test("DapConfigGenerator: Plain Java project without Spring generates standard JVM configs") {
    val dir = createTempDir()
    try
      writeFile(dir, "pom.xml",
        """<?xml version="1.0"?>
          |<project>
          |  <artifactId>plain-java-app</artifactId>
          |  <name>Plain Java App</name>
          |</project>""".stripMargin)

      writeFile(dir, "src/main/java/com/example/plain/MainApp.java",
        """package com.example.plain;
          |
          |public class MainApp {
          |  public static void main(String[] args) {
          |    System.out.println("Hello World");
          |  }
          |}""".stripMargin)

      val response = DapConfigGenerator.generateDapConfig(dir)
      assert(response.success)
      assert(response.data.isDefined)

      val result = response.data.get
      assertEquals(result.launch.`type`, "java")
      assertEquals(result.launch.name, "Launch: Plain Java App")
      assertEquals(result.launch.request, "launch")
      assertEquals(result.launch.mainClass, Some("com.example.plain.MainApp"))
      assertEquals(result.launch.projectName, Some("Plain Java App"))
      assertEquals(result.launch.preLaunchTask, Some("maven: clean package"))
      assertEquals(result.attach.port, Some(5005))
    finally
      os.remove.all(Path(dir))
  }

  test("DapConfigGenerator: Non-existent directory returns FILE_NOT_FOUND error envelope") {
    val response = DapConfigGenerator.generateDapConfig("/nonexistent/directory/for/dap")
    assert(!response.success)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
    assert(response.error.isDefined)
  }

  test("DapConfigGenerator: Plain SBT project detects build tool and main class") {
    val dir = createTempDir()
    try
      writeFile(dir, "build.sbt", """name := "scala-sbt-demo"""")
      writeFile(dir, "src/main/scala/com/example/Main.scala",
        """package com.example
          |
          |object Main extends App {
          |  println("SBT App")
          |}""".stripMargin)

      val response = DapConfigGenerator.generateDapConfig(dir)
      assert(response.success)
      val result = response.data.get
      assertEquals(result.launch.projectName, Some("scala-sbt-demo"))
      assertEquals(result.launch.mainClass, Some("com.example.Main"))
      assertEquals(result.launch.preLaunchTask, Some("sbt: compile"))
    finally
      os.remove.all(Path(dir))
  }

