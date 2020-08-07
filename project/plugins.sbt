// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.bintrayRepo("givers", "maven")

// Prevent library dependency errors with scala-xml 1.3.0 to 2.2.0, which in
// fact should be compatible
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

//resolvers += "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.5")

addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")

addSbtPlugin("net.ground5hark.sbt" % "sbt-concat" % "0.2.0")

// Excluded because too slow for workable development
//addSbtPlugin("net.ground5hark.sbt" % "sbt-css-compress" % "0.1.4")

addSbtPlugin("io.github.givesocialmovement" % "sbt-vuefy" % "6.0.0")

// For building command line tools...
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.0.0")

