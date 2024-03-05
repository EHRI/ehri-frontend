import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.packager.SettingsHelper._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import net.ground5hark.sbt.concat.Import._
import play.core.PlayVersion.{akkaHttpVersion, akkaVersion}
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._
import play.twirl.sbt.Import.TwirlKeys.templateImports
import sbt.Credentials
import sbt.Keys.{compile, credentials, mappings, publishConfiguration, publishLocalConfiguration, resourceDirectory}


logBuffered := false
logLevel := Level.Info
ThisBuild / organization := "eu.ehri-project"

val projectScalaVersion = "2.12.12"
val appName = "docview"

val backendVersion = "0.15.1"
val dataConverterVersion = "1.1.15"
val alpakkaVersion = "3.0.4"

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

  // IRI helper...
  "org.apache.jena" % "jena-iri" % "3.9.0",

  // Ontology
  "eu.ehri-project" % "ehri-definitions" % backendVersion
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
  "org.playframework.anorm" %% "anorm" % "2.6.10",
  "org.playframework.anorm" %% "anorm-postgres" % "2.6.10",

  // Commons IO
  "commons-io" % "commons-io" % "2.5",

  // Commons codec
  "commons-codec" % "commons-codec" % "1.11",

  // Password hashing
  "org.mindrot" % "jbcrypt" % "0.3m",

  // PostgreSQL
  "org.postgresql" % "postgresql" % "42.3.2",

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
  "eu.ehri-project" % "index-data-converter" % dataConverterVersion exclude("log4j", "log4j") exclude ("org.slf4j", "slf4j-log4j12"),

  // S3 Upload plugin
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % alpakkaVersion,

  // S3 sdk
  "software.amazon.awssdk" % "s3" % "2.15.63",

  // AWS Location sdk
  "software.amazon.awssdk" % "location" % "2.15.63",
)

val adminDependencies = Seq(
  // EAD validation
  "org.relaxng" % "jing" % "20181222",

  // XML parsing
  "com.lightbend.akka" %% "akka-stream-alpakka-xml" % alpakkaVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-text" % alpakkaVersion,
)

val testDependencies = Seq(
  specs2 % Test,

  // Used for testing JSON stream parsing...
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)

val additionalResolvers = Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),

  // EHRI repositories
  "EHRI Releases" at "https://dev.ehri-project.eu/artifactory/libs-release-local",
)

val validateMessages = TaskKey[Unit]("validate-messages", "Validate messages")

lazy val commonSettings = Seq(

  ThisBuild / scalaVersion := projectScalaVersion,

  // Increase the JVM heap to avoid running
  // out of space during the memory intensive integration
  // tests. Additionally, set the path to the test config
  // file as an env var.
  Test / javaOptions ++= Seq(
    "-XX:+CMSClassUnloadingEnabled",
    s"-Dconfig.file=${(LocalRootProject / baseDirectory).value / "conf" / "test.conf"}",
    s"-Dlogger.file=${(LocalRootProject / baseDirectory).value / "conf" / "logback-play-dev.xml"}"
  ),

  // Show warnings and deprecations
  ThisBuild / scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-Ywarn-unused:imports",
    "-unchecked",
    "-deprecation"
  ),

  resolvers ++= additionalResolvers,

  skip in publish := true,

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
  Compile / templateImports ++= Seq(
    "config._",
    "cookies._",
  ),

  // SBT magic: http://stackoverflow.com/a/12772739/285374
  // pick up additional resources in test
  Test / resourceDirectory := baseDirectory.apply {
    baseDir: File => baseDir / "test/resources"
  }.value,

  // Always use nodejs to build the assets - Trireme is too slow...
  JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,

  // Check the messages files on compilation
  Compile / compile := {
    (Compile / compile).dependsOn(validateMessages).value
  }
)

