package controllers.base

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import controllers.generic.Search
import play.api.libs.json.JsString
import services.cypher.CypherService

import scala.concurrent.Future


trait SearchRelated {
  this: Search =>

  def cypher: CypherService
  implicit def mat: Materializer

  protected def relatedItems(id: String): Future[Seq[String]] = cypher.rows(
    """
      |MATCH (m:_Entity {__id:$id})
      |     <-[:hasLinkTarget]-(link:Link)
      |     -[:hasLinkTarget]->(t)
      |WHERE m <> t
      |RETURN DISTINCT(t.__id)
    """.stripMargin, params = Map("id" -> JsString(id))
  ).collect { case JsString(related) :: Nil => related }.runWith(Sink.seq)
}
