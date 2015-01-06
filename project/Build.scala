
import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.rjs.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web._
import play.Play.autoImport._
import play.PlayImport.PlayKeys._
import play.twirl.sbt.Import.TwirlKeys.templateImports
import sbt.Keys._
import sbt._

object ApplicationBuild extends Build {

  ivyXML :=
    <dependencies>
      <exclude module="org.slf4j.slf4j-log4j12"/>
    </dependencies>

  parallelExecution in ThisBuild := false
  logBuffered := false

  val appName = "docview"
  val appVersion = "1.0.3-SNAPSHOT"

  val backendDependencies = Seq(
    ws,
    cache,

    // Ontology
    "ehri-project" % "ehri-definitions" % "1.0",
    "joda-time" % "joda-time" % "2.1"
  )

  val backendTestDependencies = Seq(
    "org.neo4j" % "neo4j-kernel" % "1.9.7" % "test" classifier "tests" classifier "",
    "org.neo4j.app" % "neo4j-server" % "1.9.7" % "test" classifier "tests" classifier "",
    "org.hamcrest" % "hamcrest-all" % "1.3" % "test",

    "com.sun.jersey" % "jersey-core" % "1.9" % "test",
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin", "gremlin-groovy"),
    "ehri-project" % "ehri-extension" % "0.0.1-SNAPSHOT" % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin", "gremlin-groovy")
  )

  val coreDependencies = backendDependencies ++ Seq(
    jdbc,
    anorm,
    filters,

    // Solely to satisfy SBT: bit.ly/16bFa4O
    "com.google.guava" % "guava" % "17.0",

    // Injection guff
    "com.google.inject" % "guice" % "4.0-beta",

    "jp.t2v" %% "play2-auth" % "0.13.0",

    "mysql" % "mysql-connector-java" % "5.1.25",

    // Pegdown. Currently versions higher than 1.1 crash
    // Play at runtime with an IncompatibleClassChangeError.
    "org.pegdown" % "pegdown" % "1.4.2",
    //"org.ow2.asm" % "asm-all" % "4.1",


  "org.mindrot" % "jbcrypt" % "0.3m",

    // Mailer...
    "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.0",

    // Solr stuff
    "com.github.seratch" %% "scalikesolr" % "4.10.0",

    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final"
  )
  
  val portalDependencies = Seq(
    "net.coobird" % "thumbnailator" % "[0.4, 0.5)",
    "net.sf.opencsv" % "opencsv" % "2.3",

    // S3 Upload plugin
    "com.github.seratch" %% "awscala" % "0.3.+"
  )

  val testDependencies = backendTestDependencies ++ Seq(
    "jp.t2v" %% "play2-auth-test" % "0.13.0" % "test"
  )

  val commonSettings = Seq(

    scalaVersion := "2.10.4",

    javaOptions in Test ++= Seq(
      "-Xmx8G",
      "-XX:+CMSClassUnloadingEnabled",
      "-XX:MaxPermSize=256M",
      "-Dconfig.file=conf/test.conf"
    ),

      // don't execute tests in parallel
    parallelExecution := false,

    // classes to auto-import into templates
    templateImports in Compile ++= Seq("models.base._", "utils.forms._", "acl._", "defines._", "backend.Entity"),
    // auto-import EntityType enum into routes
    routesImport += "defines.EntityType",

    // Test the unmanaged directory to test_lib to pick up
    // the repackaged version of jersey-server that we need to avoid
    // a conflict with Pegdown and asm-4.x
    unmanagedBase in Test := baseDirectory.value / "test_lib",

    // additional resolvers
    resolvers += "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    resolvers += "Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository",
    resolvers += "Codahale" at "http://repo.codahale.com",
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",

    // SBT magic: http://stackoverflow.com/a/12772739/285374
    // pick up additional resources in test
    resourceDirectory in Test <<= baseDirectory apply {
      (baseDir: File) => baseDir / "test/resources"
    },

    // Always use nodejs to build the assets - Trireme is too slow...
    JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,

    // Show warnings and deprecations
    scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation"),

    // Less files with an underscore are excluded
    includeFilter in (Assets, LessKeys.less) := "*.less",
    excludeFilter in (Assets, LessKeys.less) := "_*.less"
  )

  val assetSettings = Seq(
  )

  lazy val backend = Project(appName + "-backend", file("modules/backend"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    name := appName + "-backend",
    libraryDependencies ++= backendDependencies ++ backendTestDependencies
  ).settings(commonSettings: _*)

  lazy val core = Project(appName + "-core", file("modules/core"))
    .enablePlugins(play.PlayScala)
    .enablePlugins(SbtWeb).settings(
      version := appVersion,
      name := appName + "-core",
      libraryDependencies ++= coreDependencies,
      pipelineStages := Seq(rjs, digest, gzip),
      RjsKeys.mainModule := "core-main"
  ).settings(commonSettings: _*).dependsOn(backend % "test->test;compile->compile")

  lazy val portal = Project(appName + "-portal", file("modules/portal"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    routesImport += "models.view._",
    libraryDependencies ++= portalDependencies,
    pipelineStages := Seq(rjs, digest, gzip),
    RjsKeys.mainModule := "portal-main"
  ).settings(commonSettings: _*).dependsOn(core)

  lazy val admin = Project(appName + "-admin", file("modules/admin"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion
  ).settings(commonSettings: _*).dependsOn(portal)

  lazy val guides = Project(appName + "-guides", file("modules/guides"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion
  ).settings(commonSettings: _*).dependsOn(admin)

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= coreDependencies ++ testDependencies
  ).settings(commonSettings ++ assetSettings: _*)
    .dependsOn(portal, admin, guides)
    .aggregate(backend, core, admin, portal, guides)

  override def rootProject = Some(main)
}
