package models

import play.api.libs.json.{Json, Writes}

case class GeoPoint(
  latitude: BigDecimal,
  longitude: BigDecimal
) {
  override def toString: String = f"${latitude}%1.4f/${longitude}%1.4f"
}

object GeoPoint {
  implicit val _writes: Writes[GeoPoint] = Writes { p =>
    Json.obj(
      "type" -> "Point",
      "coordinates" -> Json.arr(p.latitude, p.longitude)
    )
  }
}

