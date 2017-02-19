package backend.aws

case class AwsConfig(
  region: String,
  instance: String,
  accessKey: String,
  secret: String
)

object AwsConfig {
  def fromConfig(config: play.api.Configuration, fallback: Map[String,String] = Map.empty): AwsConfig = {
    def getString(key: String): String = config
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
