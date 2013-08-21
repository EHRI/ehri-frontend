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

  javaOptions in Test ++= Seq(
    "-Xmx8G",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:MaxPermSize=256M",
    "-Dconfig.file=conf/test.conf"
  )


  val appDependencies = Seq(
    jdbc,
    anorm,
    filters,

    // Ontology
    "ehri-project" % "ehri-definitions" % "1.0",

    // Injection guff
    "com.google.inject" % "guice" % "3.0",
    "com.tzavellas" % "sse-guice" % "0.7.1",

    "jp.t2v" %% "play21.auth" % "0.6",
    "postgresql" % "postgresql" % "9.1-901.jdbc4",
    "mysql" % "mysql-connector-java" % "5.1.25",
    "org.markdownj" % "markdownj" % "0.3.0-1.0.2b4",
    "joda-time" % "joda-time" % "2.1",
    "org.mindrot" % "jbcrypt" % "0.3m",
    "org.codehaus.groovy" % "groovy-all" % "2.0.6",
    // Solr stuff
    "com.github.seratch" %% "scalikesolr" % "4.0.0",
    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final"
  )

  val testDependencies = Seq(
    // Forced logback to older version due to conflict with Neo4j
    "ch.qos.logback" % "logback-core" % "1.0.3" force(),
    "ch.qos.logback" % "logback-classic" % "1.0.3" force(),

    "com.sun.jersey" % "jersey-core" % "1.9" % "test",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier ""
  )


  val otherSettings = Seq(
    templatesImport ++= Seq("models.base._", "models.forms._", "acl._", "defines._", "global.GlobalConfig"),

    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository",
    resolvers += "Codahale" at "http://repo.codahale.com"
  )

  lazy val core = play.Project(
    appName + "-core", appVersion, appDependencies, path = file("modules/core"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*)

  lazy val annotation = play.Project(
    appName + "-annotation", appVersion, appDependencies, path = file("modules/annotation"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core)

  lazy val linking = play.Project(
    appName + "-linking", appVersion, appDependencies, path = file("modules/linking"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, annotation)

  lazy val archdesc = play.Project(
    appName + "-archdesc", appVersion, appDependencies, path = file("modules/archdesc"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, annotation, linking)

  lazy val authorities = play.Project(
    appName + "-authorities", appVersion, appDependencies, path = file("modules/authorities"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, annotation, linking)

  lazy val vocabs = play.Project(
    appName + "-vocabs", appVersion, appDependencies, path = file("modules/vocabs"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, annotation, linking)

  lazy val portal = play.Project(
    appName + "-portal", appVersion, appDependencies, path = file("modules/portal"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, archdesc, authorities, vocabs)
    .aggregate(core, archdesc, authorities, vocabs)

  lazy val admin = play.Project(
    appName + "-admin", appVersion, appDependencies, path = file("modules/admin"),
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(core, archdesc, authorities, vocabs)
    .aggregate(core, archdesc, authorities, vocabs)

  lazy val aaMain = play.Project(appName, appVersion, appDependencies ++ testDependencies,
    settings = Defaults.defaultSettings ++ play.Project.intellijCommandSettings("SCALA")
  ).settings(otherSettings:_*).dependsOn(admin, portal)
    .aggregate(admin, portal)
}
