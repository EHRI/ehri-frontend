package utils.search

import language.postfixOps
import models.json.ClientConvertable

/**
 * Page of search result items
 * @param items
 * @param page
 * @param count
 * @param total
 * @param facets
 * @tparam A
 */
case class ItemPage[+A](
  items: Seq[A] = Seq.empty,
  page: Int = 1,
  count:Int = 0,
  total: Long = 0,
  facets: utils.search.FacetClassList = List.empty,
  spellcheck: Option[(String,String)] = None
) extends utils.AbstractPage[A]


object ItemPage {

  def empty[T]: ItemPage[T] = new ItemPage[T]()

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import play.api.libs.json.util._

  implicit def itemPageWrites[MT](implicit rd: ClientConvertable[MT]): Writes[ItemPage[MT]] = (
    (__ \ "items").lazyWrite[Seq[MT]](Writes.seq(rd.clientFormat)) and
    (__ \ "offset").write[Int] and
    (__ \ "limit").write[Int] and
    (__ \ "total").write[Long] and
    (__ \ "facetClasses").lazyWrite(Writes.list[FacetClass[Facet]](FacetClass.facetClassWrites)) and
    (__ \ "spellcheck").writeNullable(
      (__ \ "given").write[String] and
      (__ \ "correction").write[String]
      tupled
    )
  )(unlift(ItemPage.unapply[MT]))
}

