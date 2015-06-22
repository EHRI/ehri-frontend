package object utils {

  def serviceBaseUrl(name: String, config: play.api.Configuration): String = {
    def nameKey(key: String) = s"services.$name.$key"
    def configError(key: String) = sys.error(s"Missing configuration key: " + nameKey(key))

    (for {
      host <- config.getString(nameKey("host"))
      port = config.getInt(nameKey("port")).getOrElse(configError("port"))
      mountPoint = config.getString(nameKey("mountPoint")).getOrElse(configError("mountPoint"))
      secure = config.getBoolean(nameKey("secure")).getOrElse(false)
    } yield {
        s"http${if(secure) "s" else ""}://$host:$port/$mountPoint"
      }).getOrElse(configError(nameKey("host")))
  }
}
