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
