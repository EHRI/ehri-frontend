import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "docview"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here
    "com.sun.jersey" % "jersey-core" % "1.9",
    "org.neo4j.app" % "neo4j-server" % "1.8" classifier "static-web" classifier "",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-plugin" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "")

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here    
    resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public")

}