// Exclude certain conf files (e.g. those containing secret keys)
// that we do not want packaged
val excludedResources = Seq(
  "oauth2.conf",
  "parse.conf",
  "aws.conf",
  "dos.conf",
  "minio.conf",
  "test.conf",
  "external_pages.conf",
  "api-keys.conf",
  "form-config.conf",
  "logback-play-dev.xml"
)

val resourceSettings = Seq(
  // The xtra.xqm XQuery module needs to be accessible outside of a Jar since it
  // is loaded dynamically by file URL from within the transform.xqy script.
  // This means we need to copy it at staging time from the admin module to the main
  // conf directory.
  (Compile / PlayKeys.playExternalizedResources) += file("modules/xquery/src/main/resources/xtra.xqm") -> "xtra.xqm",

  // Filter out excluded resources from packaging
  mappings in Universal := (mappings in Universal).value.filterNot { case (f, s) =>
    excludedResources contains f.getName
  },

  // Filter out excluded resources from jar generation (even though not used)
  mappings in (Compile, packageBin) := (mappings in (Compile, packageBin)).value.filterNot { case (f, s) =>
    excludedResources contains f.getName
  },
)

lazy val backend = Project(appName + "-backend", file("modules/backend"))
  .disablePlugins(PlayScala, SbtVuefy, SbtConcat, AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := appName + "-backend",
    libraryDependencies ++= backendDependencies ++ testDependencies,
    resolvers ++= additionalResolvers,
    parallelExecution := true)

lazy val core = Project(appName + "-core", file("modules/core"))
  .disablePlugins(PlayScala, SbtVuefy, SbtConcat, AssemblyPlugin)
  .settings(name := appName + "-core", libraryDependencies ++= coreDependencies)
  .settings(commonSettings: _*)
  .dependsOn(backend % "test->test;compile->compile")

