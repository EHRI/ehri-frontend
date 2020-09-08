import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import net.ground5hark.sbt.concat.Import._
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._
import play.core.PlayVersion.{akkaVersion, akkaHttpVersion}
import play.twirl.sbt.Import.TwirlKeys.templateImports


parallelExecution in ThisBuild := false
logBuffered := false

logLevel := Level.Debug

val projectScalaVersion = "2.12.10"
val appName = "docview"
val appVersion = "1.0.6-SNAPSHOT"

val backendVersion = "0.13.12"
val dataConverterVersion = "1.1.10"
val alpakkaVersion = "1.1.2"

val backendDependencies = Seq(
  // Play stuff
  ws,
  caffeine,

  // commons text
  "org.apache.commons" % "commons-text" % "1.4",

  // Push JSON parser used for stream parsing...
  "com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % alpakkaVersion,

  // CSV parser/writer...
  "com.lightbend.akka" %% "akka-stream-alpakka-csv" % alpakkaVersion,

  // XML parsing:  TODO: move to admin module
  "com.lightbend.akka" %% "akka-stream-alpakka-xml" % "1.1.2",

  // IRI helper...
  "org.apache.jena" % "jena-iri" % "3.9.0",

  // Ontology
  "ehri-project" % "ehri-definitions" % backendVersion
)

val coreDependencies = backendDependencies ++ Seq(
  guice,
  jdbc,
  evolutions,
  filters,
  openId,

  // Force Akka HTTP version
  "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-xml"   % akkaHttpVersion,

  // Anorm DB lib
  "org.playframework.anorm" %% "anorm" % "2.6.2",

  // Commons IO
  "commons-io" % "commons-io" % "2.5",

  // Commons codec
  "commons-codec" % "commons-codec" % "1.11",

  // Password hashing
  "org.mindrot" % "jbcrypt" % "0.3m",

  // PostgreSQL
  "org.postgresql" % "postgresql" % "42.2.18",

  // Markdown rendering
  "com.vladsch.flexmark" % "flexmark-all" % "0.28.10",

  "com.atlassian.commonmark" % "commonmark" % "0.12.1",
  "com.atlassian.commonmark" % "commonmark-ext-autolink" % "0.12.1",

  // HTML sanitising...
  "org.jsoup" % "jsoup" % "1.11.3",

  // Mailer...
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "7.0.1",

  // Time formatting library
  "org.ocpsoft.prettytime" % "prettytime" % "3.2.7.Final",

  // Logging: Janino is necessary for configuring LogBack's regex filter
  "org.codehaus.janino" % "janino" % "2.7.7"
)

val portalDependencies = Seq(
  // Library for the silencer compiler plugin, only needed
  // at compile time.
  "com.github.ghik" %% "silencer-lib" % "1.3.1" % Compile,

  // Helper for making thumbnails...
  "net.coobird" % "thumbnailator" % "[0.4, 0.5)",

  // EHRI indexing tools
  "ehri-project" % "index-data-converter" % dataConverterVersion exclude("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"),

  // S3 Upload plugin
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "1.1.2",

  // EAD validation testing
  "org.relaxng" % "jing" % "20181222",

  // EAD transformation... unstable!
  "org.basex" % "basex" % "8.5",

  // S3 sdk
  "com.amazonaws" % "aws-java-sdk-core" % "1.11.476",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.476"
)

val testDependencies = Seq(
  specs2 % Test,
  "com.h2database" % "h2" % "1.4.193" % Test,

  // Used for testing JSON stream parsing...
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)

val additionalResolvers = Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),

  // EHRI repositories
  "EHRI Releases" at "https://dev.ehri-project.eu/artifactory/libs-release/",

  // BaseX (for XML transformations)
  "BaseX repository" at "https://files.basex.org/maven/"
)

val validateMessages = TaskKey[Unit]("validate-messages", "Validate messages")

// Exclude certain conf files (e.g. those containing secret keys)
// that we do not want packaged
val excludedResources = Seq(
  "oauth2.conf",
  "parse.conf",
  "aws.conf",
  "dos.conf",
  "test.conf",
  "external_pages.conf",
  "api-keys.conf",
  "form-config.conf",
  "logback-play-dev.xml"
)

val commonSettings = Seq(

  logLevel := Level.Debug,

  version := appVersion,

  scalaVersion in ThisBuild := projectScalaVersion,

  // Increase the JVM heap to avoid running
  // out of space during the memory intensive integration
  // tests. Additionally, set the path to the test config
  // file as an env var.
  javaOptions in Test ++= Seq(
    "-Xmx1G",
    "-XX:+CMSClassUnloadingEnabled",
    s"-Dconfig.file=${(baseDirectory in LocalRootProject).value / "conf" / "test.conf"}",
    s"-Dlogger.file=${(baseDirectory in LocalRootProject).value / "conf" / "logback-play-dev.xml"}"
  ),

  // Show warnings and deprecations
  scalacOptions in ThisBuild ++= Seq(
    "-encoding", "UTF-8",
    "-Ywarn-unused:imports",
    "-unchecked",
    "-deprecation"
  ),

  // Don't execute tests in parallel
  parallelExecution := false,

  resolvers ++= additionalResolvers,

  // Disable documentation generation
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

val webAppSettings = Seq(
  // Add silencer plugin... 
  addCompilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.3.1"),

  // ... and silence all warnings on autogenerated files
  scalacOptions += "-P:silencer:pathFilters=target/.*",

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
      import java.io.FileInputStream
      import java.text.MessageFormat
      import java.util.Properties

      import scala.collection.JavaConverters._
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
      sLog.value.debug(s"Validating ${allMessages.size} messages file(s) in ${baseDirectory.value}")
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
    baseDir: File => baseDir / "test/resources"
  }.value,

  // Always use nodejs to build the assets - Trireme is too slow...
  JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,

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
    uglifyMangle := false, // Mangling and compressing causes problems so don't
    uglifyCompress := false,
    // Should really add cssCompress stage here but it's too slow currently
    pipelineStages in Assets := Seq(concat, uglify, digest, gzip),
    Concat.groups := Seq(
      "js/script-pre.js" -> group(
        Seq(
          "js/lib/jquery-3.4.1.js",
          "js/lib/jquery.validate-1.19.1.js",
          "js/lib/typeahead-1.2.1.js",
          "js/lib/handlebars-v4.4.3.js",
          "js/lib/jquery.cookie.js",
          "js/lib/jquery.hoverIntent.js",
          "js/feedback.js",
          "js/common.js"
        )
      ),
      "js/script-post.js" -> group(
        Seq(
          "js/lib/jquery.placeholder.js",
          "js/lib/bootstrap.bundle.js",
          "js/portal.js"
        )
      ),
      "js/script-post-signedin.js" -> group(
        Seq(
          "js/lib/jquery.placeholder.js",
          "js/lib/bootstrap.bundle.js",
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
  .dependsOn(portal % "test->test;compile->compile")

lazy val admin = Project(appName + "-admin", file("modules/admin"))
  .enablePlugins(play.sbt.PlayScala)
  .settings(libraryDependencies += specs2 % Test)
  .settings(commonSettings ++ webAppSettings: _*)
  .dependsOn(api % "test->test;compile->compile")

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
  .settings(commonSettings ++ webAppSettings: _*)
  .dependsOn(portal % "test->test;compile->compile", admin, guides, api, solr)
  .aggregate(backend, core, admin, portal, guides, api, solr)
