package cumulus.code

import munit.FunSuite
import java.nio.file.{Files, Paths}
import os.Path

class EndpointTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("endpoint-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    Files.write(filePath, content.getBytes("UTF-8"))
    filePath.toString

  private def createNestedFile(dir: String, path: String, content: String): String =
    val fullPath = Paths.get(dir, path)
    Files.createDirectories(fullPath.getParent)
    Files.write(fullPath, content.getBytes("UTF-8"))
    fullPath.toString

  // ===== Spring @GetMapping Tests =====

  test("EndpointScanner: Spring @GetMapping with path") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {
  @GetMapping("/users")
  public List<User> getUsers() {
    return null;
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/UserController.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      assert(endpoints.length >= 1)
      val endpoint = endpoints.find(e => e.path.contains("/users"))
      assert(endpoint.isDefined)
      assert(endpoint.get.http_method == "GET")
      assert(endpoint.get.path == "/api/users")
      assert(endpoint.get.handler_name == "getUsers")
    finally
      os.remove.all(Path(dir))
  }

  test("EndpointScanner: Spring @PostMapping") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
  @PostMapping("/users")
  public User createUser(User user) {
    return user;
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/UserController.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      val endpoint = endpoints.find(e => e.http_method == "POST")
      assert(endpoint.isDefined)
      assert(endpoint.get.path == "/users")
      assert(endpoint.get.handler_name == "createUser")
    finally
      os.remove.all(Path(dir))
  }

  test("EndpointScanner: Spring @PutMapping") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
  @PutMapping("/users/{id}")
  public User updateUser(Long id, User user) {
    return user;
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/UserController.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      val endpoint = endpoints.find(e => e.http_method == "PUT")
      assert(endpoint.isDefined)
      assert(endpoint.get.path == "/users/{id}")
    finally
      os.remove.all(Path(dir))
  }

  test("EndpointScanner: Spring @DeleteMapping") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
  @DeleteMapping("/users/{id}")
  public void deleteUser(Long id) {
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/UserController.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      val endpoint = endpoints.find(e => e.http_method == "DELETE")
      assert(endpoint.isDefined)
      assert(endpoint.get.path == "/users/{id}")
    finally
      os.remove.all(Path(dir))
  }

  test("EndpointScanner: Class-level @RequestMapping with method path") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example.api;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  @GetMapping
  public List<User> getAllUsers() {
    return null;
  }

  @GetMapping("/{id}")
  public User getUser(Long id) {
    return null;
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/api/UserController.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      assert(endpoints.length >= 2)
      val getAll = endpoints.find(e => e.handler_name == "getAllUsers")
      val getOne = endpoints.find(e => e.handler_name == "getUser")
      assert(getAll.isDefined)
      assert(getOne.isDefined)
      assert(getAll.get.path == "/api/v1/users")
      assert(getOne.get.path == "/api/v1/users/{id}")
    finally
      os.remove.all(Path(dir))
  }

  // ===== No Endpoints Found =====

  test("EndpointScanner: No endpoints found returns empty list") {
    val dir = createTempDir()
    try
      val javaContent = """package com.example;

public class Utility {
  public static String format(String s) {
    return s.toUpperCase();
  }
}"""
      createNestedFile(dir, "src/main/java/com/example/Utility.java", javaContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      assert(endpoints.isEmpty)
    finally
      os.remove.all(Path(dir))
  }

  // ===== ImportOptimizer Tests =====

  test("ImportOptimizer: Deduplicates duplicate imports") {
    val input = """import java.util.List;
import java.util.List;
import java.util.Set;"""
    val imports = ImportOptimizer.optimizeImports(input)
    assert(imports.length == 2)
    assert(imports.contains("import java.util.List;"))
    assert(imports.contains("import java.util.Set;"))
  }

  test("ImportOptimizer: Sorts imports lexically") {
    val input = """import java.util.Set;
import java.util.List;
import java.util.ArrayList;"""
    val imports = ImportOptimizer.optimizeImports(input)
    assert(imports.length == 3)
    assert(imports(0) == "import java.util.ArrayList;")
    assert(imports(1) == "import java.util.List;")
    assert(imports(2) == "import java.util.Set;")
  }

  test("ImportOptimizer: Preserves wildcard imports") {
    val input = """import java.util.*;
import java.util.List;"""
    val imports = ImportOptimizer.optimizeImports(input)
    assert(imports.length == 2)
    assert(imports.exists(_.contains("java.util.*")))
  }

  test("ImportOptimizer: Handles mixed input with non-import lines") {
    val input = """package com.example;

import java.util.List;
import java.util.List;
import java.util.Set;

public class MyClass {
}"""
    val imports = ImportOptimizer.optimizeImports(input)
    // Should return only the deduplicated imports
    assert(imports.length == 2)
  }

  // ===== JavaHeaderGenerator Tests =====

  test("JavaHeaderGenerator: Infers package from standard src path") {
    val dir = createTempDir()
    try
      val filePath = createNestedFile(dir, "src/main/java/com/example/MyClass.java", "")
      val header = JavaHeaderGenerator.generateHeader(filePath)
      assert(header.package_name == "com.example")
      assert(header.class_name == "MyClass")
      assert(header.class_declaration == "public class MyClass { }")
    finally
      os.remove.all(Path(dir))
  }

  test("JavaHeaderGenerator: Infers package from nested package") {
    val dir = createTempDir()
    try
      val filePath = createNestedFile(dir, "src/main/java/com/example/util/Helper.java", "")
      val header = JavaHeaderGenerator.generateHeader(filePath)
      assert(header.package_name == "com.example.util")
      assert(header.class_name == "Helper")
    finally
      os.remove.all(Path(dir))
  }

  test("JavaHeaderGenerator: Handles Kotlin files") {
    val dir = createTempDir()
    try
      val filePath = createNestedFile(dir, "src/main/kotlin/com/example/MyClass.kt", "")
      val header = JavaHeaderGenerator.generateHeader(filePath)
      assert(header.package_name == "com.example")
      assert(header.class_name == "MyClass")
    finally
      os.remove.all(Path(dir))
  }

  test("JavaHeaderGenerator: Handles test source paths") {
    val dir = createTempDir()
    try
      val filePath = createNestedFile(dir, "src/test/java/com/example/MyClassTest.java", "")
      val header = JavaHeaderGenerator.generateHeader(filePath)
      assert(header.package_name == "com.example")
      assert(header.class_name == "MyClassTest")
    finally
      os.remove.all(Path(dir))
  }

  test("JavaHeaderGenerator: Generates correct class declaration for non-existent/new files") {
    val header = JavaHeaderGenerator.generateHeader("/workspace/src/main/java/com/example/NewService.java")
    assert(header.package_name == "com.example")
    assert(header.class_name == "NewService")
    assert(header.class_declaration == "public class NewService { }")
  }

  test("EndpointScanner: JAX-RS @Path and HTTP annotations") {
    val dir = createTempDir()
    try
      val jaxrsContent = """package com.example.api;
import javax.ws.rs.*;

@Path("/users")
public class UserResource {
  @GET
  @Path("/{id}")
  public User getUser() { return null; }

  @POST
  public void createUser() { }
}"""
      createNestedFile(dir, "src/main/java/com/example/api/UserResource.java", jaxrsContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      assert(endpoints.length == 2)
      assert(endpoints.exists(e => e.http_method == "GET" && e.path == "/users/{id}" && e.handler_name == "getUser"))
      assert(endpoints.exists(e => e.http_method == "POST" && e.path == "/users" && e.handler_name == "createUser"))
    finally
      os.remove.all(Path(dir))
  }

  test("EndpointScanner: Kotlin Spring @RestController and fun handler") {
    val dir = createTempDir()
    try
      val ktContent = """package com.example.demo
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class HelloController {
  @GetMapping("/hello")
  fun sayHello(): String = "Hello"
}"""
      createNestedFile(dir, "src/main/kotlin/com/example/demo/HelloController.kt", ktContent)
      val endpoints = EndpointScanner.scanEndpoints(dir)
      assert(endpoints.length == 1)
      assert(endpoints.head.http_method == "GET")
      assert(endpoints.head.path == "/api/v1/hello")
      assert(endpoints.head.handler_name == "sayHello")
    finally
      os.remove.all(Path(dir))
  }

// ===== FileScaffolder Tests =====

class FileScaffolderTest extends FunSuite:

  test("scaffold: Java class default template") {
    val res = FileScaffolder.scaffold("src/main/java/com/example/service/UserService.java", None)
    assert(res.success)
    assert(res.data.isDefined)
    val data = res.data.get
    assertEquals(data.package_name, "com.example.service")
    assertEquals(data.type_name, "UserService")
    assertEquals(data.template, "class")
    assertEquals(data.created, false)
    assertEquals(data.content, "package com.example.service;\n\npublic class UserService {\n}\n")
  }

  test("scaffold: Java interface, record, and enum templates") {
    val resInterface = FileScaffolder.scaffold("src/main/java/com/example/repo/UserRepo.java", Some("interface"))
    assert(resInterface.success)
    assertEquals(resInterface.data.get.content, "package com.example.repo;\n\npublic interface UserRepo {\n}\n")

    val resRecord = FileScaffolder.scaffold("src/main/java/com/example/dto/UserDto.java", Some("record"))
    assert(resRecord.success)
    assertEquals(resRecord.data.get.content, "package com.example.dto;\n\npublic record UserDto(\n) {\n}\n")

    val resEnum = FileScaffolder.scaffold("src/main/java/com/example/model/Role.java", Some("enum"))
    assert(resEnum.success)
    assertEquals(resEnum.data.get.content, "package com.example.model;\n\npublic enum Role {\n}\n")
  }

  test("scaffold: Kotlin class, data class, interface, object, enum") {
    val resClass = FileScaffolder.scaffold("src/main/kotlin/com/example/model/User.kt", None)
    assert(resClass.success)
    assertEquals(resClass.data.get.content, "package com.example.model\n\nclass User {\n}\n")

    val resDataClass = FileScaffolder.scaffold("src/main/kotlin/com/example/model/User.kt", Some("data-class"))
    assert(resDataClass.success)
    assertEquals(resDataClass.data.get.content, "package com.example.model\n\ndata class User(\n    val id: String\n)\n")

    val resObj = FileScaffolder.scaffold("src/main/kotlin/com/example/util/Constants.kt", Some("object"))
    assert(resObj.success)
    assertEquals(resObj.data.get.content, "package com.example.util\n\nobject Constants {\n}\n")

    val resEnum = FileScaffolder.scaffold("src/main/kotlin/com/example/model/Status.kt", Some("enum"))
    assert(resEnum.success)
    assertEquals(resEnum.data.get.content, "package com.example.model\n\nenum class Status {\n}\n")
  }

  test("scaffold: Scala class, trait, object, enum, case-class") {
    val resClass = FileScaffolder.scaffold("src/main/scala/com/example/Runner.scala", None)
    assert(resClass.success)
    assertEquals(resClass.data.get.content, "package com.example\n\nclass Runner {\n}\n")

    val resTrait = FileScaffolder.scaffold("src/main/scala/com/example/Service.scala", Some("trait"))
    assert(resTrait.success)
    assertEquals(resTrait.data.get.content, "package com.example\n\ntrait Service {\n}\n")

    val resObj = FileScaffolder.scaffold("src/main/scala/com/example/Main.scala", Some("object"))
    assert(resObj.success)
    assertEquals(resObj.data.get.content, "package com.example\n\nobject Main {\n}\n")

    val resEnum = FileScaffolder.scaffold("src/main/scala/com/example/Color.scala", Some("enum"))
    assert(resEnum.success)
    assertEquals(resEnum.data.get.content, "package com.example\n\nenum Color {\n}\n")
  }

  test("scaffold: Multiplatform and varied source roots") {
    val resKmp = FileScaffolder.scaffold("shared/src/commonMain/kotlin/com/example/kmp/CommonModel.kt", None)
    assert(resKmp.success)
    assertEquals(resKmp.data.get.package_name, "com.example.kmp")

    val resJvm = FileScaffolder.scaffold("shared/src/jvmMain/kotlin/com/example/jvm/JvmModel.kt", None)
    assert(resJvm.success)
    assertEquals(resJvm.data.get.package_name, "com.example.jvm")

    val resTest = FileScaffolder.scaffold("src/test/java/com/example/service/UserServiceTest.java", None)
    assert(resTest.success)
    assertEquals(resTest.data.get.package_name, "com.example.service")
  }

  test("scaffold: File outside source root returns empty package") {
    val res = FileScaffolder.scaffold("/opt/code/User.java", None)
    assert(res.success)
    assertEquals(res.data.get.package_name, "")
    assertEquals(res.data.get.content, "public class User {\n}\n")
  }

  test("scaffold: Missing file returns INVALID_INPUT error envelope") {
    val resNull = FileScaffolder.scaffold(null, None)
    assert(!resNull.success)
    assertEquals(resNull.error_code, Some("INVALID_INPUT"))

    val resEmpty = FileScaffolder.scaffold("   ", None)
    assert(!resEmpty.success)
    assertEquals(resEmpty.error_code, Some("INVALID_INPUT"))
  }

  test("scaffold: --create flag creates file and parent directories") {
    val tmpDir = os.temp.dir(prefix = "scaffold-test")
    try
      val targetFile = tmpDir / "src" / "main" / "java" / "com" / "example" / "App.java"
      val res = FileScaffolder.scaffold(targetFile.toString, Some("class"), create = true)
      assert(res.success)
      assert(res.data.isDefined)
      assertEquals(res.data.get.created, true)
      assert(os.exists(targetFile))
      val fileContent = os.read(targetFile)
      assertEquals(fileContent, "package com.example;\n\npublic class App {\n}\n")

      // Attempting to overwrite non-empty file fails
      val resOverwrite = FileScaffolder.scaffold(targetFile.toString, Some("class"), create = true)
      assert(!resOverwrite.success)
      assertEquals(resOverwrite.error_code, Some("INVALID_INPUT"))
    finally
      os.remove.all(tmpDir)
  }