lazy val portal = Project(appName + "-portal", file("modules/portal"))
  .enablePlugins(PlayScala, SbtWeb)
  .disablePlugins(SbtVuefy, AssemblyPlugin)
  .settings(commonSettings ++ webAppSettings: _*)
  .settings(
    routesImport += "models.view._",
    libraryDependencies ++= portalDependencies,

    // Mangling and compressing causes problems so don't
    uglifyMangle := false,
    uglifyCompress := false,

    // Should really add cssCompress stage here but it's too slow currently
    Assets / pipelineStages := Seq(concat, uglify, digest, gzip),
    Concat.groups := Seq(
      "js/script-pre.js" -> group(
        Seq(
          "js/lib/jquery.js",
          "js/lib/jquery.validate.js",
          "js/lib/jquery.hoverIntent.js",
          "js/lib/typeahead.bundle.js",
          "js/lib/handlebars.js",
          "js/lib/js.cookie.js",
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

lazy val xslt = Project(appName + "-xslt", file("modules/xslt"))
  .disablePlugins(PlayScala, AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "javax.inject" % "javax.inject" % "1",
    "org.slf4j" % "slf4j-api" % "1.7.32",

    // We need JSON here...
    "com.typesafe.play" %% "play-json" % "2.8.1",

    // Saxon for XSLT transformation
    "net.sf.saxon" % "Saxon-HE" % "10.2",

    specs2 % Test,
  ))

lazy val xquery = Project(appName + "-xquery", file("modules/xquery"))
  .disablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(resolvers += "BaseX repository" at "https://files.basex.org/maven/")
  .settings(libraryDependencies ++= Seq(
    "javax.inject" % "javax.inject" % "1",
    "org.slf4j" % "slf4j-api" % "1.7.32",
    "ch.qos.logback" % "logback-classic" % "1.2.5",

    // EAD transformation...
    "org.basex" % "basex" % "8.5",

    // Command line parsing
    "com.github.scopt" %% "scopt" % "4.0.1",

    specs2 % Test,
  ))
  .settings(
    assembly / assemblyJarName := "xmlmapper",
    assembly / mainClass := Some("eu.ehri.project.xml.XQueryTransformer"),

    assembly / assemblyMergeStrategy := {
      // This Java 9+ module file causes merge problems creating the Jar but can be discarded:
      // https://stackoverflow.com/a/60114988/285374
      case "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    // We need to specify a resource to configure logging properly. This resource is not
    // named the default 'logback.xml' to avoid being picked up by other loggers.
    assembly / assemblyPrependShellScript := Some(AssemblyPlugin.defaultUniversalScript(
      Seq("-Dlogback.configurationFile=cmdline-logback.xml"))),
  )

lazy val api = Project(appName + "-api", file("modules/api"))
  .enablePlugins(play.sbt.PlayScala)
  .disablePlugins(SbtVuefy, SbtConcat, SbtDigest, SbtGzip, SbtUglify, SbtSassify, AssemblyPlugin)
  .settings(libraryDependencies += "org.everit.json" % "org.everit.json.schema" % "1.3.0")
  .settings(commonSettings ++ webAppSettings)
  .dependsOn(portal % "test->test;compile->compile")

lazy val admin = Project(appName + "-admin", file("modules/admin"))
  .enablePlugins(play.sbt.PlayScala)
  .disablePlugins(SbtUglify, AssemblyPlugin)
  .settings(libraryDependencies ++= adminDependencies)
  .settings(commonSettings ++ webAppSettings)
  .settings(
    // NB: using GZIP here gives issues with duplicate mappings I have yet to figure out...
    // We can't use uglify because it doesn't handle ES6 Javascript
    // Finally, concat is not necessary because there's only one main admin.js
    Assets / pipelineStages := Seq(digest),
    // The commands that triggers production build (as in `webpack -p`)
    Assets / VueKeys.vuefy / VueKeys.prodCommands := Set("stage"),
    // The location of the webpack binary.
    Assets / VueKeys.vuefy / VueKeys.webpackBinary := "./node_modules/.bin/webpack",
    // The location of the webpack configuration.
    Assets / VueKeys.vuefy / VueKeys.webpackConfig := "./webpack.config.js",
  )
  .dependsOn(api % "test->test;compile->compile")
  .dependsOn(xquery, xslt)

lazy val guides = Project(appName + "-guides", file("modules/guides"))
  .enablePlugins(play.sbt.PlayScala)
  .disablePlugins(SbtVuefy, SbtConcat, SbtDigest, SbtGzip, SbtUglify, AssemblyPlugin)
  .settings(commonSettings ++ webAppSettings)
  .dependsOn(admin)

// Solr search engine implementation.
lazy val solr = Project(appName + "-solr", file("modules/solr"))
  .disablePlugins(PlayScala, AssemblyPlugin)
  .settings(commonSettings: _*)
  .dependsOn(core % "test->test;compile->compile")

lazy val main = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala)
  .enablePlugins(LauncherJarPlugin)
  .enablePlugins(UniversalDeployPlugin)
  .disablePlugins(SbtVuefy, SbtConcat, SbtDigest, SbtGzip, SbtUglify, AssemblyPlugin)
  .settings(libraryDependencies ++= coreDependencies ++ testDependencies)
  .settings(commonSettings ++ webAppSettings ++ resourceSettings)
  .settings(
    skip in publish := false,

    // Tell sbt that we only want a tgz, not a zip
    makeDeploymentSettings(Universal, packageBin in Universal, "tgz"),

    // Remove top-level directory from package zip
    topLevelDirectory := None,

    // Allow overwriting
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),

    // Publishing
    publishTo := {
      val repo = "https://dev.ehri-project.eu/artifactory/"
      if (isSnapshot.value)
        Some("EHRI Snapshots" at repo + "libs-snapshot-local")
      else
        Some("EHRI Releases"  at repo + "libs-release-local")
    },
    // If publication fails, check release credentials in ~/.sbt/.credentials
    credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  )
  .dependsOn(portal % "test->test;compile->compile", admin, guides, api, solr)
  .aggregate(backend, core, admin, portal, guides, api, solr, xquery, xslt)

