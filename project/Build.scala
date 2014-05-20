import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import sbt._
import Keys._
import play.Play.autoImport._
import play.twirl.sbt.Import._
import PlayKeys._
import com.typesafe.sbt.rjs.Import.RjsKeys._
import TwirlKeys.templateImports
import com.typesafe.sbt.web._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.rjs.Import._
import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._

object ApplicationBuild extends Build {

  ivyXML :=
    <dependencies>
      <exclude module="org.slf4j.slf4j-log4j12"/>
    </dependencies>

  parallelExecution := false
  logBuffered := false

  val appName = "docview"
  val appVersion = "1.0-SNAPSHOT"

  scalaVersion := "2.10.4"

  javaOptions in Test ++= Seq(
    "-Xmx8G",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:MaxPermSize=256M",
    "-Dconfig.file=conf/test.conf"
  )

  scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

  val appDependencies = Seq(
    jdbc,
    anorm,
    cache,
    filters,
    ws,

    // Ontology
    "ehri-project" % "ehri-definitions" % "1.0",

    // Solely to satisfy SBT: bit.ly/16bFa4O
    "com.google.guava" % "guava" % "17.0",

    // Injection guff
    "com.google.inject" % "guice" % "3.0",
    "com.tzavellas" % "sse-guice" % "0.7.1",

    "jp.t2v" %% "play2-auth" % "0.12.0-SNAPSHOT",

    "mysql" % "mysql-connector-java" % "5.1.25",

    // Pegdown. Currently versions higher than 1.1 crash
    // Play at runtime with an IncompatibleClassChangeError.
    "org.pegdown" % "pegdown" % "1.1.0",

    "joda-time" % "joda-time" % "2.1",
    "org.mindrot" % "jbcrypt" % "0.3m",

    // S3 Upload plugin
    "nl.rhinofly" %% "play-s3" % "3.3.3",
    "net.coobird" % "thumbnailator" % "[0.4, 0.5)",

    // Mailer...
    "com.typesafe" %% "play-plugins-mailer" % "2.2.0",

    // Solr stuff
    "com.github.seratch" %% "scalikesolr" % "[4.3,)",

    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final"
  )

  val testDependencies = Seq(
    "jp.t2v" %% "play2-auth-test" % "0.12.0-SNAPSHOT" % "test",

    // Forced logback to older version due to conflict with Neo4j
    "ch.qos.logback" % "logback-core" % "1.0.3" force(),
    "ch.qos.logback" % "logback-classic" % "1.0.3" force(),

    "com.sun.jersey" % "jersey-core" % "1.9" % "test",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "",
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier ""
  )

  val otherSettings = Seq(
    templateImports in Compile ++= Seq("models.base._", "models.forms._", "acl._", "defines._"),
    routesImport += "defines.EntityType",

    JsEngineKeys.parallelism := 1,

    // Less files with an underscore are excluded
    includeFilter in (Assets, LessKeys.less) := "*.less",
    excludeFilter in (Assets, LessKeys.less) := "_*.less",

    pipelineStages := Seq(digest, gzip),

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

  lazy val core = Project(appName + "-core", file("modules/core"))
    .enablePlugins(play.PlayScala)
    .enablePlugins(SbtWeb).settings(
      version := appVersion,
      name := appName + "-core",
      libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*)

  lazy val annotation = Project(appName + "-annotation", file("modules/annotation"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core)

  lazy val linking = Project(appName + "-linking", file("modules/linking"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation)

  lazy val archdesc = Project(appName + "-archdesc", file("modules/archdesc"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val authorities = Project(appName + "-authorities", file("modules/authorities"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val vocabs = Project(appName + "-vocabs", file("modules/vocabs"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val portal = Project(appName + "-portal", file("modules/portal"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val admin = Project(appName + "-admin", file("modules/admin"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, archdesc, authorities, vocabs, portal)
    .aggregate(core, archdesc, authorities, vocabs, portal)

  lazy val guides = Project(appName + "-guides", file("modules/guides"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies
  ).settings(otherSettings: _*).dependsOn(core, annotation, linking)

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies ++ testDependencies
  ).settings(otherSettings: _*).dependsOn(admin, portal, guides)
    .aggregate(admin, portal, guides)


  override def rootProject = Some(main)
}
