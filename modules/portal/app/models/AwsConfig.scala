package models

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class AwsConfig(
  region: String,
  instance: String,
  accessKey: String,
  secret: String
)

object AwsConfig {
  def fromConfig(fallback: Map[String,String] = Map.empty)(implicit app: play.api.Application): AwsConfig = {
    def getString(key: String): String = app.configuration
      .getString(key)
      .orElse(fallback.get(key))
      .getOrElse(sys.error(s"Invalid configuration: missing key: $key"))

    new AwsConfig(
      getString("s3.region"),
      getString("aws.instance"),
      getString("aws.accessKeyId"),
      getString("aws.secretKey")
    )
  }
}
