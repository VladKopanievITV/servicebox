import sbt.Keys.publishArtifact
import ReleaseTransformations._

val monocleVersion  = "2.1.0"
val doobieVersion   = "1.0.0-RC2"
val influxDbVersion = "2.9"

val readme     = "README.md"
val readmePath = file(".") / readme
val copyReadme =
  taskKey[File](s"Copy readme file to project root")

val Scala213               = "2.13.10"

val artifactoryCredentials = sys.env.get("CI") match {
  case Some(value) if value.toBoolean =>
    println("Credentials from envs.")
    Credentials(
      "Artifactory Realm",
      "itvrepos.jfrog.io",
      sys.env("ARTIFACTORY_USERNAME"),
      sys.env("ARTIFACTORY_PASSWORD")
    )
  case _ =>
    println("Local credentials.")
    val cred = Credentials(Path.userHome / ".ivy2" / ".credentials")
    println(cred)
    cred
}

val resolverSettings = Seq(
  credentials += artifactoryCredentials,
  resolvers ++= Seq(
    "AM Artifactory Realm" at "https://itvrepos.jfrog.io/itvrepos/am-scala-libs/"
  )
)


val baseSettings = Seq(
  organization := "com.itv",
  name := "servicebox",
  scalaVersion := Scala213,
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-Xfatal-warnings",
  ),
  libraryDependencies ++= Seq(
    "org.typelevel"              %% "cats-core"      % "2.9.0",
    "org.typelevel"              %% "cats-effect"    % "3.5.0",
    "org.typelevel"              %% "kittens"        % "3.0.0",
    "org.scalatest"              %% "scalatest"      % "3.2.15" % "test",
    "com.github.julien-truffaut" %% "monocle-core"   % monocleVersion,
    "com.github.julien-truffaut" %% "monocle-macro"  % monocleVersion,
    "ch.qos.logback"             % "logback-classic" % "1.4.6",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.5",
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full)
  )
)

val artefactSettings = baseSettings ++ resolverSettings ++ Seq(
  publishArtifact := true,
  publishTo := Some("Artifactory Realm" at "https://itvrepos.jfrog.io/itvrepos/am-scala-libs/")
)

def withDeps(p: Project)(dep: Project*): Project = p.dependsOn(dep.map(_ % "compile->compile;test->test"): _*)

lazy val core = (project in file("core"))
  .settings(
    artefactSettings,
  )
  .settings(
    moduleName := "servicebox-core"
  )

lazy val docker = withDeps(
  (project in file("docker"))
    .settings(
      artefactSettings ++ Seq(
        moduleName := "servicebox-docker",
        libraryDependencies ++= Seq(
          "com.spotify" % "docker-client" % "8.16.0"
        )
      )))(core)

lazy val example = withDeps(
  (project in file("example"))
    .enablePlugins(MdocPlugin)
    .settings(baseSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.flywaydb" % "flyway-core"      % "4.2.0",
        "org.postgresql" % "postgresql" % "42.5.4",
        "org.tpolecat" %% "doobie-core"     % doobieVersion,
        "org.tpolecat" %% "doobie-postgres" % doobieVersion,
        "org.influxdb" % "influxdb-java"    % influxDbVersion
      ),
      mdocIn := baseDirectory.value / "src" / "main" / "mdoc",
      scalacOptions in Compile ~= {
        // https://github.com/scalameta/mdoc/issues/210
        _.filterNot(Set("-Xfatal-warnings"))
      },
      copyReadme := {
        val mdocDir = mdocOut.value
        val log     = streams.value.log

        log.info(s"Copying ${mdocDir / readme} to ${file(".") / readme}")

        IO.copyFile(
          mdocDir / readme,
          readmePath
        )
        readmePath
      }
    )))(core, docker)

lazy val root = (project in file("."))
  .aggregate(core, docker)
  .settings(artefactSettings)


ThisBuild / gitVersioningSnapshotLowerBound := "0.5.0"
ThisBuild / versionScheme := Some("early-semver")