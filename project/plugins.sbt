addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")

// To generate Protobuf classes
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.15"
