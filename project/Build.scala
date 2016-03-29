
import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.rjs.Import._
import net.ground5hark.sbt.concat.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web._
import play.sbt.Play.autoImport._
import play.twirl.sbt.Import.TwirlKeys.templateImports
import sbt.Keys._
import sbt._
import play.sbt.routes.RoutesKeys._


object Build extends Build {

  parallelExecution in ThisBuild := false
  logBuffered := false

  val projectScalaVersion = "2.11.7"
  val appName = "docview"
  val appVersion = "1.0.5-SNAPSHOT"

  val backendVersion = "0.13.0-SNAPSHOT"
  val neo4jVersion = "2.3.2"
  val jerseyVersion = "1.19"

  val backendDependencies = Seq(
    ws,
    cache,

    // Ontology
    "ehri-project" % "ehri-definitions" % backendVersion,

    // The ever-vital Joda time
    "joda-time" % "joda-time" % "2.8.1"
  )

  val backendTestDependencies = Seq(
    specs2 % Test,
    "org.neo4j" % "neo4j-kernel" % neo4jVersion % "test" classifier "tests" classifier "" exclude("org.mockito", "mockito-core"),
    "org.neo4j" % "neo4j-io" % neo4jVersion % "test" classifier "tests" classifier "" exclude("org.mockito", "mockito-core"),
    "org.neo4j.app" % "neo4j-server" % neo4jVersion % "test" classifier "tests" classifier "" exclude("org.mockito",
      "mockito-core"),
    "org.hamcrest" % "hamcrest-all" % "1.3" % "test",

    // This is necessary to allow the Neo4j server to start
    "com.sun.jersey" % "jersey-core" % jerseyVersion % "test",
    "com.sun.jersey" % "jersey-server" % jerseyVersion % "test",

    // We need the backend code to test against, but exclude any
    // groovy stuff because a) it's not needed, and b) it has a
    // ton of awkward transitive dependencies
    "ehri-project" % "ehri-core" % backendVersion % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin",
      "gremlin-groovy") exclude("org.mockito", "mockito-core"),
    "ehri-project" % "ehri-ws" % backendVersion % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin", "gremlin-groovy") exclude("org.mockito", "mockito-core")
  )


  val coreDependencies = backendDependencies ++ Seq(
    jdbc,
    evolutions,
    filters,

    // Anorm DB lib
    "com.typesafe.play" %% "anorm" % "2.4.0",

    // Commons IO
    "commons-io" % "commons-io" % "2.4",

    // Authentication
    "jp.t2v" %% "play2-auth" % "0.14.1",

    // Password hashing
    "org.mindrot" % "jbcrypt" % "0.3m",

    // Mysql driver. Note: version 5.1.36 is
    // incompatible with our old version of MySQL:
    // https://bugs.mysql.com/bug.php?id=77665
    "mysql" % "mysql-connector-java" % "5.1.35",

    // PostgreSQL
    //"org.postgresql" % "postgresql" % "9.4-1204-jdbc42",

    // Markdown rendering
    "org.pegdown" % "pegdown" % "1.6.0",

    // HTML sanitising...
    "org.jsoup" % "jsoup" % "1.8.3",

    // Mailer...
    "com.typesafe.play" %% "play-mailer" % "3.0.1",

    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "3.2.7.Final",

    // Logging: Janino is necessary for configuring LogBack's regex filter
    "org.codehaus.janino" % "janino" % "2.7.7"
  )

  val portalDependencies = Seq(
    "net.coobird" % "thumbnailator" % "[0.4, 0.5)",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-csv" % "2.6.4",

    // EHRI indexing tools
    "ehri-project" % "index-data-converter" % "1.1.5" exclude("log4j", "log4j") exclude ("org.slf4j",
      "slf4j-log4j12"),
    "com.sun.jersey" % "jersey-core" % jerseyVersion,

    // S3 Upload plugin
    "com.github.seratch" %% "awscala" % "0.3.+"
  )

  val testDependencies = backendTestDependencies ++ Seq(
    specs2 % Test,
    "jp.t2v" %% "play2-auth-test" % "0.14.1" % "test"
  )

  val additionalResolvers = Seq(
    Resolver.mavenLocal,
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "EHRI Snapshots" at "http://ehridev.dans.knaw.nl/artifactory/libs-snapshot/",
    "EHRI Releases" at "http://ehridev.dans.knaw.nl/artifactory/libs-release/",
    // For this annoying issue: https://github.com/etorreborre/specs2/issues/347
    "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
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
      "utils.forms._",
      "acl._",
      "defines._",
      "backend.Entity"
    ),

    // Auto-import EntityType enum into routes
    routesImport ++= Seq(
      "defines.EntityType",
      "defines.binders._"
    ),

    // SBT magic: http://stackoverflow.com/a/12772739/285374
    // pick up additional resources in test
    resourceDirectory in Test <<= baseDirectory apply {
      (baseDir: File) => baseDir / "test/resources"
    },

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

  val assetSettings = Seq(
  )

  lazy val backend = Project(appName + "-backend", file("modules/backend"))
    .settings(
      name := appName + "-backend",
      libraryDependencies ++= backendDependencies ++ backendTestDependencies,
      resolvers ++= additionalResolvers
  )

  lazy val core = Project(appName + "-core", file("modules/core"))
    .enablePlugins(play.sbt.PlayScala).settings(
      name := appName + "-core",
      libraryDependencies ++= coreDependencies
  ).settings(commonSettings: _*).dependsOn(backend % "test->test;compile->compile")

  lazy val portal = Project(appName + "-portal", file("modules/portal"))
    .enablePlugins(play.sbt.PlayScala)
    .enablePlugins(SbtWeb).settings(
    routesImport += "models.view._",
    libraryDependencies ++= portalDependencies,
    RjsKeys.mainModule := "portal-main",
    //pipelineStages := Seq(rjs, concat, digest, gzip),
    pipelineStages in Assets := Seq(concat, digest, gzip),
    Concat.groups := Seq(
     "css/portal-all.css" -> group(
        Seq(
          "css/font-awesome.css",
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
          "bootstrap/js/bootstrap.js",
          "js/portal.js"
        )
      ),
      "js/script-post-signedin.js" -> group(
        Seq(
          "js/lib/jquery.cookie.js",
          "js/lib/jquery.placeholder.js",
          "bootstrap/js/bootstrap.js",
          "js/portal.js",
          "js/portal-signedin.js"
        )
      )
    )
  ).settings(commonSettings ++ webAppSettings: _*).dependsOn(core % "test->test;compile->compile")

  lazy val admin = Project(appName + "-admin", file("modules/admin"))
    .enablePlugins(play.sbt.PlayScala).settings(
    libraryDependencies += specs2 % Test
  ).settings(commonSettings ++ webAppSettings: _*).dependsOn(portal)

  lazy val guides = Project(appName + "-guides", file("modules/guides"))
    .enablePlugins(play.sbt.PlayScala).settings(
  ).settings(commonSettings ++ webAppSettings: _*).dependsOn(admin)

  // Solr search engine implementation.
  lazy val solr = Project(appName + "-solr", file("modules/solr")).settings(
    libraryDependencies ++= Seq(
      "com.github.seratch" %% "scalikesolr" % "4.10.0"
    )).settings(commonSettings: _*)
  .dependsOn(core % "test->test;compile->compile")

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.sbt.PlayScala).settings(

      // Instantiate controllers via dependency injection
      routesGenerator := InjectedRoutesGenerator,

      libraryDependencies ++= coreDependencies ++ testDependencies
  ).settings(commonSettings ++ assetSettings: _*)
    .dependsOn(portal % "test->test;compile->compile", admin, guides, solr)
    .aggregate(backend, core, admin, portal, guides, solr)

  override def rootProject = Some(main)
}
