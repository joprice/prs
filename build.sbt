import sbtrelease.ReleaseStateTransformations._

lazy val prs = project.in(file("."))

enablePlugins(JavaAppPackaging)

scalaVersion := "2.11.8"

organization := "com.joprice"

mainClass in Compile := Some("prs.Main")

credentials ++= sys.env.get("ARCHIVA_PASSWORD").map { pass =>
  Seq(
    Credentials("Repository Archiva Managed internal Repository", "amparchiva.int.ihrdev.com", "amp", pass),
    Credentials("Repository Archiva Managed snapshots Repository", "amparchiva.int.ihrdev.com", "amp", pass)
  )
  }.getOrElse {
    Seq(
      Credentials(Path.userHome / ".ivy2" /".poweramp-snapshot-credentials"),
      Credentials(Path.userHome / ".ivy2" /".poweramp-release-credentials")
    )
  }

publishTo := {
  val baseUrl = "http://amparchiva.int.ihrdev.com/repository"
  def repo(name: String) = s"Archiva Managed $name Repository" at s"${baseUrl}/$name"
  Some(
    if (isSnapshot.value)
      repo("snapshots")
    else
      repo("internal")
    )
}

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.5.12",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.lihaoyi" %% "fansi" % "0.1.3",
  "com.iheart" %% "ficus" % "1.2.6",
  "org.scalaj" %% "scalaj-http" % "2.3.0"
)

lazy val checkVersionNotes = taskKey[Unit]("Checks that the notes for the next version are present to avoid build failures.")

checkVersionNotes := {
  val notesFile = GithubRelease.notesDir.value / (version.value.stripSuffix("-SNAPSHOT") +".markdown")
  val notesPath = notesFile.relativeTo(baseDirectory.value).getOrElse(notesFile)
  if (!notesPath.exists) {
    sys.error(s"Missing notes file $notesPath")
  }
}

GithubRelease.repo := "joprice/prs"

GithubRelease.releaseAssets := Seq((packageBin in Universal).value)

//GithubRelease.draft := true

releaseProcess := Seq[ReleaseStep](
  releaseStepTask(checkVersionNotes),
  releaseStepTask(checkGithubCredentials),
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  releaseStepTask(releaseOnGithub),
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

