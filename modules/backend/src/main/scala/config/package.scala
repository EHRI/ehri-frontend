import play.api.Configuration
import play.api.http.HeaderNames

import java.util.Base64

package object config {

  /**
    * Get the host name of a service.
    */
  def serviceHost(name: String, config: Configuration): String = {
    val host = config.get[String](s"services.$name.host")
    val port = config.get[Int](s"services.$name.port")
    val secure = config.getOptional[Boolean](s"services.$name.secure").getOrElse(false)

    s"http${if (secure) "s" else ""}://$host:$port"
  }

  /**
    * Get the base URL of a service, including the hostname
    */
  def serviceBaseUrl(name: String, config: play.api.Configuration): String = {
    val mountPoint = config.get[String](s"services.$name.mountPoint")
    serviceHost(name, config) + "/" + mountPoint
  }

  /**
    * Get a service's authentication parameters.
    */
  def serviceAuth(name: String, config: play.api.Configuration): Option[(String, String)] = for {
    username <- config.getOptional[String](s"services.$name.username")
    password <- config.getOptional[String](s"services.$name.password")
  } yield (username, password)

  /**
    * Get a service's authentication headers.
    */
  def serviceAuthHeaders(name: String, config: play.api.Configuration): Seq[(String, String)] =
    serviceAuth(name, config).toSeq.map { case (username, password) =>
      val credentials = Base64.getEncoder.encodeToString(s"$username:$password".getBytes)
      HeaderNames.AUTHORIZATION -> s"Basic $credentials"
    }
}
