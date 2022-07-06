package controllers.portal.base

import akka.stream.Materializer
import controllers.base.SearchRelated
import controllers.generic.{Read, Search}
import models.{Annotation, ContentType, UserProfile}
import play.api.mvc._
import services.cypher.CypherService
import utils.Page

import scala.concurrent.Future


trait Related[MT] extends SearchRelated with Read[MT] {
  this: PortalController with Search =>

  def cypher: CypherService
  implicit def mat: Materializer

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
