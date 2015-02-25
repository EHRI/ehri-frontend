
import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.rjs.Import._
import net.ground5hark.sbt.concat.Import._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web._
import play.Play.autoImport._
import play.PlayImport.PlayKeys._
import play.twirl.sbt.Import.TwirlKeys.templateImports
import sbt.Keys._
import sbt._

object ApplicationBuild extends Build {

  parallelExecution in ThisBuild := false
  logBuffered := false

  val appName = "docview"
  val appVersion = "1.0.3-SNAPSHOT"

  val backendDependencies = Seq(
    ws,
    cache,

    // Ontology
    "ehri-project" % "ehri-definitions" % "0.1-SNAPSHOT",

    // The ever-vital Joda time
    "joda-time" % "joda-time" % "2.1"
  )

  val backendTestDependencies = Seq(
    "org.neo4j" % "neo4j-kernel" % "1.9.9" % "test" classifier "tests" classifier "",
    "org.neo4j.app" % "neo4j-server" % "1.9.9" % "test" classifier "tests" classifier "",
    "org.hamcrest" % "hamcrest-all" % "1.3" % "test",

    // This is necessary to allow the Neo4j server to start
    "com.sun.jersey" % "jersey-core" % "1.9" % "test",

    // We need the backend code to test against, but exclude any
    // groovy stuff because a) it's not needed, and b) it has a
    // ton of awkward transitive dependencies
    "ehri-project" % "ehri-frames" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin", "gremlin-groovy"),
    "ehri-project" % "ehri-extension" % "0.1-SNAPSHOT" % "test" classifier "tests" classifier "" exclude("com.tinkerpop.gremlin", "gremlin-groovy")
  )

  val coreDependencies = backendDependencies ++ Seq(
    jdbc,
    anorm,
    filters,

    // Commons IO
    "commons-io" % "commons-io" % "2.4",

    // Injection guff - yep, we're using a beta
    "com.google.inject" % "guice" % "4.0-beta",

    // Authentication
    "jp.t2v" %% "play2-auth" % "0.13.0",

    // Password hashing
    "org.mindrot" % "jbcrypt" % "0.3m",

    // Mysql driver
    "mysql" % "mysql-connector-java" % "5.1.25",

    // Markdown rendering
    "org.pegdown" % "pegdown" % "1.4.2",

    // Mailer...
    "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.0",

    // Time formatting library
    "org.ocpsoft.prettytime" % "prettytime" % "1.0.8.Final",

    // Logging: Janino is necessary for configuring LogBack's regex filter
    "org.codehaus.janino" % "janino" % "2.7.7"
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

  val additionalResolvers = Seq(
    "neo4j-public-repository" at "http://m2.neo4j.org/content/groups/public",
    "Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository",
    "Codahale" at "http://repo.codahale.com",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    Resolver.sonatypeRepo("releases"),
    "EHRI Snapshots" at "http://ehridev.dans.knaw.nl/artifactory/libs-snapshot/"
  )


  val commonSettings = Seq(

    scalaVersion := "2.10.4",

    // Increase the JVM heap and permgen to avoid running
    // out of space during the memory intensive integration
    // tests. Additionally, set the path to the test config
    // file as an env var.
    javaOptions in Test ++= Seq(
      "-Xmx1G",
      "-XX:+CMSClassUnloadingEnabled",
      "-XX:MaxPermSize=256M",
      "-Dconfig.file=conf/test.conf"
    ),

    // Show warnings and deprecations
    scalacOptions in ThisBuild ++= Seq(
      "-encoding", "UTF-8",
      "-Xlint",
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.6"
    ),

    // Allow SBT to tell Scaladoc where to find external
    // api docs if dependencies provide that metadata
    autoAPIMappings := true,

      // Don't execute tests in parallel
    parallelExecution := false,

    // Classes to auto-import into templates
    templateImports in Compile ++= Seq(
      "models.base._",
      "utils.forms._",
      "acl._",
      "defines._",
      "backend.Entity"
    ),

    resolvers ++= additionalResolvers,

    // Auto-import EntityType enum into routes
    routesImport += "defines.EntityType",

    // Test the unmanaged directory to test_lib to pick up
    // the repackaged version of jersey-server that we need to avoid
    // a conflict with Pegdown and asm-4.x
    unmanagedBase in Test := baseDirectory.value / "test_lib",

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

    // Exclude certain conf files (e.g. those containing secret keys)
    // that we do not want packaged
    excludeFilter in unmanagedResources := ("oauth2.conf"
        || "parse.conf" || "aws.conf" || "test.conf")
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
    .enablePlugins(play.PlayScala).settings(
      version := appVersion,
      name := appName + "-core",
      libraryDependencies ++= coreDependencies
  ).settings(commonSettings: _*).dependsOn(backend % "test->test;compile->compile")

  lazy val portal = Project(appName + "-portal", file("modules/portal"))
    .enablePlugins(play.PlayScala)
    .enablePlugins(SbtWeb).settings(
    version := appVersion,
    routesImport += "models.view._",
    libraryDependencies ++= portalDependencies,
    RjsKeys.mainModule := "portal-main",
    pipelineStages := Seq(rjs, concat, digest, gzip),
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
          "js/lib/jquery-1.8.3.js",
          "js/lib/jquery.autosize.js",
          "js/lib/jquery.history.js",
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
  ).settings(commonSettings: _*).dependsOn(core % "test->test;compile->compile")

  lazy val admin = Project(appName + "-admin", file("modules/admin"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion
  ).settings(commonSettings: _*).dependsOn(portal)

  lazy val guides = Project(appName + "-guides", file("modules/guides"))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion
  ).settings(commonSettings: _*).dependsOn(admin)

  // Solr search engine implementation.
  lazy val solr = Project(appName + "-solr", file("modules/solr")).settings(
    libraryDependencies ++= Seq(
      "com.github.seratch" %% "scalikesolr" % "4.10.0"
    ),
    resolvers ++= additionalResolvers,
    version := appVersion
  ).dependsOn(core % "test->test;compile->compile")

  lazy val main = Project(appName, file("."))
    .enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= coreDependencies ++ testDependencies
  ).settings(commonSettings ++ assetSettings: _*)
    .dependsOn(portal % "test->test;compile->compile", admin, guides, solr)
    .aggregate(backend, core, admin, portal, guides, solr)

  override def rootProject = Some(main)
}
