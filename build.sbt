import ResourceFilter.filterResources

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / version := "0.1"
ThisBuild / description := "A BungeeCord plugin for controlling access to data shared among downstream servers"

//region 依存設定

resolvers ++= Seq(
  "bungeecord-repo" at "https://oss.sonatype.org/content/repositories/snapshots",
)

val providedDependencies = Seq(
  "net.md-5" % "bungeecord-api" % "1.12-SNAPSHOT",
  // no runtime
  "org.typelevel" %% "simulacrum" % "1.0.0"
).map(_ % "provided")

val testDependencies = Seq(
  "org.scalamock" %% "scalamock" % "4.4.0",
  "org.scalatest" %% "scalatest" % "3.2.2",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0",
).map(_ % "test")

val dependenciesToEmbed = Seq(
  "org.typelevel" %% "cats-core" % "2.1.0",
  "org.typelevel" %% "cats-effect" % "2.1.0",
)

//endregion

//region トークン置換の設定

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

// トークン置換を行ったファイルをunmanagedResourcesのコピーから除外する
excludeFilter in unmanagedResources :=
  filesToBeReplacedInResourceFolder.foldLeft((excludeFilter in unmanagedResources).value)(_.||(_))

//endregion

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

lazy val root = (project in file("."))
  .settings(
    name := "BungeeSemaphore",
    assemblyOutputPath in assembly := baseDirectory.value / "target" / "build" / s"BungeeSemaphore-${version.value}.jar",
    libraryDependencies := providedDependencies ++ testDependencies ++ dependenciesToEmbed,
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