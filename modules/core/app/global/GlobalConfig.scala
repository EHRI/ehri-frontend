package global

import java.io.File
import javax.inject.Inject

case class AppGlobalConfig @Inject()(configuration: play.api.Configuration) extends GlobalConfig

trait GlobalConfig {
  
  def configuration: play.api.Configuration

  /**
   * Flag to indicate whether we're running a testing config or not.
   * This is different from the usual dev/prod run configuration because
   * we might be running experimental stuff on a real life server.
   */
  def isTestMode = configuration.getBoolean("ehri.testing").getOrElse(true)
  def isStageMode = configuration.getBoolean("ehri.staging").getOrElse(false)

  lazy val https =
    configuration.getBoolean("ehri.https").getOrElse(false)

  lazy val skipRecaptcha =
    configuration.getBoolean("recaptcha.skip").getOrElse(false)

  lazy val analyticsEnabled: Boolean =
    configuration.getBoolean("analytics.enabled").getOrElse(false)

  lazy val analyticsId: Option[String] =
    configuration.getString("analytics.trackingId")

  lazy val mapsApiKey: Option[String] =
    configuration.getString("google.maps.browserApiKey")

  import scala.collection.JavaConversions._
  lazy val languages: Seq[String] =
    configuration.getStringList("play.i18n.langs").toSeq.flatten

  // Set readonly mode...
  private lazy val readOnlyFile: Option[File] = configuration.getString("ehri.readonly.file")
      .map(new File(_))

  // Set maintenance mode...
  private lazy val maintenanceFile: Option[File] = configuration.getString("ehri.maintenance.file")
    .map(new File(_))

  // Read a stock message
  private lazy val messageFile: Option[File] = configuration.getString("ehri.message.file")
    .map(new File(_))

  private lazy val ipFilterFile: Option[File] = configuration.getString("ehri.ipfilter.file")
    .map(new File(_))

  def readOnly: Boolean = readOnlyFile.exists(file => file.isFile && file.exists)

  def maintenance: Boolean = maintenanceFile.exists(file => file.isFile && file.exists)

  /**
   * Fetch a config string value.
   */
  def configString(key: String, orElse: String): String =
    configuration.getString(key).getOrElse(orElse)

  /**
   * Fetch a config string list value.
   */
  def configStringList(key: String, orElse: List[String] = Nil): List[String] = {
    import scala.collection.JavaConverters._
    configuration.getStringList(key).map(_.asScala.toList).getOrElse(orElse)
  }


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
