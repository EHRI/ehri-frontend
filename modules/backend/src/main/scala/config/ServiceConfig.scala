package config

import play.api.Configuration
import play.api.http.HeaderNames

import java.util.Base64

case class ServiceConfig(
  host: String,
  port: Int,
  secure: Boolean,
  mountPoint: String,
  credentials: Option[(String, String)] = None,
) {
  /**
    * Get the host name of a service.
    */
  def hostUrl: String = s"http${if (secure) "s" else ""}://$host:$port"

  /**
    * Get the base URL of a service, including the hostname
    */
  def baseUrl: String = s"$hostUrl/$mountPoint"

  /**
    * Get a service's authentication headers.
    */
  def authHeaders: Seq[(String, String)] = credentials.toSeq.map { case (u, pw) =>
    val creds = Base64.getEncoder.encodeToString(s"$u:$pw".getBytes)
    HeaderNames.AUTHORIZATION -> s"Basic $creds"
  }
}

object ServiceConfig {
  /**
    * Load a service configuration by name.
    */
  def apply(name: String, config: Configuration): ServiceConfig = ServiceConfig(
    config.get[String](s"services.$name.host"),
    config.get[Int](s"services.$name.port"),
    config.getOptional[Boolean](s"services.$name.secure").getOrElse(false),
    config.get[String](s"services.$name.mountPoint"),
    for {
      username <- config.getOptional[String](s"services.$name.username")
      password <- config.getOptional[String](s"services.$name.password")
    } yield (username, password)
  )
}

