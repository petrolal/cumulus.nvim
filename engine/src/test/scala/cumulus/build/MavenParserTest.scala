package cumulus.build

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File

class MavenParserTest extends FunSuite:

  private def createTempFile(content: String): String =
    val file = Files.createTempFile("pom", ".xml").toFile
    Files.write(file.toPath, content.getBytes)
    file.getAbsolutePath

  test("parse simple Maven POM with standard lifecycle goals") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>simple-app</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      val result = MavenParser.parseGoals(pomPath)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.error_code.isEmpty)
      assert(result.data.isDefined)

      val goals = result.data.get.goals
      val goalNames = goals.map(_.name).toSet
      val lifecycleGoals = Set("clean", "compile", "test", "package", "install")
      assert(goalNames.intersect(lifecycleGoals).size >= 5)

      // All lifecycle goals should have source "lifecycle"
      val lifecycleGoalsSeq = goals.filter(_.source == "lifecycle").map(_.name)
      assert(lifecycleGoalsSeq.contains("clean"))
      assert(lifecycleGoalsSeq.contains("compile"))
      assert(lifecycleGoalsSeq.contains("test"))
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("parse POM with Spring Boot plugin") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>spring-boot-app</artifactId>
      |  <version>1.0.0</version>
      |  <build>
      |    <plugins>
      |      <plugin>
      |        <groupId>org.springframework.boot</groupId>
      |        <artifactId>spring-boot-maven-plugin</artifactId>
      |        <version>3.0.0</version>
      |      </plugin>
      |    </plugins>
      |  </build>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(result.success)
    val goals = result.data.get.goals
    val goalNames = goals.map(_.name).toSet

    assert(goalNames.contains("spring-boot:run"))
    assert(goalNames.contains("spring-boot:build-image"))

    // Verify they have correct source
    val pluginGoals = goals.filter(_.source == "plugin")
    assert(pluginGoals.map(_.name).contains("spring-boot:run"))

    Files.delete(Paths.get(pomPath))
  }

  test("parse POM with Quarkus plugin") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>quarkus-app</artifactId>
      |  <version>1.0.0</version>
      |  <build>
      |    <plugins>
      |      <plugin>
      |        <groupId>io.quarkus</groupId>
      |        <artifactId>quarkus-maven-plugin</artifactId>
      |        <version>3.0.0</version>
      |      </plugin>
      |    </plugins>
      |  </build>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(result.success)
    val goals = result.data.get.goals
    val goalNames = goals.map(_.name).toSet

    assert(goalNames.contains("quarkus:dev"))
    assert(goalNames.contains("quarkus:build"))

    Files.delete(Paths.get(pomPath))
  }

  test("parse POM with Surefire and Exec plugins") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>multi-plugin-app</artifactId>
      |  <version>1.0.0</version>
      |  <build>
      |    <plugins>
      |      <plugin>
      |        <groupId>org.apache.maven.plugins</groupId>
      |        <artifactId>maven-surefire-plugin</artifactId>
      |        <version>2.22.0</version>
      |      </plugin>
      |      <plugin>
      |        <groupId>org.codehaus.mojo</groupId>
      |        <artifactId>exec-maven-plugin</artifactId>
      |        <version>3.0.0</version>
      |      </plugin>
      |    </plugins>
      |  </build>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(result.success)
    val goals = result.data.get.goals
    val goalNames = goals.map(_.name).toSet

    assert(goalNames.contains("surefire:test"))
    assert(goalNames.contains("exec:java"))
    assert(goalNames.contains("exec:exec"))

    Files.delete(Paths.get(pomPath))
  }

  test("parse multi-module POM with submodules") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>multi-module-app</artifactId>
      |  <version>1.0.0</version>
      |  <packaging>pom</packaging>
      |  <modules>
      |    <module>core</module>
      |    <module>web</module>
      |    <module>services</module>
      |  </modules>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseModules(pomPath)

    assert(result.success)
    assert(result.error.isEmpty)
    val modules = result.data.get.modules

    assert(modules.length == 3)
    assert(modules.map(_.name).toSet == Set("core", "web", "services"))

    // Check path normalization
    val corePath = modules.find(_.name == "core").get.path
    assert(corePath == "./core")

    Files.delete(Paths.get(pomPath))
  }

  test("parse POM with empty modules") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>simple-app</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseModules(pomPath)

    assert(result.success)
    val modules = result.data.get.modules
    assert(modules.isEmpty)

    Files.delete(Paths.get(pomPath))
  }

  test("parse POM with namespaced elements") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>namespaced-app</artifactId>
      |  <version>1.0.0</version>
      |  <build>
      |    <plugins>
      |      <plugin>
      |        <groupId>org.springframework.boot</groupId>
      |        <artifactId>spring-boot-maven-plugin</artifactId>
      |      </plugin>
      |    </plugins>
      |  </build>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(result.success)
    val goals = result.data.get.goals
    val goalNames = goals.map(_.name).toSet

    // Should still extract Spring Boot goals despite namespace
    assert(goalNames.contains("spring-boot:run"))

    Files.delete(Paths.get(pomPath))
  }

  test("handle missing file with FILE_NOT_FOUND error") {
    val result = MavenParser.parseGoals("/nonexistent/path/pom.xml")

    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("handle missing modules file with FILE_NOT_FOUND error") {
    val result = MavenParser.parseModules("/nonexistent/path/pom.xml")

    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("handle malformed XML with PARSE_ERROR") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project>
      |  <groupId>com.example</groupId>
      |  <!-- Missing closing tag -->
      |  <artifactId>broken-app</artifactId>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(!result.success)
    assert(result.error_code.contains("PARSE_ERROR"))

    Files.delete(Paths.get(pomPath))
  }

  test("extract multiple plugins with distinct goals") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>multi-plugin-app</artifactId>
      |  <version>1.0.0</version>
      |  <build>
      |    <plugins>
      |      <plugin>
      |        <groupId>org.springframework.boot</groupId>
      |        <artifactId>spring-boot-maven-plugin</artifactId>
      |      </plugin>
      |      <plugin>
      |        <groupId>io.quarkus</groupId>
      |        <artifactId>quarkus-maven-plugin</artifactId>
      |      </plugin>
      |    </plugins>
      |  </build>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseGoals(pomPath)

    assert(result.success)
    val goals = result.data.get.goals
    val goalNames = goals.map(_.name).toSet

    // Should have both Spring Boot and Quarkus goals
    assert(goalNames.contains("spring-boot:run"))
    assert(goalNames.contains("quarkus:dev"))
    assert(goalNames.contains("quarkus:build"))

    Files.delete(Paths.get(pomPath))
  }

  test("maintain declaration order of modules") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>ordered-modules</artifactId>
      |  <version>1.0.0</version>
      |  <packaging>pom</packaging>
      |  <modules>
      |    <module>alpha</module>
      |    <module>beta</module>
      |    <module>gamma</module>
      |  </modules>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    val result = MavenParser.parseModules(pomPath)

    assert(result.success)
    val moduleNames = result.data.get.modules.map(_.name)
    assert(moduleNames == Seq("alpha", "beta", "gamma"))

    Files.delete(Paths.get(pomPath))
  }
