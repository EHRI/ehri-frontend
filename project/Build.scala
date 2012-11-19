import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "docview"
  val appVersion = "1.0-SNAPSHOT"

  javaOptions in (Test,run) += "-XX:MaxPermSize=1024m"

  val appDependencies = Seq(
    // Add your project dependencies here
    "jp.t2v" %% "play20.auth" % "0.4-SNAPSHOT",
    "com.codahale" % "jerkson_2.9.1" % "0.5.0",
    "com.sun.jersey" % "jersey-core" % "1.9",
    "org.neo4j.app" % "neo4j-server" % "1.8" classifier "static-web" classifier "",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "joda-time" % "joda-time" % "2.1",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-plugin" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "")

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here    
    resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Codahale" at "http://repo.codahale.com"
    )
    
    // pubishing this locally for 0.4-SNAPSHOT
    //resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/")
}
