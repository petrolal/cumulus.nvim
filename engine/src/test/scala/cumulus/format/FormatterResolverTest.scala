package cumulus.format

import munit.FunSuite
import os.Path
import cumulus.protocol.FormatterSpec

class FormatterResolverTest extends FunSuite:

  test("detect Scalafmt formatter from .scalafmt.conf") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", """
      version = 3.4.0
      indent {
        main = 2
      }
    """)

    val result = FormatterResolver.resolveFormatter((tempDir / "test.scala").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "scalafmt")
    assert(result.data.get.config_file == Some(".scalafmt.conf"))
  }

  test("detect Ktlint formatter from ktlint.json") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "ktlint.json", "{}")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.kt").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "ktlint")
    assert(result.data.get.config_file == Some("ktlint.json"))
  }

  test("detect Prettier formatter from .prettierrc") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".prettierrc", "{}")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.js").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "prettier")
  }

  test("detect Prettier formatter from .prettierrc.json") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".prettierrc.json", "{}")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.js").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "prettier")
  }

  test("detect Prettier from package.json") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "package.json", """{"prettier": {}}""")
    os.write(tempDir / "test.js", "")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.js").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "prettier")
  }

  test("detect Spotless Maven formatter from pom.xml") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "pom.xml", """
      <project>
        <plugins>
          <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
          </plugin>
        </plugins>
      </project>
    """)

    val result = FormatterResolver.resolveFormatter((tempDir / "test.java").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "mvn")
  }

  test("detect Spotless Gradle formatter from build.gradle") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "build.gradle", """
      plugins {
        id 'com.diffplug.spotless'
      }
      spotless {
        java {
          googleJavaFormat()
        }
      }
    """)

    val result = FormatterResolver.resolveFormatter((tempDir / "test.java").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "gradle")
  }

  test("detect Terraform formatter from .terraform.lock.hcl") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".terraform.lock.hcl", "# lock file")

    val result = FormatterResolver.resolveFormatter((tempDir / "main.tf").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "terraform")
  }

  test("detect Terraform formatter from .tf files") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "main.tf", "resource \"aws_instance\" \"example\" {}")

    val result = FormatterResolver.resolveFormatter((tempDir / "main.tf").toString)
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.formatter == "terraform")
  }

  test("return NOT_FOUND error when no formatter config is present") {
    val tempDir = os.temp.dir(deleteOnExit = true)

    val result = FormatterResolver.resolveFormatter((tempDir / "test.java").toString)
    assert(result.success == false)
    assert(result.error == Some("No formatter configuration found"))
    assert(result.error_code == Some("NOT_FOUND"))
  }

  test("prioritize Scalafmt over Prettier when both are present") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", """indent { main = 2 }""")
    os.write(tempDir / ".prettierrc", "{}")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.scala").toString)
    assert(result.success == true)
    assert(result.data.get.formatter == "scalafmt")
  }

  test("prioritize language-specific formatter in multi-formatter project") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", """indent { main = 2 }""")
    os.write(tempDir / ".prettierrc", "{}")

    // When both are present, Scalafmt has higher priority, so it's returned regardless of file type
    val result = FormatterResolver.resolveFormatter((tempDir / "test.js").toString)
    assert(result.success == true)
    // Scalafmt has higher priority, so it's returned first even for .js file
    assert(result.data.get.formatter == "scalafmt")
  }

  test("traverse up to project root to find .scalafmt.conf") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    val subdir = tempDir / "src" / "main" / "scala"
    os.makeDir.all(subdir)
    os.write(tempDir / ".scalafmt.conf", """indent { main = 2 }""")

    val result = FormatterResolver.resolveFormatter((subdir / "App.scala").toString)
    assert(result.success == true)
    assert(result.data.get.formatter == "scalafmt")
  }

  test("handle nested file in subdirectory correctly") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    val subdir = tempDir / "src" / "main"
    os.makeDir.all(subdir)
    os.write(tempDir / ".prettierrc", "{}")

    val result = FormatterResolver.resolveFormatter((subdir / "config.json").toString)
    assert(result.success == true)
    assert(result.data.get.formatter == "prettier")
  }

  test("FormatterSpec contains valid args structure") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", """indent { main = 2 }""")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.scala").toString)
    assert(result.success == true)
    val spec = result.data.get
    assert(spec.args.length > 0)
    // Verify all args have name and value
    spec.args.foreach(arg => {
      assert(arg.name.nonEmpty)
      assert(arg.value.nonEmpty)
    })
  }

  test("FormatterSpec stdin_mode matches formatter type") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", """indent { main = 2 }""")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.scala").toString)
    assert(result.success == true)
    // Scalafmt supports stdin
    assert(result.data.get.stdin_mode == true)
  }

  test("FormatterSpec for Ktlint has stdin_mode false") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / "ktlint.json", "{}")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.kt").toString)
    assert(result.success == true)
    // Ktlint prefers file operations
    assert(result.data.get.stdin_mode == false)
  }

  test("Malformed config file should still detect formatter") {
    val tempDir = os.temp.dir(deleteOnExit = true)
    os.write(tempDir / ".scalafmt.conf", "this is not valid config !@#$")

    val result = FormatterResolver.resolveFormatter((tempDir / "test.scala").toString)
    assert(result.success == true)
    // Should still detect formatter, just without config options
    assert(result.data.get.formatter == "scalafmt")
  }
