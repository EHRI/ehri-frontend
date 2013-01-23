import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "docview"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    jdbc,
    anorm,
    filters,

    // Add your project dependencies here
    "jp.t2v" % "play20.auth_2.10.0" % "0.4-SNAPSHOT",
    "com.sun.jersey" % "jersey-core" % "1.9",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "joda-time" % "joda-time" % "2.1",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.codehaus.groovy" % "groovy-all" % "2.0.6",
    //"com.typesafe" % "play-plugins-mailer_2.10" % "2.1-SNAPSHOT",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-plugin" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "")

  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here

    templatesImport ++= Seq("models.base._", "acl._", "defines._"),


    resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Codahale" at "http://repo.codahale.com"
    )
    
    // pubishing this locally for 0.4-SNAPSHOT
    //resolvers += "t2v.jp repo" at "http://www.t2v.jp/maven-repo/")
}
