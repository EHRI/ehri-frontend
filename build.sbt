import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.rjs.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import play.sbt.Play.autoImport._
import play.twirl.sbt.Import.TwirlKeys.templateImports
import play.sbt.routes.RoutesKeys._
import net.ground5hark.sbt.concat.Import._


parallelExecution in ThisBuild := false
logBuffered := false

val projectScalaVersion = "2.11.8"
val appName = "docview"
val appVersion = "1.0.6-SNAPSHOT"

val backendVersion = "0.13.9"
val dataConverterVersion = "1.1.10"

val backendDependencies = Seq(
  ws,
  ehcache,

  // Push JSON parser used for stream parsing...
  "de.undercouch" % "actson" % "1.2.0",

  // CSV parser/writer...
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-csv" % "2.6.4",

  // Ontology
  "ehri-project" % "ehri-definitions" % backendVersion
)

val coreDependencies = backendDependencies ++ Seq(
  guice,
  jdbc,
  evolutions,
  filters,
  openId,

  // Anorm DB lib
  "com.typesafe.play" %% "anorm" % "2.5.3",

  // Commons IO
  "commons-io" % "commons-io" % "2.5",

  // Commons Text
  "org.apache.commons" % "commons-text" % "1.1",

  // Password hashing
  "org.mindrot" % "jbcrypt" % "0.3m",

  // PostgreSQL
  "org.postgresql" % "postgresql" % "42.1.1",

  // Markdown rendering
  "com.vladsch.flexmark" % "flexmark-all" % "0.19.3",

  // HTML sanitising...
  "org.jsoup" % "jsoup" % "1.8.3",

  // Mailer...
  "com.typesafe.play" %% "play-mailer" % "6.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.0",

  // Time formatting library
  "org.ocpsoft.prettytime" % "prettytime" % "3.2.7.Final",

  // Logging: Janino is necessary for configuring LogBack's regex filter
  "org.codehaus.janino" % "janino" % "2.7.7"
)

val portalDependencies = Seq(
  // Helper for making thumbnails...
  "net.coobird" % "thumbnailator" % "[0.4, 0.5)",

  // EHRI indexing tools
  "ehri-project" % "index-data-converter" % dataConverterVersion exclude("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"),

  // S3 Upload plugin
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "0.12"
)

val testDependencies = Seq(
  specs2 % Test,
  "com.h2database" % "h2" % "1.4.193" % Test,

  // Used for testing JSON stream parsing...
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.1" % Test
)

val additionalResolvers = Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  "EHRI Snapshots" at "https://dev.ehri-project.eu/artifactory/libs-snapshot/",
  "EHRI Releases" at "https://dev.ehri-project.eu/artifactory/libs-release/"
)

val validateMessages = TaskKey[Unit]("validate-messages", "Validate messages")

// Exclude certain conf files (e.g. those containing secret keys)
// that we do not want packaged
val excludedResources = Seq(
  "oauth2.conf",
  "parse.conf",
  "aws.conf",
  "test.conf",
  "external_pages.conf",
  "logback-play-dev.xml"
)

val commonSettings = Seq(

  version := appVersion,

  scalaVersion in ThisBuild := projectScalaVersion,

  // Increase the JVM heap to avoid running
  // out of space during the memory intensive integration
  // tests. Additionally, set the path to the test config
  // file as an env var.
  javaOptions in Test ++= Seq(
    "-Xmx1G",
    "-XX:+CMSClassUnloadingEnabled",
    "-Dconfig.file=conf/test.conf",
    s"-Dlogger.file=${(baseDirectory in LocalRootProject).value / "conf" / "logback-play-dev.xml"}"
  ),

  // Show warnings and deprecations
  scalacOptions in ThisBuild ++= Seq(
    "-encoding", "UTF-8",
    "-Xlint",
    "-unchecked",
    "-deprecation"
  ),

  // Don't execute tests in parallel
  parallelExecution := false,

  resolvers ++= additionalResolvers
)

