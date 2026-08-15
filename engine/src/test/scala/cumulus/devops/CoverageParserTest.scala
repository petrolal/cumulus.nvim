package cumulus.devops

import munit.FunSuite
import os.Path

class CoverageParserTest extends FunSuite:

  test("parse standard JaCoCo XML content") {
    val xml =
      """<?xml version="1.0" encoding="UTF-8"?>
        |<report name="cumulus">
        |  <package name="com/example/demo">
        |    <sourcefile name="App.java">
        |      <line nr="10" mi="0" ci="5" mb="0" cb="0"/>
        |      <line nr="11" mi="0" ci="2" mb="0" cb="0"/>
        |      <line nr="15" mi="3" ci="0" mb="0" cb="0"/>
        |      <line nr="20" mi="1" ci="0" mb="0" cb="0"/>
        |    </sourcefile>
        |  </package>
        |</report>
        |""".stripMargin

    val entries = CoverageParser.parseJacocoXml(xml)
    assertEquals(entries.length, 1)
    assertEquals(entries.head.file, "com/example/demo/App.java")
    assertEquals(entries.head.covered_lines, Seq(10, 11))
    assertEquals(entries.head.missed_lines, Seq(15, 20))
  }

  test("handle dotted package names in JaCoCo XML") {
    val xml =
      """<report name="demo">
        |  <package name="com.example.service">
        |    <sourcefile name="UserService.kt">
        |      <line nr="5" mi="0" ci="1"/>
        |    </sourcefile>
        |  </package>
        |</report>
        |""".stripMargin

    val entries = CoverageParser.parseJacocoXml(xml)
    assertEquals(entries.length, 1)
    assertEquals(entries.head.file, "com/example/service/UserService.kt")
    assertEquals(entries.head.covered_lines, Seq(5))
  }

  test("handle default package (empty package name)") {
    val xml =
      """<report name="demo">
        |  <package name="">
        |    <sourcefile name="Root.java">
        |      <line nr="1" mi="0" ci="1"/>
        |    </sourcefile>
        |  </package>
        |</report>
        |""".stripMargin

    val entries = CoverageParser.parseJacocoXml(xml)
    assertEquals(entries.length, 1)
    assertEquals(entries.head.file, "Root.java")
    assertEquals(entries.head.covered_lines, Seq(1))
    assertEquals(entries.head.missed_lines, Seq.empty[Int])
  }

  test("handle multiple packages and sourcefiles") {
    val xml =
      """<report name="demo">
        |  <package name="com/pkg1">
        |    <sourcefile name="File1.java">
        |      <line nr="5" mi="0" ci="1"/>
        |    </sourcefile>
        |    <sourcefile name="File2.java">
        |      <line nr="8" mi="2" ci="0"/>
        |    </sourcefile>
        |  </package>
        |  <package name="com/pkg2">
        |    <sourcefile name="File3.java">
        |      <line nr="100" mi="0" ci="10"/>
        |    </sourcefile>
        |  </package>
        |</report>
        |""".stripMargin

    val entries = CoverageParser.parseJacocoXml(xml)
    assertEquals(entries.length, 3)
    assertEquals(entries(0).file, "com/pkg1/File1.java")
    assertEquals(entries(0).covered_lines, Seq(5))
    assertEquals(entries(1).file, "com/pkg1/File2.java")
    assertEquals(entries(1).missed_lines, Seq(8))
    assertEquals(entries(2).file, "com/pkg2/File3.java")
    assertEquals(entries(2).covered_lines, Seq(100))
  }

  test("handle empty XML content") {
    val entries = CoverageParser.parseJacocoXml("")
    assertEquals(entries, Seq.empty)
  }

  test("parseCoverage from temporary file") {
    val tempDir = os.temp.dir(prefix = "cumulus-cov-test")
    try
      val reportFile = tempDir / "jacoco.xml"
      val xml =
        """<report name="test">
          |  <package name="org/test">
          |    <sourcefile name="TestService.java">
          |      <line nr="42" mi="0" ci="1"/>
          |    </sourcefile>
          |  </package>
          |</report>
          |""".stripMargin
      os.write(reportFile, xml)

      val response = CoverageParser.parseCoverage(reportFile.toString)
      assertEquals(response.success, true)
      assert(response.data.isDefined)
      val entries = response.data.get
      assertEquals(entries.length, 1)
      assertEquals(entries.head.file, "org/test/TestService.java")
      assertEquals(entries.head.covered_lines, Seq(42))
    finally
      os.remove.all(tempDir)
  }

  test("parseCoverage returns FILE_NOT_FOUND for non-existent file") {
    val response = CoverageParser.parseCoverage("/non/existent/jacoco.xml")
    assertEquals(response.success, false)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
    assert(response.error.get.contains("File not found"))
  }

  test("parseCoverage returns PARSE_ERROR for malformed XML") {
    val tempDir = os.temp.dir(prefix = "cumulus-cov-err")
    try
      val badFile = tempDir / "malformed.xml"
      os.write(badFile, "<report><unclosed>")

      val response = CoverageParser.parseCoverage(badFile.toString)
      assertEquals(response.success, false)
      assertEquals(response.error_code, Some("PARSE_ERROR"))
    finally
      os.remove.all(tempDir)
  }

  test("parseCoverage returns INVALID_INPUT when path is a directory") {
    val tempDir = os.temp.dir(prefix = "cumulus-cov-dir")
    try
      val response = CoverageParser.parseCoverage(tempDir.toString)
      assertEquals(response.success, false)
      assertEquals(response.error_code, Some("INVALID_INPUT"))
    finally
      os.remove.all(tempDir)
  }
