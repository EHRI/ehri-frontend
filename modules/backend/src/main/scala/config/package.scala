package object config {

  def serviceBaseUrl(name: String, config: play.api.Configuration): String = {
    def nameKey(key: String) = s"services.$name.$key"

    val host = config.get[String](nameKey("host"))
    val port = config.get[Int](nameKey("port"))
    val mountPoint = config.get[String](nameKey("mountPoint"))
    val secure = config.getOptional[Boolean](nameKey("secure")).getOrElse(false)

    s"http${if (secure) "s" else ""}://$host:$port/$mountPoint"
  }
}
