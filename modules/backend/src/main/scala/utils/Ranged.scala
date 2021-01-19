package utils

import services.data.Constants._

object Ranged {
  def streamHeader: (String, String) = STREAM_HEADER_NAME -> true.toString
}

trait Ranged {
  def offset: Int

  def limit: Int

  def hasLimit: Boolean = limit >= 0

  def queryParams: Seq[(String, String)] =
    Seq(OFFSET_PARAM -> offset.toString, LIMIT_PARAM -> limit.toString)

  def headers: Seq[(String, String)] =
    if (hasLimit) Seq.empty else Seq(Ranged.streamHeader)
}
