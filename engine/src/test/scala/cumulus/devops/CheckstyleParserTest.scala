package cumulus.devops

import munit.FunSuite
import os.Path

class CheckstyleParserTest extends FunSuite:

  test("parse standard Checkstyle XML content") {
    val xml =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<checkstyle version="10.0">
        |  <file name="/src/main/java/com/example/App.java">
        |    <error line="15" column="5" severity="warning" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck"/>
        |    <error line="22" column="1" severity="error" message="Line is longer than 100 characters."/>
        |  </file>
        |</checkstyle>
        |""".stripMargin

    val response = CheckstyleParser.parseCheckstyle(xml)
    assertEquals(response.success, true)
    val diags = response.data.get
    assertEquals(diags.length, 2)
    assertEquals(diags(0).file, "/src/main/java/com/example/App.java")
    assertEquals(diags(0).line, 15)
    assertEquals(diags(0).col, Some(5))
    assertEquals(diags(0).severity, "WARNING")
    assertEquals(diags(0).message, "Missing a Javadoc comment.")

    assertEquals(diags(1).file, "/src/main/java/com/example/App.java")
    assertEquals(diags(1).line, 22)
    assertEquals(diags(1).col, Some(1))
    assertEquals(diags(1).severity, "ERROR")
    assertEquals(diags(1).message, "Line is longer than 100 characters.")
  }

  test("handle missing column in error element") {
    val xml =
      """<checkstyle version="10.0">
        |  <file name="/src/Foo.java">
        |    <error line="40" severity="info" message="TODO item found."/>
        |  </file>
        |</checkstyle>
        |""".stripMargin

    val response = CheckstyleParser.parseCheckstyle(xml)
    assertEquals(response.success, true)
    val diags = response.data.get
    assertEquals(diags.length, 1)
    assertEquals(diags.head.col, None)
    assertEquals(diags.head.severity, "INFO")
  }

  test("handle missing severity attribute defaulting to WARN") {
    val xml =
      """<checkstyle version="10.0">
        |  <file name="/src/Bar.java">
        |    <error line="10" message="Naming convention violation."/>
        |  </file>
        |</checkstyle>
        |""".stripMargin

    val response = CheckstyleParser.parseCheckstyle(xml)
    assertEquals(response.success, true)
    val diags = response.data.get
    assertEquals(diags.length, 1)
    assertEquals(diags.head.severity, "WARN")
  }

  test("handle empty message fallback to source attribute") {
    val xml =
      """<checkstyle version="10.0">
        |  <file name="/src/Bar.java">
        |    <error line="10" source="com.puppycrawl.checks.NewlineAtEndOfFileCheck"/>
        |  </file>
        |</checkstyle>
        |""".stripMargin

    val response = CheckstyleParser.parseCheckstyle(xml)
    assertEquals(response.success, true)
    val diags = response.data.get
    assertEquals(diags.length, 1)
    assertEquals(diags.head.message, "com.puppycrawl.checks.NewlineAtEndOfFileCheck")
  }

  test("handle empty XML / clean checkstyle report") {
    val xml =
      """<checkstyle version="10.0">
        |  <file name="/src/Clean.java"/>
        |</checkstyle>
        |""".stripMargin

    val response = CheckstyleParser.parseCheckstyle(xml)
    assertEquals(response.success, true)
    assertEquals(response.data.get, Seq.empty)
  }

  test("handle empty string input") {
    val response = CheckstyleParser.parseCheckstyle("")
    assertEquals(response.success, true)
    assertEquals(response.data.get, Seq.empty)
  }

  test("parseCheckstyleFile from temporary file") {
    val tempDir = os.temp.dir(prefix = "cumulus-cs-test")
    try
      val reportFile = tempDir / "checkstyle-result.xml"
      val xml =
        """<checkstyle version="10.0">
          |  <file name="/src/Service.java">
          |    <error line="3" column="8" severity="error" message="Unused import."/>
          |  </file>
          |</checkstyle>
          |""".stripMargin
      os.write(reportFile, xml)

      val response = CheckstyleParser.parseCheckstyleFile(reportFile.toString)
      assertEquals(response.success, true)
      val diags = response.data.get
      assertEquals(diags.length, 1)
      assertEquals(diags.head.file, "/src/Service.java")
      assertEquals(diags.head.line, 3)
      assertEquals(diags.head.col, Some(8))
      assertEquals(diags.head.severity, "ERROR")
    finally
      os.remove.all(tempDir)
  }

  test("parseCheckstyleFile returns FILE_NOT_FOUND for non-existent file") {
    val response = CheckstyleParser.parseCheckstyleFile("/non/existent/checkstyle.xml")
    assertEquals(response.success, false)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
  }

  test("parseCheckstyle returns PARSE_ERROR for malformed XML") {
    val response = CheckstyleParser.parseCheckstyle("<checkstyle><unclosed>")
    assertEquals(response.success, false)
    assertEquals(response.error_code, Some("PARSE_ERROR"))
  }
