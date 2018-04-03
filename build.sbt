name := "medbase"

version := "1.0.0"

organization := "edu.utdallas.hltri"

publishTo := sonatypePublishTo.value

// enable publishing to maven
publishMavenStyle := true

// do not append scala version to the generated artifacts
crossPaths := false

// do not add scala libraries as a dependency!
autoScalaLibrary := false

fork := false

// add the apache resolver (for Jena)
resolvers += "apache" at "https://repository.apache.org/content/repositories/releases/"

libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test"

//libraryDependencies += "com.google.guava" % "guava" % "14.0.+"

// used by medline's api
// libraryDependencies += "javax" % "javaee-api" % "7.0" % "provided"
// https://mvnrepository.com/artifact/javax.xml.ws/jaxws-api
libraryDependencies += "javax.xml.ws" % "jaxws-api" % "2.2.11"

// https://mvnrepository.com/artifact/com.sun.xml.ws/jaxws-rt
libraryDependencies += "com.sun.xml.ws" % "jaxws-rt" % "2.2.10" pomOnly()

// https://mvnrepository.com/artifact/com.sun.xml.ws/jaxws-tools
libraryDependencies += "com.sun.xml.ws" % "jaxws-tools" % "2.2.10" pomOnly()

// hltri libraries
libraryDependencies ++= Seq(
  "edu.utdallas.hltri" % "hltri-util" % "1.0.1",
  "edu.utdallas.hltri" % "scribe" % "0.3.1",
  "edu.utdallas.hltri" % "inquire" % "0.1.0"
)  

exportJars := true

// add sqlite jdbc driver
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.7.2"

// add sparql support
libraryDependencies ++= Seq(
  "org.slf4j" % "log4j-over-slf4j" % "1.7.7", "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
  "org.apache.jena" % "apache-jena-libs" % "2.10.1" exclude("log4j", "log4j") exclude("org.slf4j", "slf4j-log4j12")
)

// Fast trie/radix tree
libraryDependencies += "com.googlecode.concurrent-trees" % "concurrent-trees" % "2.4.0"

libraryDependencies += "postgresql" % "postgresql" % "9.1-901-1.jdbc4"

lazy val medbase = project in file(".")
