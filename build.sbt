
lazy val prs = project.in(file("."))

enablePlugins(JavaAppPackaging)

version := "0.0.2"

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
  "com.typesafe.play" %% "play-ws" % "2.5.3" excludeAll (
    ExclusionRule(organization = "oauth.signpost")
  ),
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "com.lihaoyi" %% "fansi" % "0.1.3",
  "com.iheart" %% "ficus" % "1.2.6"
)


GithubRelease.repo := "joprice/prs"

//GithubRelease.draft := true

GithubRelease.releaseAssets := Seq((packageBin in Universal).value)

