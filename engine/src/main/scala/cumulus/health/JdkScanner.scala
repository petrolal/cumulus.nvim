package cumulus.health

import cumulus.protocol.JdkInfo
import os.Path
import scala.util.Try
import scala.sys.process._

/**
 * Scans for available JDK installations on the system.
 */
object JdkScanner:

  /**
   * Scan all standard JDK installation paths and return detected Java versions.
   * Checks:
   * - $JAVA_HOME environment variable
   * - /usr/lib/jvm/ (Linux)
   * - ~/.sdkman/candidates/java/ (SDKman)
   * - /Library/Java/JavaVirtualMachines/ (macOS)
   */
  def scanJdkInstallations(): Seq[JdkInfo] =
    val jdks = scala.collection.mutable.ListBuffer[JdkInfo]()

    // 1. Check $JAVA_HOME
    sys.env.get("JAVA_HOME").foreach { home =>
      val homeInfo = detectJdkInfo(home)
      homeInfo.foreach(jdks += _)
    }

    // 2. Scan /usr/lib/jvm/ (Linux)
    val libJvm = os.Path("/usr/lib/jvm")
    if os.exists(libJvm) && os.isDir(libJvm) then
      try
        val entries = os.list(libJvm).filter(p => os.isDir(p))
        entries.foreach { entry =>
          val info = detectJdkInfo(entry.toString)
          info.foreach { i =>
            if !jdks.exists(_.path == i.path) then jdks += i
          }
        }
      catch
        case _: Exception => ()

    // 3. Scan ~/.sdkman/candidates/java/ (SDKman)
    val sdkmanJava = os.Path(sys.env.getOrElse("HOME", "/root")) / ".sdkman" / "candidates" / "java"
    if os.exists(sdkmanJava) && os.isDir(sdkmanJava) then
      try
        val entries = os.list(sdkmanJava).filter(p => os.isDir(p))
        entries.foreach { entry =>
          val info = detectJdkInfo(entry.toString)
          info.foreach { i =>
            if !jdks.exists(_.path == i.path) then jdks += i
          }
        }
      catch
        case _: Exception => ()

    // 4. Scan /Library/Java/JavaVirtualMachines/ (macOS)
    val javaVMs = os.Path("/Library/Java/JavaVirtualMachines")
    if os.exists(javaVMs) && os.isDir(javaVMs) then
      try
        val entries = os.list(javaVMs).filter(p => os.isDir(p))
        entries.foreach { entry =>
          val homePath = entry / "Contents" / "Home"
          if os.exists(homePath) && os.isDir(homePath) then
            val info = detectJdkInfo(homePath.toString)
            info.foreach { i =>
              if !jdks.exists(_.path == i.path) then jdks += i
            }
        }
      catch
        case _: Exception => ()

    // Mark the highest LTS version (or latest) as active
    if jdks.nonEmpty then
      val sortedByVersion = jdks.sortBy { jdk =>
        try
          val versionParts = jdk.version.split("""\.""").take(2).map(_.toInt)
          if versionParts.length == 2 then
            versionParts(0) * 1000 + versionParts(1)
          else
            0
        catch
          case _: Exception => 0
      }.reverse

      val ltsVersions = Seq(8, 11, 17, 21) // Known LTS versions
      val activeLts = sortedByVersion.find { jdk =>
        ltsVersions.exists(lts => jdk.version.startsWith(lts.toString))
      }

      activeLts match
        case Some(active) =>
          jdks.clear()
          jdks ++= sortedByVersion.map { j =>
            if j.path == active.path then j.copy(active = true) else j.copy(active = false)
          }
        case None =>
          // No LTS found, mark the latest as active
          if sortedByVersion.nonEmpty then
            jdks.clear()
            jdks ++= sortedByVersion.zipWithIndex.map { case (j, idx) =>
              j.copy(active = idx == 0)
            }

    jdks.toSeq

  /**
   * Detect JDK information from a given path.
   * Tries to extract version from release file or running java -version.
   */
  private def detectJdkInfo(path: String): Option[JdkInfo] =
    try
      val javaPath = os.Path(path)
      if !os.exists(javaPath) || !os.isDir(javaPath) then return None

      // Try to read version from 'release' file
      val releaseFile = javaPath / "release"
      val version = if os.exists(releaseFile) && os.isFile(releaseFile) then
        try
          val content = os.read(releaseFile)
          val pattern = """JAVA_VERSION="([\d.]+)""".r
          pattern.findFirstMatchIn(content).map(_.group(1))
            .orElse {
              val altPattern = """JAVA_VERSION="(\d+)""".r
              altPattern.findFirstMatchIn(content).map(_.group(1))
            }
        catch
          case _: Exception => None
      else
        None

      // If no release file, try running java -version
      val fallbackVersion = version.orElse {
        try
          val javaBin = javaPath / "bin" / "java"
          if os.exists(javaBin) && os.isFile(javaBin) then
            val cmd = javaBin.toString + " -version"
            val output = Try {
              cmd.!!
            }.getOrElse("")

            // Parse version from output like "openjdk version \"21.0.1\" 2023-10-17"
            val pattern1 = """version "([0-9.]+)""".r
            val pattern2 = """java version "([0-9.]+)""".r
            pattern1.findFirstMatchIn(output).map(_.group(1))
              .orElse(pattern2.findFirstMatchIn(output).map(_.group(1)))
          else
            None
        catch
          case _: Exception => None
      }

      // Extract version from directory name as last resort
      val dirVersion = fallbackVersion.orElse {
        val dirName = os.Path(path).last
        val pattern = """.*(?:java|jdk)[_-]?(?:openjdk[_-])?(\d+(?:\.\d+)*).*""".r
        pattern.findFirstMatchIn(dirName).map(_.group(1))
          .orElse {
            val altPattern = """(\d+)""".r
            altPattern.findFirstMatchIn(dirName).map(_.group(1))
          }
      }

      dirVersion.map(v => JdkInfo(version = v, path = path, active = false))
    catch
      case _: Exception => None

  /**
   * Detect the currently active JDK (from $JAVA_HOME or java command).
   */
  def detectActiveJdk(): Option[JdkInfo] =
    sys.env.get("JAVA_HOME").flatMap { home =>
      detectJdkInfo(home)
    }.orElse {
      try
        val output = "java -version".!!
        val pattern = """version "([0-9.]+)""".r
        pattern.findFirstMatchIn(output).map { m =>
          JdkInfo(version = m.group(1), path = "unknown", active = true)
        }
      catch
        case _: Exception => None
    }
