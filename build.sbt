import ResourceFilter.filterResources

ThisBuild / scalaVersion := "2.13.6"
ThisBuild / version := "0.1.0"
ThisBuild / description := "A BungeeCord plugin for controlling access to data shared among downstream servers"

//region dependency config

resolvers ++= Seq(
  "bungeecord-repo" at "https://oss.sonatype.org/content/repositories/snapshots",
)

val providedDependencies = Seq(
  "net.md-5" % "bungeecord-api" % "1.12-SNAPSHOT",
  "net.md-5" % "bungeecord-parent" % "1.12-SNAPSHOT",
  // no runtime
  "org.typelevel" %% "simulacrum" % "1.0.1"
).map(_ % "provided")

val testDependencies = Seq(
  "org.scalamock" %% "scalamock" % "4.4.0",
  "org.scalatest" %% "scalatest" % "3.2.17",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0",
).map(_ % "test")

val dependenciesToEmbed = Seq(
  "org.typelevel" %% "cats-core" % "2.3.0",
  "org.typelevel" %% "cats-effect" % "2.3.0",
  "com.github.etaty" %% "rediscala" % "1.9.0",
)

// treat localDependencies as "provided" and do not shade them in the output Jar
assemblyExcludedJars in assembly := {
  (fullClasspath in assembly).value
    .filter { a =>
      def directoryContainsFile(directory: File, file: File) =
        file.absolutePath.startsWith(directory.absolutePath)

      directoryContainsFile(baseDirectory.value / "localDependencies", a.data)
    }
}

assemblyMergeStrategy in assembly := {
  case PathList(x) if x.endsWith(".conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

//endregion

//region settings for token replacements

val tokenReplacementMap = settingKey[Map[String, String]]("Map specifying what tokens should be replaced to")

tokenReplacementMap := Map(
  "name" -> name.value,
  "version" -> version.value
)

val filesToBeReplacedInResourceFolder = Seq("plugin.yml")

val filteredResourceGenerator = taskKey[Seq[File]]("Resource generator to filter resources")

filteredResourceGenerator in Compile :=
  filterResources(
    filesToBeReplacedInResourceFolder,
    tokenReplacementMap.value,
    (resourceManaged in Compile).value, (resourceDirectory in Compile).value
  )

resourceGenerators in Compile += (filteredResourceGenerator in Compile)

unmanagedResources in Compile += baseDirectory.value / "LICENSE"

// exclude replaced files from copying of unmanagedResources
excludeFilter in unmanagedResources :=
  filesToBeReplacedInResourceFolder.foldLeft((excludeFilter in unmanagedResources).value)(_.||(_))

//endregion

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full)

lazy val root = (project in file("."))
  .settings(
    name := "BungeeSemaphore",
    assemblyOutputPath in assembly := baseDirectory.value / "target" / "build" / "BungeeSemaphore.jar",
    libraryDependencies := providedDependencies ++ testDependencies ++ dependenciesToEmbed,
    unmanagedBase := baseDirectory.value / "localDependencies",
    unmanagedBase := baseDirectory.value / "localDependencies",
    scalacOptions ++= Seq(
      "-encoding", "utf8",
      "-unchecked",
      "-language:higherKinds",
      "-deprecation",
      "-Ypatmat-exhaust-depth", "320",
      "-Ymacro-annotations",
    ),
    javacOptions ++= Seq("-encoding", "utf8")
  )
