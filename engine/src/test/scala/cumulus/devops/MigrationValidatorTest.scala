package cumulus.devops

import munit.FunSuite
import os.Path

class MigrationValidatorTest extends FunSuite:

  test("valid versioned, repeatable and undo migrations return no issues") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-")
    try
      os.write(tempDir / "V1__init.sql", "CREATE TABLE users (id INT);")
      os.write(tempDir / "V2.1__add_column.sql", "ALTER TABLE users ADD name TEXT;")
      os.write(tempDir / "R__user_views.sql", "CREATE VIEW v_users AS SELECT * FROM users;")
      os.write(tempDir / "U1__undo_init.sql", "DROP TABLE users;")

      val issues = MigrationValidator.validateMigrations(tempDir)
      assertEquals(issues, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("duplicate Flyway migration versions flag ERROR") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-dup-")
    try
      os.write(tempDir / "V1__init.sql", "CREATE TABLE users (id INT);")
      os.write(tempDir / "V1__dup.sql", "CREATE TABLE accounts (id INT);")

      val issues = MigrationValidator.validateMigrations(tempDir)
      assertEquals(issues.length, 2)
      assert(issues.forall(_.severity == "ERROR"))
      assert(issues.forall(_.message.contains("Duplicate Flyway migration version: V1")))
      assertEquals(issues.map(_.file).sorted, Seq("V1__dup.sql", "V1__init.sql"))
    finally
      os.remove.all(tempDir)
  }

  test("invalid migration filenames flag WARN") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-invalid-")
    try
      os.write(tempDir / "bad_name.sql", "SELECT 1;")

      val issues = MigrationValidator.validateMigrations(tempDir)
      assertEquals(issues.length, 1)
      val issue = issues.head
      assertEquals(issue.file, "bad_name.sql")
      assertEquals(issue.severity, "WARN")
      assert(issue.message.contains("does not match standard Flyway convention"))
    finally
      os.remove.all(tempDir)
  }

  test("non-existent directory gracefully returns empty sequence") {
    val nonExistentPath = "/nonexistent/path/for/cumulus/tests"
    val response = MigrationValidator.validateMigrationsDir(nonExistentPath)
    assert(response.success)
    assertEquals(response.data, Some(Seq.empty))
  }

  test("empty directory returns empty sequence") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-empty-")
    try
      val response = MigrationValidator.validateMigrationsDir(tempDir.toString)
      assert(response.success)
      assertEquals(response.data, Some(Seq.empty))
    finally
      os.remove.all(tempDir)
  }

  test("detects normalized duplicate versions (V1 vs V01, V1_0 vs V1.0)") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-norm-")
    try
      os.write(tempDir / "V1__init.sql", "CREATE TABLE a (id INT);")
      os.write(tempDir / "V01__other.sql", "CREATE TABLE b (id INT);")
      os.write(tempDir / "V2_0__two.sql", "CREATE TABLE c (id INT);")
      os.write(tempDir / "V2.0__two_dup.sql", "CREATE TABLE d (id INT);")

      val issues = MigrationValidator.validateMigrations(tempDir)
      assertEquals(issues.length, 4)
      assert(issues.forall(_.severity == "ERROR"))
    finally
      os.remove.all(tempDir)
  }

  test("valid multi-part semver versions are accepted") {
    val tempDir = os.temp.dir(prefix = "cumulus-migrations-semver-")
    try
      os.write(tempDir / "V1.10.100__patch.sql", "SELECT 1;")
      os.write(tempDir / "U1.10.100__undo.sql", "SELECT 2;")

      val issues = MigrationValidator.validateMigrations(tempDir)
      assertEquals(issues, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

