sourceDirectory := file("dummy source directory")

scalaVersionSettings

// When bumping to 1.14.1, remember to set mimaPreviousArtifacts to 1.14.0
lazy val versionNumber = "1.14.0"

lazy val isRelease = false

lazy val travisCommit = Option(System.getenv().get("TRAVIS_COMMIT"))

lazy val scalaVersionSettings = Seq(
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.10.6", "2.11.11", "2.13.0-M2", scalaVersion.value)
)

lazy val sharedSettings = MimaSettings.settings ++ scalaVersionSettings ++ Seq(

  name := "scalacheck",

  version := {
    val suffix =
      if (isRelease) ""
      else travisCommit.map("-" + _.take(7)).getOrElse("") + "-SNAPSHOT"
    versionNumber + suffix
  },

  isSnapshot := !isRelease,

  organization := "org.scalacheck",

  licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),

  homepage := Some(url("http://www.scalacheck.org")),

  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username, password
  )).toSeq,

  unmanagedSourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "src" / "main" / "scala",

  unmanagedSourceDirectories in Test += (baseDirectory in LocalRootProject).value / "src" / "test" / "scala",

  resolvers += "sonatype" at "https://oss.sonatype.org/content/repositories/releases",

  javacOptions += "-Xmx1024M",

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen") ++ {
    scalaBinaryVersion.value match {
      case "2.10" => Seq("-Xlint")
      case "2.11" => Seq("-Xlint", "-Ywarn-infer-any", "-Ywarn-unused-import")
      case _      => Seq("-Xlint:-unused", "-Ywarn-infer-any", "-Ywarn-unused:imports,-patvars,-implicits,-locals,-privates,-params")
    }
  },

  // HACK: without these lines, the console is basically unusable,
  // since all imports are reported as being unused (and then become
  // fatal errors).
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,

  // don't use fatal warnings in tests
  scalacOptions in Test ~= (_ filterNot (_ == "-Xfatal-warnings")),

  //mimaPreviousArtifacts := (
  //  if (CrossVersion isScalaApiCompatible scalaVersion.value)
  //    Set("org.scalacheck" %%% "scalacheck" % "1.14.0")
  //  else
  //    Set.empty
  //),

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    val (name, path) = if (isSnapshot.value) ("snapshots", "content/repositories/snapshots")
                       else ("releases", "service/local/staging/deploy/maven2")
    Some(name at nexus + path)
  },

  publishMavenStyle := true,

  // Travis should only publish snapshots
  publishArtifact := !(isRelease && travisCommit.isDefined),

  publishArtifact in Test := false,

  pomIncludeRepository := { _ => false },

  pomExtra := {
    <scm>
      <url>https://github.com/rickynils/scalacheck</url>
      <connection>scm:git:git@github.com:rickynils/scalacheck.git</connection>
    </scm>
    <developers>
      <developer>
        <id>rickynils</id>
        <name>Rickard Nilsson</name>
      </developer>
    </developers>
  }
)

lazy val js = project.in(file("js"))
  .settings(sharedSettings: _*)
  .settings(
    scalaJSStage in Global := FastOptStage,
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion,

    // When using a nightly build of Scala (i.e when scalaVersion has a "-bin-.." suffix)
    // Replace the scalajs-compiler compiler plugin injected by sbt-scalajs
    // With the one built for the _previous_ version of Scala
    //
    // Example:
    // When building with Scala 2.12.4-bin-38628d1, which is a nightly build for future Scala 2.12.4
    // Replace scalajs-compiler_2.12.4-bin-38628d1 (which doesn't exist)
    // with scalajs-compiler_2.12.3
    libraryDependencies := {
      /** If scalaVersion has a "-bin-.." suffix, return the previous version.
       *
       *  | scalaVersion       | crossSv        | baseSv         | prevSv         |
       *  |--------------------+----------------+----------------+----------------|
       *  | 2.12.4             | Some("2.12.4") | None           | None           |
       *  | 2.12.4-bin-38628d1 | Some("2.12.4") | Some("2.12.4") | Some("2.12.3") |
       */
      def prevScalaVersion(scalaVersion: String, scalaBinaryVersion: String): Option[String] = {
        val crossVersion = CrossVersion(CrossVersion.patch, scalaVersion, scalaBinaryVersion)
        val crossSv = crossVersion map (fn => fn("") stripPrefix "_")
        val baseSv = crossSv filter (_ != scalaVersion)
        val prevSv = baseSv collect {
          case VersionNumber(Seq(x, y, z), Seq(), Seq()) if z > 0 => s"$x.$y.${z - 1}"
        }
        prevSv
      }

      val prevSv = prevScalaVersion(scalaVersion.value, scalaBinaryVersion.value)

      val libDeps = libraryDependencies.value
      prevSv match {
        case None         => libDeps
        case Some(prevSv) =>
          val scalaJsCompiler =
            compilerPlugin("org.scala-js" % s"scalajs-compiler_$prevSv" % scalaJSVersion)
          libDeps map (m => if (m.name == "scalajs-compiler") scalaJsCompiler else m)
      }
    }
  )
  .enablePlugins(ScalaJSPlugin)

lazy val jvm = project.in(file("jvm"))
  .settings(sharedSettings: _*)
  .settings(
    libraryDependencies += "org.scala-sbt" %  "test-interface" % "1.0"
  )