val webAppSettings = Seq(

  // Allow SBT to tell Scaladoc where to find external
  // api docs if dependencies provide that metadata
  autoAPIMappings := true,

  // Check messages files contain valid format strings
  validateMessages := {
    def messagesFiles(base: File): Seq[File] = {
      val finder: PathFinder = (base / "conf") * "messages*"
      finder.get
    }

    def validate(messageFile: File): Unit = {
      import java.util.Properties
      import java.text.MessageFormat
      import scala.collection.JavaConverters._
      import java.io.FileInputStream
      val properties: Properties = new Properties()
      val fis = new FileInputStream(messageFile)
      try {
        properties.load(fis)
        properties.stringPropertyNames().asScala.foreach { key =>
          val text = properties.getProperty(key)
          if (text != null) {
            try {
              MessageFormat.format(text)
            } catch {
              case e: IllegalArgumentException =>
                val err =
                  s"""
                     |Invalid message text as key: $key in:
                     |  ${messageFile.getAbsoluteFile}
                     |
                     |  ${e.getLocalizedMessage}
                   """.stripMargin
                sys.error(err)
            }
          }
        }
      } finally {
        fis.close()
      }
    }
    val allMessages = messagesFiles(baseDirectory.value)
    if (allMessages.nonEmpty) {
      streams.value.log.debug(s"Validating ${allMessages.size} messages file(s) in ${baseDirectory.value}")
      allMessages.foreach(validate)
    }
  },

  // Classes to auto-import into templates
  templateImports in Compile ++= Seq(
    "models.base._",
    "acl._",
    "defines._"
  ),

  // Auto-import EntityType enum into routes
  routesImport ++= Seq(
    "defines.EntityType",
    "utils.binders._"
  ),

  // SBT magic: http://stackoverflow.com/a/12772739/285374
  // pick up additional resources in test
  resourceDirectory in Test := baseDirectory.apply {
    (baseDir: File) => baseDir / "test/resources"
  }.value,

  // Always use nodejs to build the assets - Trireme is too slow...
  JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,

  // Less files with an underscore are excluded
  includeFilter in (Assets, LessKeys.less) := "*.less",
  excludeFilter in (Assets, LessKeys.less) := "_*.less",

  // Filter out excluded resources from packaging
  mappings in Universal := (mappings in Universal).value.filterNot { case (f, s) =>
    excludedResources contains f.getName
  },

  compile in Compile := {
    validateMessages.value
    (compile in Compile).value
  }
)

lazy val backend = Project(appName + "-backend", file("modules/backend"))
  .settings(
    name := appName + "-backend",
    libraryDependencies ++= backendDependencies ++ testDependencies,
    resolvers ++= additionalResolvers,
    parallelExecution := true)

lazy val core = Project(appName + "-core", file("modules/core"))
  .enablePlugins(play.sbt.PlayScala)
  .settings(name := appName + "-core", libraryDependencies ++= coreDependencies)
  .settings(commonSettings: _*)
  .dependsOn(backend % "test->test;compile->compile")

lazy val portal = Project(appName + "-portal", file("modules/portal"))
  .enablePlugins(play.sbt.PlayScala)
  .enablePlugins(SbtWeb)
  .settings(commonSettings ++ webAppSettings: _*)
  .settings(
    routesImport += "models.view._",
    libraryDependencies ++= portalDependencies,
    RjsKeys.mainModule := "portal-main",
    pipelineStages in Assets := Seq(concat, digest, gzip),
    Concat.groups := Seq(
     "css/portal-all.css" -> group(
        Seq(
          "css/portal.css"
        )
       ),
      "js/script-pre.js" -> group(
        Seq(
          "js/lib/jquery-1.11.2.js",
          "js/lib/jquery.autosize.js",
          "js/lib/jquery.validate.js",
          "js/lib/typeahead.js",
          "js/lib/handlebar.js",
          "js/lib/jquery.cookie.js",
          "js/lib/jquery.hoverIntent.js",
          "js/select2/select2.js",
          "js/feedback.js",
          "js/common.js"
        )
      ),
      "js/script-post.js" -> group(
        Seq(
          "js/lib/jquery.cookie.js",
          "js/lib/jquery.placeholder.js",
          "js/lib/bootstrap.js",
          "js/portal.js"
        )
      ),
      "js/script-post-signedin.js" -> group(
        Seq(
          "js/lib/jquery.cookie.js",
          "js/lib/jquery.placeholder.js",
          "js/lib/bootstrap.js",
          "js/portal.js",
          "js/portal-signedin.js"
        )
      )
    )
  ).dependsOn(core % "test->test;compile->compile")

lazy val api = Project(appName + "-api", file("modules/api"))
  .enablePlugins(play.sbt.PlayScala)
  .settings(libraryDependencies += "org.everit.json" % "org.everit.json.schema" % "1.3.0")
  .settings(commonSettings ++ webAppSettings: _*)
  .dependsOn(portal)

lazy val admin = Project(appName + "-admin", file("modules/admin"))
  .enablePlugins(play.sbt.PlayScala)
  .settings(libraryDependencies += specs2 % Test)
  .settings(commonSettings ++ webAppSettings: _*)
  .dependsOn(api)

lazy val guides = Project(appName + "-guides", file("modules/guides"))
  .enablePlugins(play.sbt.PlayScala)
  .settings(commonSettings ++ webAppSettings: _*)
  .dependsOn(admin)

// Solr search engine implementation.
lazy val solr = Project(appName + "-solr", file("modules/solr"))
  .settings(commonSettings: _*)
  .dependsOn(core % "test->test;compile->compile")

lazy val main = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala)
  .enablePlugins(LauncherJarPlugin)
  .settings(libraryDependencies ++= coreDependencies ++ testDependencies)
  .settings(commonSettings: _*)
  .dependsOn(portal % "test->test;compile->compile", admin, guides, api, solr)
  .aggregate(backend, core, admin, portal, guides, api, solr)
