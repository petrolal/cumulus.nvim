package cumulus.code

import munit.FunSuite
import java.nio.file.{Files, Paths}
import os.Path

class CodeLensTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("codelens-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    Files.write(filePath, content.getBytes("UTF-8"))
    filePath.toString

  // ===== @Test Annotation Tests =====

  test("CodeLensExtractor: @Test annotation in Java file") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  @Test
        |  public void testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: @Test annotation at line 5") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  private int x;
        |  public void setup() {}
        |
        |  @Test
        |  public void testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 5)
      assert(items(0).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  // ===== @Scheduled Annotation Tests =====

  test("CodeLensExtractor: @Scheduled annotation") {
    val dir = createTempDir()
    try
      val javaContent = """public class Task {
        |  @Scheduled(cron = "0 0 * * * *")
        |  public void task() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Task.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "⏰ Scheduled Task")
    finally
      os.remove.all(Path(dir))
  }

  // ===== main() Method Tests =====

  test("CodeLensExtractor: main method at line 12") {
    val dir = createTempDir()
    try
      val javaContent = """public class App {
        |  public static void doSomething() {}
        |
        |
        |  public static void doMore() {}
        |
        |
        |
        |
        |  public static void helperMethod(String[] args) {}
        |
        |  public static void main(String[] args) {
        |    System.out.println("Hello");
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "App.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 12)
      assert(items(0).title == "▶ Run Main")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: main method with spacing variations") {
    val dir = createTempDir()
    try
      val javaContent = """public class App {
        |  public  static   void   main  (  String[]  args  ) {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "App.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Main")
    finally
      os.remove.all(Path(dir))
  }

  // ===== @KafkaListener & @EventListener Tests =====

  test("CodeLensExtractor: @KafkaListener annotation") {
    val dir = createTempDir()
    try
      val javaContent = """public class Listener {
        |  @KafkaListener(topics = "my-topic")
        |  public void onMessage(String msg) {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Listener.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "🎧 Event Listener")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: @EventListener annotation") {
    val dir = createTempDir()
    try
      val javaContent = """public class Listener {
        |  @EventListener
        |  public void onApplicationEvent(ApplicationEvent event) {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Listener.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "🎧 Event Listener")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: both @KafkaListener and @EventListener in separate methods") {
    val dir = createTempDir()
    try
      val javaContent = """public class Listener {
        |  @KafkaListener(topics = "topic1")
        |  public void onKafkaMessage(String msg) {
        |  }
        |
        |  @EventListener
        |  public void onEvent(ApplicationEvent event) {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Listener.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 2)
      assert(items(0).line == 2)
      assert(items(0).title == "🎧 Event Listener")
      assert(items(1).line == 6)
      assert(items(1).title == "🎧 Event Listener")
    finally
      os.remove.all(Path(dir))
  }

  // ===== Multiple Annotations on Same Line (Deduplication) =====

  test("CodeLensExtractor: multiple annotations on same line") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  @Test @DisplayName("foo")
        |  public void test() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  // ===== Kotlin File Support =====

  test("CodeLensExtractor: @Test annotation in Kotlin file") {
    val dir = createTempDir()
    try
      val kotlinContent = """class MyTest {
        |  @Test
        |  fun testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.kt", kotlinContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  // ===== Comment Line Filtering =====

  test("CodeLensExtractor: filters out annotation in single-line comment") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  // @Test - this is a comment
        |  public void testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: filters out annotation in block comment start") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  /* @Test - this is a block comment
        |  public void testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: annotation after comment on same line") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  @Test // this is a comment after annotation
        |  public void testFoo() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: block comment with annotation inline is filtered") {
    val dir = createTempDir()
    try
      val javaContent = """public class Test {
        |  @Test /* inline block comment */ public void test() { }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Test.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.isEmpty)  // Should be filtered due to /* in line
    finally
      os.remove.all(Path(dir))
  }

  // ===== No Annotations Tests =====

  test("CodeLensExtractor: no annotations returns empty list") {
    val dir = createTempDir()
    try
      val javaContent = """public class Util {
        |  public static String join(List<String> items) {
        |    return String.join(",", items);
        |  }
        |
        |  public static int add(int a, int b) {
        |    return a + b;
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Util.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  // ===== Edge Cases =====

  test("CodeLensExtractor: empty file returns empty list") {
    val dir = createTempDir()
    try
      val filePath = createTempFile(dir, "Empty.java", "")
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: malformed annotation is still detected by regex") {
    val dir = createTempDir()
    try
      val javaContent = """public class MyTest {
        |  @Test(unclosed
        |  public void validTest() {
        |  }
        |
        |  @Test
        |  public void anotherTest() {
        |  }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MyTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      // Regex matches both the malformed @Test(unclosed on line 2 and valid @Test on line 6
      assert(items.length == 2)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
      assert(items(1).line == 6)
      assert(items(1).title == "▶ Run Test")
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: large file with many classes") {
    val dir = createTempDir()
    try
      val largeContent = (1 to 100).map { i =>
        if i % 10 == 0 then
          s"""public class Test$i {
            |  @Test
            |  public void test$i() { }
            |}
            |"""
        else
          s"""public class Util$i {
            |  public void method$i() { }
            |}
            |"""
      }.mkString
      val filePath = createTempFile(dir, "Large.java", largeContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      // Should find 10 @Test annotations
      assert(items.length == 10)
      // Verify they're in correct order and have correct line numbers
      assert(items.forall(_.title == "▶ Run Test"))
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: mixed annotations in single file") {
    val dir = createTempDir()
    try
      val javaContent = """public class Mixed {
        |  @Test
        |  public void testMethod() { }
        |
        |  @Scheduled(cron = "0 0 * * * *")
        |  public void scheduledTask() { }
        |
        |  @KafkaListener(topics = "topic1")
        |  public void kafkaListener(String msg) { }
        |
        |  @EventListener
        |  public void eventListener(ApplicationEvent e) { }
        |
        |  public static void main(String[] args) { }
        |}""".stripMargin
      val filePath = createTempFile(dir, "Mixed.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 5)
      assert(items(0).line == 2)
      assert(items(0).title == "▶ Run Test")
      assert(items(1).line == 5)
      assert(items(1).title == "⏰ Scheduled Task")
      assert(items(2).line == 8)
      assert(items(2).title == "🎧 Event Listener")
      assert(items(3).line == 11)
      assert(items(3).title == "🎧 Event Listener")
      assert(items(4).line == 14)
      assert(items(4).title == "▶ Run Main")
    finally
      os.remove.all(Path(dir))
  }

  // ===== File Not Found Test =====

  test("CodeLensExtractor: file not found throws exception") {
    val exception = intercept[Exception] {
      CodeLensExtractor.extractCodeLens("/nonexistent/path/File.java")
    }
    assert(exception.getMessage.contains("not found"))
  }

  // ===== Line Number Accuracy Tests =====

  test("CodeLensExtractor: line numbers are accurate for CRLF line endings") {
    val dir = createTempDir()
    try
      val javaContent = "public class Test {\r\n  @Test\r\n  public void test() { }\r\n}"
      val filePath = createTempFile(dir, "Test.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: line numbers are accurate for LF line endings") {
    val dir = createTempDir()
    try
      val javaContent = "public class Test {\n  @Test\n  public void test() { }\n}"
      val filePath = createTempFile(dir, "Test.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 1)
      assert(items(0).line == 2)
    finally
      os.remove.all(Path(dir))
  }

  test("CodeLensExtractor: preserves order of detection") {
    val dir = createTempDir()
    try
      val javaContent = """public class MultiTest {
        |  @Test public void test1() { }
        |  @Scheduled(cron = "0 0 * * * *") public void sched() { }
        |  @Test public void test2() { }
        |}""".stripMargin
      val filePath = createTempFile(dir, "MultiTest.java", javaContent)
      val items = CodeLensExtractor.extractCodeLens(filePath)
      assert(items.length == 3)
      assert(items(0).line == 2)
      assert(items(1).line == 3)
      assert(items(2).line == 4)
    finally
      os.remove.all(Path(dir))
  }
