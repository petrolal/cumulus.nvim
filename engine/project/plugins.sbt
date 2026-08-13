// GraalVM native-image packaging plugin (unavailable in standard repositories)
// The spec references sbt-native-packager 1.9.16, but this version and others
// (1.9.9, 1.10.0, 1.10.1) do not exist in Maven Central or sbt plugin repos.
// Native image support must be deferred or plugin repository/coordinates must be clarified.
// addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.9.16")

// Build-time metadata injection
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
