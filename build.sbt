enablePlugins(JavaAppPackaging)
name := "akka-chord-simulator"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.6.8"
lazy val AkkaHttpVersion = "10.2.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
   "com.typesafe.akka" % "akka-http_2.13"  % AkkaHttpVersion, 
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe" % "config" % "1.4.0",
  "com.typesafe.akka" %% "akka-cluster-typed"         % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed"% akkaVersion,
 )
TaskKey[Unit]("someTask") := (runMain in Compile).toTask(" com.example.Main").value
