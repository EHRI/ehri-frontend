package services.search

import defines.EntityType
import play.api.libs.json.{Format, Json, Writes}
import utils.EnumUtils


case object SearchField extends Enumeration {
  type Field = Value
  val Identifier = Value("identifier")
  val Title = Value("title")
  val Creator = Value("creator")
  val Person = Value("person")
  val Place = Value("place")
  val Subject = Value("subject")
  val Address = Value("address")

  implicit val _fmt: Format[SearchField.Value] = EnumUtils.enumFormat(SearchField)
}

case object SearchSort extends Enumeration {
  type Sort = Value
  val Id = Value("id")
  val Score = Value("score")
  val Name = Value("name")
  val DateNewest = Value("updated")
  val Country = Value("country")
  val Holder = Value("holder")
  val Location = Value("location")
  val Detail = Value("detail")
  val ChildCount = Value("holdings")

  implicit val _fmt: Format[SearchSort.Value] = utils.EnumUtils.enumFormat(SearchSort)
}

case object SearchMode extends Enumeration {
  type Type = Value
  val DefaultAll = Value("all")
  val DefaultNone = Value("none")

  implicit val _fmt: Format[SearchMode.Value] = utils.EnumUtils.enumFormat(SearchMode)
}

case object FacetSort extends Enumeration {
  type FacetSort = Value
  val Name = Value("name")
  val Count = Value("count")
  val Fixed = Value("fixed")

  implicit val _fmt: Format[FacetSort.Value] = utils.EnumUtils.enumFormat(FacetSort)
}

case class BoundingBox(
  latMin: BigDecimal,
  lonMin: BigDecimal,
  latMax: BigDecimal,
  lonMax: BigDecimal
) {
  // NB: should we ensure lat/lon min is always less then max?
  // For now just check the range...
  def isValid: Boolean = latMin >= -90 && latMin <= 90 &&
                          lonMin >= -180 && lonMin <= 180 &&
                          latMax >= -90 && latMax <= 90 &&
                          lonMax >= -180 && lonMax <= 180
  override def toString: String = s"$latMin,$lonMin,$latMax,$lonMax"
}

object BoundingBox {
  def fromString(s: String): Either[String, BoundingBox] = {
    val err = s"Invalid bounding box format '$s', should be lat-min,lon-min,lat-max,lon-max"
    try {
      s.split(",").toList.map(BigDecimal.exact) match {
        case l1 :: l2 :: l3:: l4 :: Nil =>
          val box = BoundingBox(l1, l2, l3, l4)
          if (box.isValid) Right(box) else Left(err)
        case _ => Left(err)
      }
    } catch {
      case _: Throwable => Left(err)
    }
  }

  implicit val writes: Writes[BoundingBox] = Json.writes[BoundingBox]
}

case class LatLng(
  lat: BigDecimal,
  lon: BigDecimal
) {
  def isValid: Boolean = lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180

  override def toString: String = s"$lat,$lon"
}

object LatLng {
  def fromString(s: String): Either[String, LatLng] = {
    val err = s"Invalid point format '$s', should be lat,lon"
    try {
      s.split(",").toList.map(BigDecimal.exact) match {
        case l1 :: l2 :: Nil =>
          val point = LatLng(l1, l2)
          if (point.isValid) Right(point) else Left(err)
        case _ => Left(err)
      }
    } catch {
      case _: Throwable => Left(err)
    }
  }

  implicit val writes: Writes[LatLng] = Json.writes[LatLng]
}

/**
  * Class encapsulating the parameters of a Solr search.
  */
case class SearchParams(
  query: Option[String] = None,
  sort: Option[SearchSort.Value] = None,
  entities: Seq[EntityType.Value] = Nil,
  fields: Seq[SearchField.Value] = Nil,
  facets: Seq[String] = Nil,
  excludes: Seq[String] = Nil,
  filters: Seq[String] = Nil,
  bbox: Option[BoundingBox] = None,
  latLng: Option[LatLng] = None
) {
  /**
    * Is there an active constraint on these params?
    */
  def isFiltered: Boolean = !query.forall(_.trim.isEmpty)
}

object SearchParams {
  val SORT = "sort"
  val QUERY = "q"
  val FIELD = "qf"
  val FACET = "facet"
  val ENTITY = "st"
  val EXCLUDE = "ex"
  val FILTERS = "f"
  val BBOX = "bbox"
  val LATLNG = "latlng"

  def empty: SearchParams = SearchParams()

  implicit val writes: Writes[SearchParams] = Json.writes[SearchParams]
}
