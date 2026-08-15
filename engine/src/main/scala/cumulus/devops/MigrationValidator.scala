package cumulus.devops

import cumulus.protocol.CumulusResponse
import os.Path
import scala.collection.mutable.ListBuffer

object MigrationValidator:

  // Flyway migration file naming conventions
  // Versioned: V1__init.sql, V1.1__update.sql, U1__undo.sql, V1_1__update-table.sql
  val VersionedPattern = """^(V|U)(\d+(?:[._]\d+)*)__([A-Za-z0-9_-]+)\.sql$""".r
  // Repeatable: R__views.sql
  val RepeatablePattern = """^R__([A-Za-z0-9_-]+)\.sql$""".r

  /**
   * Validates Flyway migrations in the given directory path.
   * Checks for duplicate version numbers (ERROR) and invalid naming conventions (WARN).
   */
  def validateMigrations(dirPath: Path): Seq[MigrationIssue] =
    if !os.exists(dirPath) || !os.isDir(dirPath) then
      return Seq.empty

    val files = try {
      os.list(dirPath).filter(os.isFile).sortBy(_.last)
    } catch {
      case _: Exception => return Seq.empty
    }

    val issues = ListBuffer[MigrationIssue]()
    val versionMap = collection.mutable.Map[String, ListBuffer[String]]()

    for file <- files do
      val filename = file.last
      if filename.endsWith(".sql") then
        filename match
          case VersionedPattern(prefix, version, _) =>
            val versionKey = s"$prefix$version"
            versionMap.getOrElseUpdate(versionKey, ListBuffer()) += filename
          case RepeatablePattern(_) =>
            // Repeatable migrations are valid and skip version uniqueness check
            ()
          case _ =>
            issues += MigrationIssue(
              file = filename,
              line = None,
              severity = "WARN",
              message = s"Migration filename does not match standard Flyway convention: $filename"
            )

    // Check for duplicate versions
    for (versionKey, fileList) <- versionMap if fileList.size > 1 do
      for filename <- fileList do
        issues += MigrationIssue(
          file = filename,
          line = None,
          severity = "ERROR",
          message = s"Duplicate Flyway migration version: $versionKey"
        )

    issues.toSeq.sortBy(i => (i.file, i.severity))

  /**
   * Validates Flyway migrations in the directory string path, returning a CumulusResponse envelope.
   */
  def validateMigrationsDir(dir: String): CumulusResponse[Seq[MigrationIssue]] =
    try
      val dirPathOpt = try {
        Some(if dir.startsWith("/") then Path(dir) else os.pwd / os.RelPath(dir))
      } catch {
        case _: Exception => None
      }

      dirPathOpt match
        case None =>
          // Gracefully return empty list for invalid paths
          CumulusResponse(
            success = true,
            data = Some(Seq.empty),
            error = None,
            error_code = None
          )
        case Some(dirPath) =>
          if !os.exists(dirPath) || !os.isDir(dirPath) then
            CumulusResponse(
              success = true,
              data = Some(Seq.empty),
              error = None,
              error_code = None
            )
          else
            val issues = validateMigrations(dirPath)
            CumulusResponse(
              success = true,
              data = Some(issues),
              error = None,
              error_code = None
            )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error validating migrations: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
