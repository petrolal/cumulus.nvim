package cumulus.util

import munit.FunSuite
import os.Path

class SessionSanitizerTest extends FunSuite:

  test("filters [No Name], term://, and snacks buffers from session lines") {
    val lines = Seq(
      "badd +0 /home/user/project/src/Main.java",
      "badd +0 [No Name]",
      "badd +0 term://localhost:1000:1",
      "badd +0 snacks_dashboard",
      "badd +0 /home/user/project/build.gradle",
      "badd +0 snacks_picker"
    )

    val (filtered, removed) = SessionSanitizer.filterSessionLines(lines)
    assertEquals(removed, 4)
    assertEquals(filtered.length, 2)
    assert(filtered(0).contains("Main.java"))
    assert(filtered(1).contains("build.gradle"))
  }

  test("filters floating window and buftype=nofile commands") {
    val lines = Seq(
      "badd +0 /home/user/App.scala",
      "setlocal buftype=nofile",
      "setlocal buftype=floating",
      "let w:snacks_win = 1"
    )

    val (filtered, removed) = SessionSanitizer.filterSessionLines(lines)
    assertEquals(removed, 3)
    assertEquals(filtered.length, 1)
  }

  test("sanitizes session file on disk") {
    val tempFile = os.temp(
      """badd +0 /workspace/App.java
        |badd +0 [No Name]
        |badd +0 term://bash
        |badd +0 /workspace/Pom.xml
        |""".stripMargin
    )
    try
      val response = SessionSanitizer.sanitizeSession(tempFile.toString)
      assert(response.success)
      val res = response.data.get
      assertEquals(res.cleaned_lines, 2)
      assertEquals(res.total_lines, 4)

      val contentAfter = os.read(tempFile)
      assert(contentAfter.contains("App.java"))
      assert(contentAfter.contains("Pom.xml"))
      assert(!contentAfter.contains("[No Name]"))
      assert(!contentAfter.contains("term://"))
    finally
      os.remove(tempFile)
  }

  test("handles non-existent session file") {
    val response = SessionSanitizer.sanitizeSession("/path/to/missing_session.vim")
    assert(!response.success)
    assertEquals(response.data.get.success, false)
  }
