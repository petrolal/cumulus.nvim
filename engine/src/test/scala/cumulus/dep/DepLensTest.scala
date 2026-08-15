package cumulus.dep

import munit.FunSuite

class DepLensTest extends FunSuite:

  test("classifies semver ages correctly") {
    assertEquals(DepLens.classifyVersionAge("1.2.3", "1.2.3"), "CURRENT")
    assertEquals(DepLens.classifyVersionAge("1.2.0", "1.2.5"), "PATCH_OUTDATED")
    assertEquals(DepLens.classifyVersionAge("1.2.0", "1.3.0"), "MINOR_OUTDATED")
    assertEquals(DepLens.classifyVersionAge("1.2.0", "2.0.0"), "MAJOR_OUTDATED")
    assertEquals(DepLens.classifyVersionAge("invalid", "1.0.0"), "UNKNOWN")
  }

  test("extracts line numbers from Maven POM dependencies") {
    val pomXml =
      """<project>
        |  <dependencies>
        |    <dependency>
        |      <groupId>org.slf4j</groupId>
        |      <artifactId>slf4j-api</artifactId>
        |      <version>2.0.9</version>
        |    </dependency>
        |  </dependencies>
        |</project>
        |""".stripMargin

    val lenses = DepLens.parsePomWithLines(pomXml)
    assertEquals(lenses.length, 1)
    assertEquals(lenses.head.group, "org.slf4j")
    assertEquals(lenses.head.artifact, "slf4j-api")
    assertEquals(lenses.head.current_version, "2.0.9")
    assertEquals(lenses.head.line, 3)
  }

  test("extracts line numbers from Gradle version catalog") {
    val toml =
      """[versions]
        |junit = "5.10.0"
        |
        |[libraries]
        |junit-jupiter = "org.junit.jupiter:junit-jupiter:5.10.0"
        |""".stripMargin

    val lenses = DepLens.parseGradleVersionsWithLines(toml)
    assertEquals(lenses.length, 1)
    assertEquals(lenses.head.group, "org.junit.jupiter")
    assertEquals(lenses.head.artifact, "junit-jupiter")
    assertEquals(lenses.head.current_version, "5.10.0")
    assertEquals(lenses.head.line, 5)
  }

  test("handles versions with build metadata and pre-release suffixes") {
    assertEquals(DepLens.classifyVersionAge("1.2.3+build1", "1.2.3"), "CURRENT")
    assertEquals(DepLens.classifyVersionAge("1.2.0-RC1", "1.2.5"), "PATCH_OUTDATED")
    assertEquals(DepLens.classifyVersionAge("1.0.0_release", "2.0.0"), "MAJOR_OUTDATED")
  }

  test("resolves inline properties and built-in project coordinates") {
    val pomXml =
      """<project>
        |  <groupId>com.example</groupId>
        |  <artifactId>app</artifactId>
        |  <version>1.5.0</version>
        |  <properties><lib.version>2.0.0</lib.version></properties>
        |  <dependencies>
        |    <dependency>
        |      <groupId>com.example</groupId>
        |      <artifactId>core</artifactId>
        |      <version>${project.version}</version>
        |    </dependency>
        |    <dependency>
        |      <groupId>org.sample</groupId>
        |      <artifactId>lib</artifactId>
        |      <version>${lib.version}</version>
        |    </dependency>
        |  </dependencies>
        |</project>""".stripMargin

    val lenses = DepLens.parsePomWithLines(pomXml)
    assertEquals(lenses.length, 2)
    assertEquals(lenses(0).current_version, "1.5.0")
    assertEquals(lenses(1).current_version, "2.0.0")
  }

