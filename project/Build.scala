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


  val coreDependencies = Seq(
    jdbc,
    anorm,
    cache,
    filters,

    // Ontology
    "ehri-project" % "ehri-definitions" % "1.0",

    // Solely to satisfy SBT: bit.ly/16bFa4O
    "com.google.guava" % "guava" % "17.0",

    // Injection guff
    "com.google.inject" % "guice" % "3.0",
    "com.tzavellas" % "sse-guice" % "0.7.1",

    "jp.t2v" %% "play2-auth" % "0.11.0",

    "mysql" % "mysql-connector-java" % "5.1.25",

    // Pegdown. Currently versions higher than 1.1 crash
    // Play at runtime with an IncompatibleClassChangeError.
    "org.pegdown" % "pegdown" % "1.1.0",

    "joda-time" % "joda-time" % "2.1",
    "org.mindrot" % "jbcrypt" % "0.3m",

    // Mailer...
    "com.typesafe" %% "play-plugins-mailer" % "2.2.0",

    // Solr stuff
    "com.github.seratch" %% "scalikesolr" % "[4.3,)",

    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final"
  )

  val portalDependencies = Seq(
    // S3 Upload plugin
    "nl.rhinofly" %% "play-s3" % "3.3.3",
    "net.coobird" % "thumbnailator" % "[0.4, 0.5)"
  )

  val testDependencies = Seq(
    "jp.t2v" %% "play2-auth-test" % "0.11.0" % "test",
    // Forced logback to older version due to conflict with Neo4j
    "ch.qos.logback" % "logback-core" % "1.0.3" force(),
    "ch.qos.logback" % "logback-classic" % "1.0.3" force(),

    "com.sun.jersey" % "jersey-core" % "1.9" % "test",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier ""
  )

  val otherSettings = Seq(
    templatesImport ++= Seq("models.base._", "models.forms._", "acl._", "defines._"),
    routesImport += "defines.EntityType",

    resolvers += Resolver.file("Local Repository", file("/home/mike/dev/play/playframework/repository/local"))(Resolver.ivyStylePatterns),
    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "Codahale" at "http://repo.codahale.com",
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local",

    // SBT magic: http://stackoverflow.com/a/12772739/285374
    resourceDirectory in Test <<= baseDirectory apply {
      (baseDir: File) => baseDir / "test/resources"
    }
  )

  lazy val core = play.Project(
    appName + "-core", appVersion, coreDependencies, path = file("modules/core")
  ).settings(otherSettings: _*)

  lazy val annotation = play.Project(
    appName + "-annotation", appVersion, path = file("modules/annotation")
  ).settings(otherSettings: _*).dependsOn(core)

  lazy val linking = play.Project(
    appName + "-linking", appVersion, path = file("modules/linking")
  ).settings(otherSettings: _*).dependsOn(core, annotation)

  lazy val archdesc = play.Project(
    appName + "-archdesc", appVersion, path = file("modules/archdesc")
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val authorities = play.Project(
    appName + "-authorities", appVersion, path = file("modules/authorities")
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val vocabs = play.Project(
    appName + "-vocabs", appVersion, path = file("modules/vocabs")
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val portal = play.Project(
    appName + "-portal", appVersion, portalDependencies, path = file("modules/portal"))
    .settings(otherSettings: _*).dependsOn(core, annotation, linking)
    .aggregate(core, annotation, linking)

  lazy val admin = play.Project(
    appName + "-admin", appVersion, path = file("modules/admin")
  ).settings(otherSettings: _*).dependsOn(core, archdesc, authorities, vocabs, portal)
    .aggregate(core, archdesc, authorities, vocabs, portal, guides)

  lazy val guides = play.Project(
    appName + "-guides", appVersion, path = file("modules/guides")
  ).settings(otherSettings: _*).dependsOn(archdesc)
    .aggregate(archdesc)

  lazy val main = play.Project(appName, appVersion, testDependencies
  ).settings(otherSettings: _*).dependsOn(admin, portal, guides)
    .aggregate(admin, portal, guides)


  override def rootProject = Some(main)
}
