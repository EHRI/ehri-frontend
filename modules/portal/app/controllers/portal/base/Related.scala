package controllers.portal.base

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.generic.Read
import models.{Annotation, ContentType, UserProfile}
import play.api.libs.json.JsString
import play.api.mvc._
import services.cypher.CypherService
import utils.Page

import scala.concurrent.Future


trait Related[MT] extends Read[MT] {
  this: PortalController =>

  def cypher: CypherService
  implicit def mat: Materializer

  private def relatedItems(id: String)(implicit ct: ContentType[MT]): Future[Seq[String]] = cypher.rows(
    """
      |MATCH (m:_Entity {__id:$id})
      |     <-[:hasLinkTarget]-(link:Link)
      |     -[:hasLinkTarget]->(t)
      |WHERE m <> t
      |RETURN DISTINCT(t.__id)
    """.stripMargin, params = Map("id" -> JsString(id))
  ).collect { case JsString(related) :: Nil => related }.runWith(Sink.seq)

  case class ItemRelatedRequest[A](
    item: MT,
    annotations: Page[Annotation],
    links: Seq[String],
    watched: Seq[String],
    userOpt: Option[UserProfile],
    request: Request[A]                                
  ) extends WrappedRequest[A](request)
    with WithOptionalUser
  
  def GetItemRelatedAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemRelatedRequest, AnyContent] =
    ItemPermissionAction(id) andThen new CoreActionTransformer[ItemPermissionRequest, ItemRelatedRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemRelatedRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val watchedF: Future[Seq[String]] = watchedItemIds(request.userOpt.map(_.id))
        val annotationF: Future[Page[Annotation]] = userDataApi.annotations[Annotation](id)
        val linksF: Future[Seq[String]] = relatedItems(id)
        for {
          watched <- watchedF
          annotations <- annotationF
          links <- linksF
        } yield ItemRelatedRequest(request.item, annotations, links, watched, request.userOpt, request)
      }
    }
}
