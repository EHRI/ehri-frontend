package global

import play.api.Play.current
import java.io.File

trait GlobalConfig {

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   */
  def isTestMode = current.configuration.getBoolean("ehri.testing").getOrElse(true)
  def isStageMode = current.configuration.getBoolean("ehri.staging").getOrElse(false)

  lazy val https =
    current.configuration.getBoolean("ehri.https").getOrElse(false)

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

  // Set maintenance mode...
  private lazy val maintenanceFile: Option[File] = current.configuration.getString("ehri.maintenance.file")
    .map(new File(_))

  // Read a stock message
  private lazy val messageFile: Option[File] = current.configuration.getString("ehri.message.file")
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
}
