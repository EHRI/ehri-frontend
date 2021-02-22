package controllers.portal.base

import controllers.generic.Read
import models.{Annotation, ContentType, Link, UserProfile}
import play.api.mvc._
import utils.Page

import scala.concurrent.Future


trait Generic[MT] extends Read[MT] {
  this: PortalController =>

  case class ItemBrowseRequest[A](
    item: MT,
    annotations: Page[Annotation],
    links: Page[Link],
    watched: Seq[String],
    userOpt: Option[UserProfile],
    request: Request[A]                                
  ) extends WrappedRequest[A](request)
    with WithOptionalUser
  
  def GetItemAction(id: String)(implicit ct: ContentType[MT]): ActionBuilder[ItemBrowseRequest, AnyContent] =
    ItemPermissionAction(id) andThen new CoreActionTransformer[ItemPermissionRequest, ItemBrowseRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ItemBrowseRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val watchedF: Future[Seq[String]] = watchedItemIds(request.userOpt.map(_.id))
        val annotationF: Future[Page[Annotation]] = userDataApi.annotations[Annotation](id)
        val linksF: Future[Page[Link]] = userDataApi.links[Link](id)
        for {
          watched <- watchedF
          annotations <- annotationF
          links <- linksF
        } yield ItemBrowseRequest(request.item, annotations, links, watched, request.userOpt, request)
      }
    }
}
