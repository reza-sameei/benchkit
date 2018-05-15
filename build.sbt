
organization := "me.samei.xtool"

name := "bechkit.v1"

version := "0.0-SNAPSHOT"

scalaVersion := "2.12.6"

crossScalaVersions := Seq("2.11.12", "2.12.6", "2.13.0-M3")

val akkaOrg = "com.typesafe.akka"

val akkaVersion = "2.5.12"

libraryDependencies ++= Seq(
    "akka-actor"
).map { m => akkaOrg %% m % akkaVersion }

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"

libraryDependencies += akkaOrg %% "akka-testkit" % akkaVersion % Test

