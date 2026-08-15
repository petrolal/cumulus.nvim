package cumulus.git

import munit.FunSuite
import os.Path

class ConflictParserTest extends FunSuite:

  test("standard Git conflict block extraction") {
    val content =
      """<<<<<<< HEAD
        |foo
        |=======
        |bar
        |>>>>>>> feature""".stripMargin

    val blocks = ConflictParser.parseConflicts(content)
    assertEquals(blocks.length, 1)
    val block = blocks.head
    assertEquals(block.start_line, 1)
    assertEquals(block.sep_line, 3)
    assertEquals(block.end_line, 5)
    assertEquals(block.current_header, "HEAD")
    assertEquals(block.incoming_header, "feature")
  }

  test("bare conflict markers default to HEAD and INCOMING") {
    val content =
      """<<<<<<<
        |foo
        |=======
        |bar
        |>>>>>>>""".stripMargin

    val blocks = ConflictParser.parseConflicts(content)
    assertEquals(blocks.length, 1)
    val block = blocks.head
    assertEquals(block.start_line, 1)
    assertEquals(block.sep_line, 3)
    assertEquals(block.end_line, 5)
    assertEquals(block.current_header, "HEAD")
    assertEquals(block.incoming_header, "INCOMING")
  }

  test("multiple conflict blocks in single file") {
    val content =
      """public class App {
        |<<<<<<< main
        |    int x = 1;
        |=======
        |    int x = 2;
        |>>>>>>> patch-1
        |    void run() {}
        |<<<<<<< main
        |    int y = 10;
        |=======
        |    int y = 20;
        |>>>>>>> patch-1
        |}""".stripMargin

    val blocks = ConflictParser.parseConflicts(content)
    assertEquals(blocks.length, 2)

    val first = blocks.head
    assertEquals(first.start_line, 2)
    assertEquals(first.sep_line, 4)
    assertEquals(first.end_line, 6)
    assertEquals(first.current_header, "main")
    assertEquals(first.incoming_header, "patch-1")

    val second = blocks(1)
    assertEquals(second.start_line, 8)
    assertEquals(second.sep_line, 10)
    assertEquals(second.end_line, 12)
    assertEquals(second.current_header, "main")
    assertEquals(second.incoming_header, "patch-1")
  }

  test("clean file with no conflict markers returns empty list") {
    val content =
      """public class App {
        |    public static void main(String[] args) {
        |        System.out.println("Hello");
        |    }
        |}""".stripMargin

    val blocks = ConflictParser.parseConflicts(content)
    assertEquals(blocks, Seq.empty)
  }

  test("parseGitConflictsFile on file and non-existent path") {
    val tempFile = os.temp(prefix = "git-conflict-", suffix = ".txt")
    try
      val content =
        """<<<<<<< HEAD
          |current
          |=======
          |incoming
          |>>>>>>> branch-b""".stripMargin
      os.write.over(tempFile, content)

      val resValid = ConflictParser.parseGitConflictsFile(tempFile.toString)
      assert(resValid.success)
      assertEquals(resValid.data.get.length, 1)

      val resNotFound = ConflictParser.parseGitConflictsFile("/nonexistent/file.txt")
      assert(!resNotFound.success)
      assertEquals(resNotFound.error_code, Some("FILE_NOT_FOUND"))
    finally
      os.remove(tempFile)
  }
