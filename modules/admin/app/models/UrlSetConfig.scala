package models

import play.api.libs.json.Reads

case class UrlNameMap(url: String, name: String)

case class UrlSetConfig(
  urlMap: Seq[(String, String)],
  auth: Option[BasicAuthConfig] = None,
  headers: Option[Seq[(String, String)]] = None,
) extends HarvestConfig {
  override def src: ImportDataset.Src.Value = ImportDataset.Src.UrlSet

  def urls: Seq[UrlNameMap] = urlMap.map { case (url, name) => UrlNameMap(url, name) }

  def names: Seq[String] = urlMap.map(_._2)

  def duplicates: Seq[(Int, Int)] = for {
    i <- names.indices
    j <- names.indices
    if i < j && names(i) == names(j)
  } yield (i, j)
}

object UrlSetConfig {
  val URLS = "urlMap"
  val AUTH = "auth"
  val HEADERS = "headers"

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val _reads: Reads[UrlSetConfig] = (
    (__ \ URLS).read[Seq[(String, String)]](Reads.seq(Reads.Tuple2R[String, String](
      Reads.filter(JsonValidationError("errors.invalidUrl"))(forms.isValidUrl),
      Reads.pattern("^[\\w.\\-_]+$".r, "harvesting.error.invalidFileName")))) and
    (__ \ AUTH).readNullable[BasicAuthConfig] and
    (__ \ HEADERS).readNullable[Seq[(String, String)]]
  ) (UrlSetConfig.apply _)

  implicit val _writes: Writes[UrlSetConfig] = Json.writes[UrlSetConfig]
  implicit val _format: Format[UrlSetConfig] = Format(_reads, _writes)
}



