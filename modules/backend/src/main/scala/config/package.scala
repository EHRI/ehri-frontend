import play.api.Configuration

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
}
