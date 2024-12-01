import sbt.*
import sbtassembly.AssemblyPlugin.autoImport._

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"
val scalapbVersion = "0.11.17"

lazy val root = (project in file("."))
  .enablePlugins(ProtocPlugin)
  .settings(
    name := "CS-441-HW-3",

    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-lambda-java-events" % "3.12.0",
      "software.amazon.awssdk" % "bedrock" % "2.26.22",
      "software.amazon.awssdk" % "bedrockruntime" % "2.29.22",

      "com.thesamet.scalapb" %% "compilerplugin" % "0.11.15",
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.1",

      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.6",
      "com.typesafe.akka" %% "akka-stream" % "2.8.6",
      "com.typesafe.akka" %% "akka-http-testkit" % "10.5.3" % Test,
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.3",

      // AWS SDK v2 dependencies for Bedrock
      "software.amazon.awssdk" % "aws-core" % "2.25.30",

      // JSON processing (useful for API responses)
      "com.typesafe.play" %% "play-json" % "2.10.6",

      // Ollama
      "io.github.ollama4j" % "ollama4j" % "1.0.79",

      // Logging dependencies
      "org.slf4j" % "slf4j-api" % "2.0.16",
      "org.slf4j" % "slf4j-simple" % "2.0.16",
      "ch.qos.logback" % "logback-classic" % "1.5.6",

      // Configuration management
      "com.typesafe" % "config" % "1.4.3",

      // Scala testing
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalatestplus" %% "scalatestplus-mockito" % "1.0.0-M2" % Test,
      "org.mockito" %% "mockito-scala" % "1.17.37" % Test,

      "org.slf4j" % "slf4j-api" % "2.0.12",
      "ch.qos.logback" % "logback-classic" % "1.5.6" excludeAll
        ExclusionRule(organization = "org.slf4j", name = "slf4j-simple")
    ),

    resolvers ++= Seq(
      "Maven Central" at "https://repo1.maven.org/maven2/",
      "jitpack.io" at "https://jitpack.io"
    ),

    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),

    Compile / PB.protocVersion := "3.21.12",

    assembly / mainClass := Some("App.AkkaHttpServer"),

    // sbt-assembly settings for creating a fat jar
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) =>
        xs match {
          case "MANIFEST.MF" :: Nil => MergeStrategy.discard
          case "services" :: _ => MergeStrategy.concat
          case _ => MergeStrategy.discard
        }
      case "reference.conf" => MergeStrategy.concat
      case x if x.endsWith(".proto") => MergeStrategy.rename
      case _ => MergeStrategy.first
    }
  )
