import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  ivyXML :=
    <dependencies>
      <exclude module="org.slf4j.slf4j-log4j12"/>
    </dependencies>

  parallelExecution := false
  logBuffered := false

  val appName = "docview"
  val appVersion = "1.0-SNAPSHOT"

  javaOptions in test += "-Xmx8G"

  val appDependencies = Seq(
    jdbc,
    anorm,
    filters,

    "jp.t2v" %% "play21.auth" % "0.6",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    // Forced logback to older version due to conflict with Neo4j
    "ch.qos.logback" % "logback-core" % "1.0.3" force(),
    "ch.qos.logback" % "logback-classic" % "1.0.3" force(),
    "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4",
    "joda-time" % "joda-time" % "2.1",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.codehaus.groovy" % "groovy-all" % "2.0.6",
    // Solr stuff
    "com.github.seratch" %% "scalikesolr" % "4.0.0",
    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final",
    //"com.typesafe" % "play-plugins-mailer_2.10" % "2.1-SNAPSHOT",

    "ehri-project" % "ehri-definitions" % "1.0",

    // Injection guff
    "com.google.inject" % "guice" % "3.0",
    "com.tzavellas" % "sse-guice" % "0.7.1",

    // Test dependencies...
    "com.sun.jersey" % "jersey-core" % "1.9" % "test",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier ""
  )


  val otherSettings = Seq(
    templatesImport ++= Seq("models.base._", "models.forms._", "acl._", "defines._", "global.MenuConfig"),

    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository",
    resolvers += "Codahale" at "http://repo.codahale.com"
  )

  lazy val core = play.Project(
    appName + "-core", appVersion, appDependencies, path = file("modules/core"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*)

  lazy val archdesc = play.Project(
    appName + "-archdesc", appVersion, appDependencies, path = file("modules/archdesc"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core)

  lazy val authorities = play.Project(
    appName + "-authorities", appVersion, appDependencies, path = file("modules/authorities"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core)

  lazy val vocabs = play.Project(
    appName + "-vocabs", appVersion, appDependencies, path = file("modules/vocabs"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core)

  lazy val aaMain = play.Project(appName, appVersion, appDependencies,
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, archdesc, authorities, vocabs)
    .aggregate(core, archdesc, authorities, vocabs)
}
