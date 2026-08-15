package cumulus.dep

import munit.FunSuite

class DepResolverTest extends FunSuite:

  test("parses Maven POM with property substitution") {
    val pomXml =
      """<project>
        |  <properties>
        |    <spring.boot.version>3.2.0</spring.boot.version>
        |    <log.scope>test</log.scope>
        |  </properties>
        |  <dependencies>
        |    <dependency>
        |      <groupId>org.springframework.boot</groupId>
        |      <artifactId>spring-boot-starter-web</artifactId>
        |      <version>${spring.boot.version}</version>
        |      <scope>compile</scope>
        |    </dependency>
        |    <dependency>
        |      <groupId>org.junit.jupiter</groupId>
        |      <artifactId>junit-jupiter</artifactId>
        |      <version>5.10.0</version>
        |      <scope>${log.scope}</scope>
        |    </dependency>
        |  </dependencies>
        |</project>
        |""".stripMargin

    val deps = DepResolver.parsePomDependencies(pomXml)
    assertEquals(deps.length, 2)
    assertEquals(deps(0).group, "org.springframework.boot")
    assertEquals(deps(0).artifact, "spring-boot-starter-web")
    assertEquals(deps(0).version, "3.2.0")
    assertEquals(deps(0).scope, "compile")

    assertEquals(deps(1).group, "org.junit.jupiter")
    assertEquals(deps(1).artifact, "junit-jupiter")
    assertEquals(deps(1).version, "5.10.0")
    assertEquals(deps(1).scope, "test")
  }

  test("parses Gradle version catalog TOML file") {
    val toml =
      """[versions]
        |groovy = "3.0.5"
        |checkstyle = "8.37"
        |
        |[libraries]
        |groovy-core = "org.codehaus.groovy:groovy:3.0.5"
        |groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
        |checkstyle = { group = "com.puppycrawl.tools", name = "checkstyle", version.ref = "checkstyle" }
        |""".stripMargin

    val deps = DepResolver.parseVersionCatalog(toml)
    assertEquals(deps.length, 3)
    assertEquals(deps(0).group, "org.codehaus.groovy")
    assertEquals(deps(0).artifact, "groovy")
    assertEquals(deps(0).version, "3.0.5")

    assertEquals(deps(1).group, "org.codehaus.groovy")
    assertEquals(deps(1).artifact, "groovy-json")
    assertEquals(deps(1).version, "3.0.5")

    assertEquals(deps(2).group, "com.puppycrawl.tools")
    assertEquals(deps(2).artifact, "checkstyle")
    assertEquals(deps(2).version, "8.37")
  }
