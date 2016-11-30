package services.storage

case class AwsConfig(
  region: String,
  accessKey: String,
  secret: String
)

object AwsConfig {
  def fromConfig(config: play.api.Configuration, fallback: Map[String,String] = Map.empty): AwsConfig = {
    def getString(key: String): String = config
      .getOptional[String](key)
      .orElse(fallback.get(key))
      .getOrElse(sys.error(s"Invalid configuration: missing key: $key"))

    new AwsConfig(
      getString("s3.region"),
      getString("aws.accessKeyId"),
      getString("aws.secretKey")
    )
  }
}
