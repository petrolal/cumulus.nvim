// GraalVM native-image plugin (resolved via precedent in ~/cumulus.dotfiles)
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.5.0")

// Build-time metadata injection
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")

// CI release and Sonatype publishing
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.9.2")

// Assembly plugin for creating fat JARs
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")
