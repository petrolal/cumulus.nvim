package cumulus.code

import munit.FunSuite
import scala.io.Source
import java.io.File
import java.nio.file.{Files, Paths}
import os.Path

class SpringBootTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("springboot-test-").toString

  private def writeFile(dir: String, path: String, content: String): Unit =
    val filePath = Paths.get(dir, path)
    Files.createDirectories(filePath.getParent)
    Files.write(filePath, content.getBytes("UTF-8"))

  // ===== Spring Boot Detection Tests =====

  test("detect @SpringBootApplication in src/main/java") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/Main.java",
        """package com.example;
          |import org.springframework.boot.SpringApplication;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class Main {
          |  public static void main(String[] args) {
          |    SpringApplication.run(Main.class, args);
          |  }
          |}
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.main_class == "com.example.Main")
    finally
      os.remove.all(Path(dir))
  }

  test("extract project name from pom.xml") {
    val dir = createTempDir()
    try
      writeFile(dir, "pom.xml",
        """<?xml version="1.0"?>
          |<project>
          |  <artifactId>my-app</artifactId>
          |  <name>My Application</name>
          |</project>
          |""".stripMargin)
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.project_name == "My Application")
      assert(app.build_tool == Some("maven"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract project name from build.gradle") {
    val dir = createTempDir()
    try
      writeFile(dir, "build.gradle",
        """rootProject.name = 'gradle-app'
          |""".stripMargin)
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.project_name == "gradle-app")
      assert(app.build_tool == Some("gradle"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect active profiles from application.yml") {
    val dir = createTempDir()
    try
      writeFile(dir, "application.yml",
        """spring:
          |  profiles:
          |    active: prod,debug
          |""".stripMargin)
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.active_profiles.contains("prod"))
      assert(app.active_profiles.contains("debug"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect active profiles from application.properties") {
    val dir = createTempDir()
    try
      writeFile(dir, "application.properties",
        """spring.profiles.active=dev,test
          |""".stripMargin)
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.active_profiles.contains("dev"))
      assert(app.active_profiles.contains("test"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract JVM debug args from Maven pom.xml") {
    val dir = createTempDir()
    try
      writeFile(dir, "pom.xml",
        """<?xml version="1.0"?>
          |<project>
          |  <build>
          |    <plugins>
          |      <plugin>
          |        <artifactId>maven-surefire-plugin</artifactId>
          |        <configuration>
          |          <argLine>-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005</argLine>
          |        </configuration>
          |      </plugin>
          |    </plugins>
          |  </build>
          |</project>
          |""".stripMargin)
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.jvm_debug_args.isDefined)
      assert(app.jvm_debug_args.get.contains("jdwp"))
    finally
      os.remove.all(Path(dir))
  }

  test("error when no Spring Boot application found") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/Util.java",
        """package com.example;
          |
          |public class Util { }
          |""".stripMargin)

      intercept[Exception] {
        SpringBootDetector.detectSpringBootApp(dir)
      }
    finally
      os.remove.all(Path(dir))
  }

  test("error when directory not found") {
    intercept[Exception] {
      SpringBootDetector.detectSpringBootApp("/nonexistent/path")
    }
  }

  test("return Option[String] for build_tool when no pom.xml or build.gradle") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/App.java",
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |
          |@SpringBootApplication
          |public class App { }
          |""".stripMargin)

      val app = SpringBootDetector.detectSpringBootApp(dir)
      assert(app.build_tool == None)
    finally
      os.remove.all(Path(dir))
  }

  // ===== Spring Bean Parsing Tests =====

  test("detect @Service stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserService.java",
        """package com.example;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.nonEmpty)
      assert(beans.exists(b => b.name == "UserService" && b.stereotype == "@Service"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect @Repository stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserRepository.java",
        """package com.example;
          |import org.springframework.stereotype.Repository;
          |
          |@Repository
          |public class UserRepository {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.stereotype == "@Repository"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect @Component stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/MyComponent.java",
        """package com.example;
          |import org.springframework.stereotype.Component;
          |
          |@Component
          |public class MyComponent {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.stereotype == "@Component"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect @Controller stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserController.java",
        """package com.example;
          |import org.springframework.stereotype.Controller;
          |
          |@Controller
          |public class UserController {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.stereotype == "@Controller"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect @RestController stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/ApiController.java",
        """package com.example;
          |import org.springframework.web.bind.annotation.RestController;
          |
          |@RestController
          |public class ApiController {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.stereotype == "@RestController"))
    finally
      os.remove.all(Path(dir))
  }

  test("detect @Configuration stereotype") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/AppConfig.java",
        """package com.example;
          |import org.springframework.context.annotation.Configuration;
          |
          |@Configuration
          |public class AppConfig {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.stereotype == "@Configuration"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract @Autowired fields") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserService.java",
        """package com.example;
          |import org.springframework.beans.factory.annotation.Autowired;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |  @Autowired
          |  private UserRepository repository;
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      val userService = beans.find(b => b.name == "UserService")
      assert(userService.isDefined)
      assert(userService.get.injected_dependencies.nonEmpty)
      assert(userService.get.injected_dependencies.exists(d => d.field_name == "repository"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract @Inject fields") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/MyService.java",
        """package com.example;
          |import javax.inject.Inject;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class MyService {
          |  @Inject
          |  private SomeBean bean;
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      val myService = beans.find(b => b.name == "MyService")
      assert(myService.isDefined)
      assert(myService.get.injected_dependencies.exists(d => d.field_name == "bean"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract field type from @Autowired field") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserService.java",
        """package com.example;
          |import org.springframework.beans.factory.annotation.Autowired;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |  @Autowired
          |  private UserRepository userRepository;
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      val userService = beans.find(b => b.name == "UserService")
      assert(userService.isDefined)
      val dep = userService.get.injected_dependencies.find(d => d.field_name == "userRepository")
      assert(dep.isDefined)
      assert(dep.get.field_type == "UserRepository")
    finally
      os.remove.all(Path(dir))
  }

  test("return empty list when no beans found") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/Util.java",
        """package com.example;
          |
          |public class Util {
          |  public static void log(String msg) { }
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  test("extract fully-qualified class name") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/service/UserService.java",
        """package com.example.service;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.class_name == "com.example.service.UserService"))
    finally
      os.remove.all(Path(dir))
  }

  test("extract line number for bean class") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/UserService.java",
        """package com.example;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      val userService = beans.find(b => b.name == "UserService")
      assert(userService.isDefined)
      assert(userService.get.line_number == 4)
    finally
      os.remove.all(Path(dir))
  }

  test("handle multiple beans in single file") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/Services.java",
        """package com.example;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class UserService {
          |}
          |
          |@Service
          |public class ProductService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.count(b => b.stereotype == "@Service") >= 2)
    finally
      os.remove.all(Path(dir))
  }

  test("scan src/test/java for beans") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/test/java/com/example/TestService.java",
        """package com.example;
          |import org.springframework.stereotype.Service;
          |
          |@Service
          |public class TestService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.name == "TestService"))
    finally
      os.remove.all(Path(dir))
  }

  test("handle generics in field type") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/CollectionService.java",
        """package com.example;
          |import org.springframework.beans.factory.annotation.Autowired;
          |import org.springframework.stereotype.Service;
          |import java.util.List;
          |
          |@Service
          |public class CollectionService {
          |  @Autowired
          |  private List<String> items;
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      val service = beans.find(b => b.name == "CollectionService")
      assert(service.isDefined)
      val dep = service.get.injected_dependencies.find(d => d.field_name == "items")
      assert(dep.isDefined)
    finally
      os.remove.all(Path(dir))
  }

  test("skip annotations in comments") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/java/com/example/Util.java",
        """package com.example;
          |// @Service
          |public class Util {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(!beans.exists(b => b.name == "Util"))
    finally
      os.remove.all(Path(dir))
  }

  test("handle Kotlin files") {
    val dir = createTempDir()
    try
      writeFile(dir, "src/main/kotlin/com/example/KotlinService.kt",
        """package com.example
          |import org.springframework.stereotype.Service
          |
          |@Service
          |class KotlinService {
          |}
          |""".stripMargin)

      val beans = BeanGraphAnalyzer.parseSpringBeans(dir)
      assert(beans.exists(b => b.name == "KotlinService"))
    finally
      os.remove.all(Path(dir))
  }
