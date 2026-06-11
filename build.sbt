ThisBuild / organization := "io.akka.sbt"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))

lazy val `sbt-mima-bom` = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-mima-bom",
    libraryDependencies += "com.typesafe" %% "mima-core" % "1.1.4",
    scriptedLaunchOpts ++= Seq("-Xmx1024M", s"-Dplugin.version=${version.value}"),
    scriptedBufferLog := false)
