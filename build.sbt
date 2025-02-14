import ResourceFilter.filterResources

ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version := "0.1.0"
ThisBuild / description := "A BungeeCord plugin for controlling access to data shared among downstream servers"

//region dependency config

resolvers ++= Seq(
  "bungeecord-repo" at "https://oss.sonatype.org/content/repositories/snapshots",
  "minecraft-libraries" at "https://libraries.minecraft.net",
)

val providedDependencies = Seq(
  "net.md-5" % "bungeecord-api" % "1.20-R0.2",
  "net.md-5" % "bungeecord-parent" % "1.20-R0.2",
  // no runtime
  "org.typelevel" %% "simulacrum" % "1.0.1"
).map(_ % "provided")

val testDependencies = Seq(
  "org.scalamock" %% "scalamock" % "6.1.1",
  "org.scalatest" %% "scalatest" % "3.2.19",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0",
).map(_ % "test")

val dependenciesToEmbed = Seq(
  "org.typelevel" %% "cats-core" % "2.13.0",
  "org.typelevel" %% "cats-effect" % "2.5.5",
  "io.github.rediscala" %% "rediscala" % "1.17.0",
)

// treat localDependencies as "provided" and do not shade them in the output Jar
assembly / assemblyExcludedJars := {
  (assembly / fullClasspath).value
    .filter { a =>
      def directoryContainsFile(directory: File, file: File) =
        file.absolutePath.startsWith(directory.absolutePath)

      directoryContainsFile(baseDirectory.value / "localDependencies", a.data)
    }
}

assembly / assemblyMergeStrategy := {
  case PathList(x) if x.endsWith(".conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
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

Compile / filteredResourceGenerator :=
  filterResources(
    filesToBeReplacedInResourceFolder,
    tokenReplacementMap.value,
    (Compile / resourceManaged).value, (Compile / resourceDirectory).value
  )

Compile / resourceGenerators += (Compile / filteredResourceGenerator)

Compile / unmanagedResources += baseDirectory.value / "LICENSE"

// exclude replaced files from copying of unmanagedResources
unmanagedResources / excludeFilter :=
  filesToBeReplacedInResourceFolder.foldLeft((unmanagedResources / excludeFilter).value)(_.||(_))

//endregion

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full)

lazy val root = (project in file("."))
  .settings(
    name := "BungeeSemaphore",
    assembly / assemblyOutputPath := baseDirectory.value / "target" / "build" / "BungeeSemaphore.jar",
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
