package global

import java.io.File
import javax.inject.Inject

import play.api.mvc.RequestHeader

case class AppGlobalConfig @Inject()(configuration: play.api.Configuration) extends GlobalConfig

trait GlobalConfig {

  def configuration: play.api.Configuration

  /**
    * Default group(s) new users belong to.
    */
  def defaultPortalGroups: Seq[String] = configuration
    .getOptional[Seq[String]]("ehri.portal.defaultUserGroups")
    .getOrElse(Seq.empty)

  /**
    * Whether new users are signed up for messaging or not.
    */
  def canMessage: Boolean = configuration
    .getOptional[Boolean]("ehri.users.messaging.default")
    .getOrElse(false)

  def minPasswordLength: Int = configuration
    .getOptional[Int]("ehri.passwords.minLength")
    .getOrElse(6)

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   */
  def isTestMode: Boolean = configuration.getOptional[Boolean]("ehri.testing").getOrElse(true)

  def isStageMode: Boolean = configuration.getOptional[Boolean]("ehri.staging").getOrElse(false)

  def isEmbedMode(implicit req: RequestHeader): Boolean =
    req.getQueryString("embed").map(_.toLowerCase).contains("true")

  lazy val https: Boolean =
    configuration.getOptional[Boolean]("ehri.https").getOrElse(false)

  lazy val skipRecaptcha: Boolean =
    configuration.getOptional[Boolean]("recaptcha.skip").getOrElse(false)

  lazy val analyticsEnabled: Boolean =
    configuration.getOptional[Boolean]("analytics.enabled").getOrElse(false)

  lazy val logMessageMaxLength: Int =
    configuration.getOptional[Int]("ehri.logMessage.maxLength").getOrElse(400)

  lazy val analyticsId: Option[String] =
    configuration.getOptional[String]("analytics.trackingId")

  lazy val mapsApiKey: Option[String] =
    configuration.getOptional[String]("google.maps.browserApiKey")

  lazy val languages: Seq[String] =
    configuration.getOptional[Seq[String]]("play.i18n.langs").getOrElse(Seq.empty)

  def protocol(implicit req: RequestHeader): String =
    "http" + (if (req.secure) "s" else "") + "://"

  def absoluteURL(implicit req: RequestHeader): String =
    protocol + req.host + req.uri

  def absolutePath(implicit req: RequestHeader): String =
    protocol + req.host + req.path

  // Set readonly mode...
  private lazy val readOnlyFile: Option[File] = configuration
    .getOptional[String]("ehri.readonly.file")
    .map(new File(_))

  // Set maintenance mode...
  private lazy val maintenanceFile: Option[File] = configuration
    .getOptional[String]("ehri.maintenance.file")
    .map(new File(_))

  // Read a stock message
  private lazy val messageFile: Option[File] = configuration
    .getOptional[String]("ehri.message.file")
    .map(new File(_))

  private lazy val ipFilterFile: Option[File] = configuration.getOptional[String]("ehri.ipfilter.file")
    .map(new File(_))

  def readOnly: Boolean = readOnlyFile.exists(file => file.isFile && file.exists)

  def maintenance: Boolean = maintenanceFile.exists(file => file.isFile && file.exists)

  def message: Option[String] = {
    messageFile.flatMap { f =>
      if (f.isFile && f.exists()) {
        import org.apache.commons.io.FileUtils
        Some(FileUtils.readFileToString(f, "UTF-8"))
      } else None
    }
  }

  def ipFilter: Option[Seq[String]] = {
    ipFilterFile.flatMap { f =>
      if (f.isFile && f.exists()) {
        import org.apache.commons.io.FileUtils
        val ips: Seq[String] = FileUtils.readFileToString(f, "UTF-8").split('\n').toSeq
        if (ips.isEmpty) None
        else Some(ips)
      } else None
    }
  }
}
