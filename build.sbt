name := "ssl-certificate-check"

version := "1.0"

scalaVersion := "2.12.1"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % "10.0.4",
  "com.typesafe.akka" %% "akka-http" % "10.0.4",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.4",
  "org.json4s" %% "json4s-native" % "3.5.0",
  "org.slf4j" % "slf4j-simple" % "1.7.24",
  "com.typesafe" % "config" % "1.3.0",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
