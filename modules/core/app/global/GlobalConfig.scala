package global

import play.api.Play.current
import java.io.File


trait MenuConfig {
  val mainSection: Iterable[(String,String)]
  val adminSection: Iterable[(String,String)]
  val authSection: Iterable[(String,String)]
}

trait GlobalConfig {

  val menuConfig: MenuConfig
  val routeRegistry: RouteRegistry

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   */
  def isTestMode = current.configuration.getBoolean("ehri.testing").getOrElse(true)
  def isStageMode = current.configuration.getBoolean("ehri.staging").getOrElse(false)

  lazy val https =
    current.configuration.getBoolean("securesocial.ssl").getOrElse(false)

  lazy val skipRecaptcha =
    current.configuration.getBoolean("recaptcha.skip").getOrElse(false)

  lazy val analyticsEnabled: Boolean =
    current.configuration.getBoolean("analytics.enabled").getOrElse(false)

  lazy val analyticsId: Option[String] =
    current.configuration.getString("analytics.trackingId")

  lazy val languages: Seq[String] = current.configuration
        .getString("application.langs").map(_.split(",").toSeq).getOrElse(Nil)

  // Set readonly mode...
  private lazy val readOnlyFile: Option[File] = current.configuration.getString("ehri.readonly.file")
      .map(new File(_))

  def readOnly = readOnlyFile.exists(file => file.isFile && file.exists)
}
