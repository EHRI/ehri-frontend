package controllers.generic

import backend.ContentType
import defines._
import models._
import models.base._
import play.api.data.Form
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsError, Json}
import play.api.mvc._
import utils.search.{SearchHit, _}

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
 * Class representing an access point link.
 * @param target id of the destination item
 * @param `type`  type field, i.e. associative
 * @param description descrioption of link
 */
case class AccessPointLink(
  target: String,
  `type`: Option[LinkF.LinkType.Value] = None,
  description: Option[String] = None
)

object AccessPointLink {
  // handlers for creating/listing/deleting links via JSON
  implicit val linkTypeFormat = defines.EnumUtils.enumFormat(LinkF.LinkType)
  implicit val accessPointTypeFormat = defines.EnumUtils.enumFormat(AccessPointF.AccessPointType)
  implicit val accessPointFormat = Json.format[AccessPointF]
  implicit val accessPointLinkReads = Json.format[AccessPointLink]
}


/**
 * Trait for setting visibility on any AccessibleEntity.
 *
 * @tparam MT the entity's build class
 */
trait Linking[MT <: AnyModel] extends Read[MT] with Search {

  // This is used to send the link data back to JSON endpoints...
  private implicit val linkFormatForClient = Json.format[LinkF]

  case class LinkSelectRequest[A](
    item: MT,
    searchResult: SearchResult[(AnyModel, SearchHit)],
    entityType: EntityType.Value,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def LinkSelectAction(id: String, toType: EntityType.Value, facets: FacetBuilder = emptyFacets)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new ActionTransformer[ItemPermissionRequest, LinkSelectRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[LinkSelectRequest[A]] = {
        implicit val req = request
        find[AnyModel](
          facetBuilder = facets,
          defaultParams = SearchParams(excludes=Some(List(id))),
          entities = Seq(toType)
        ).map { r =>
          LinkSelectRequest(request.item, r, toType, request.userOpt, request)
        }
      }
    }

  case class LinkItemsRequest[A](
    from: MT,
    to: AnyModel,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def LinkAction(id: String, toType: EntityType.Value, to: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new ActionTransformer[ItemPermissionRequest, LinkItemsRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[LinkItemsRequest[A]] = {
        implicit val req = request
        userBackend.get[AnyModel](AnyModel.resourceFor(toType), to).map { toItem =>
          LinkItemsRequest(request.item, toItem, request.userOpt, request)
        }
      }
    }

  case class CreateLinkRequest[A](
    from: MT,
    formOrLink: Either[(AnyModel, Form[LinkF]), Link],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def CreateLinkAction(id: String, toType: EntityType.Value, to: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new ActionTransformer[ItemPermissionRequest, CreateLinkRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[CreateLinkRequest[A]] = {
        implicit val req = request
        Link.form.bindFromRequest.fold(
          errorForm => { // oh dear, we have an error...
            userBackend.get[AnyModel](AnyModel.resourceFor(toType), to).map { toItem =>
              CreateLinkRequest(request.item, Left((toItem, errorForm)), request.userOpt, request)
            }
          },
          ann => userBackend.linkItems[MT, Link, LinkF](id, to, ann).map { link =>
            CreateLinkRequest(request.item, Right(link), request.userOpt, request)
          }
        )
      }
    }

  case class MultiLinksRequest[A](
    item: MT,
    formOrLinks: Either[Form[List[(String,LinkF,Option[String])]], Seq[Link]],
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def CreateMultipleLinksAction(id: String)(implicit ct: ContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Annotate) andThen new ActionTransformer[ItemPermissionRequest, MultiLinksRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[MultiLinksRequest[A]] = {
        implicit val req = request
        val multiForm: Form[List[(String,LinkF,Option[String])]] = Link.multiForm
        multiForm.bindFromRequest.fold(
          errorForm => immediate(MultiLinksRequest(request.item, Left(errorForm), request.userOpt, request)),
          links => userBackend.linkMultiple[MT, Link, LinkF](id, links).map { outLinks =>
            MultiLinksRequest(request.item, Right(outLinks), request.userOpt, request)
          }
        )
      }
    }


  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   */
  def createLink(id: String, apid: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Annotate).async(parse.json) { implicit request =>
      request.body.validate[AccessPointLink].fold(
        errors => immediate(BadRequest(JsError.toJson(errors))),
        ann => {
          val link = new LinkF(id = None, linkType=LinkF.LinkType.Associative, description=ann.description)
          userBackend.linkItems[MT, Link, LinkF](id, ann.target, link, Some(apid)).map { ann =>
            Created(Json.toJson(ann.model))
          }
        }
      )
    }
  }

  /**
   * Create a link, via Json, for the object with the given id and a set of
   * other objects.
   */
  def createMultipleLinks(id: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Annotate).async(parse.json) { implicit request =>
      request.body.validate[List[AccessPointLink]].fold(
        errors => immediate(BadRequest(JsError.toJson(errors))),
        anns => {
          val links = anns.map(ann =>
            (ann.target, new LinkF(id = None, linkType=ann.`type`.getOrElse(LinkF.LinkType.Associative), description=ann.description), None)
          )
          userBackend.linkMultiple[MT, Link, LinkF](id, links).map { newLinks =>
            Created(Json.toJson(newLinks.map(_.model)))
          }
        }
      )
    }
  }

  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   */
  def createAccessPoint(id: String, did: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Update).async(parse.json) { implicit request =>
      request.body.validate[AccessPointF](AccessPointLink.accessPointFormat).fold(
        errors => immediate(BadRequest(JsError.toJson(errors))),
        ap => userBackend.createAccessPoint(id, did, ap).map { ann =>
          Created(Json.toJson(ann)(Json.format[AccessPointF]))
        }
      )
    }
  }

  /**
   * Get the link, if any, for a document and an access point.
   */
  def getLink(id: String, apid: String) = OptionalUserAction.async { implicit  request =>
    userBackend.getLinksForItem[Link](id).map { links =>
      val linkOpt = links.find(link => link.bodies.exists(b => b.model.id.contains(apid)))
      val res = for {
        link <- linkOpt
        target <- link.opposingTarget(id)
      } yield new AccessPointLink(
          target = target.id,
          `type` = Some(link.model.linkType),
          description = link.model.description
        )
      Ok(Json.toJson(res))
    }
  }

  /**
   * Delete an access point by ID.
   */
  def deleteAccessPoint(id: String, did: String, accessPointId: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Update).async { implicit request =>
      userBackend.deleteAccessPoint(id, did, accessPointId).map { ok =>
        Ok(Json.toJson(true))
      }
    }
  }

  /**
   * Delete a link.
   */
  def deleteLink(id: String, linkId: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Annotate).async { implicit request =>
      userBackend.deleteLink(id, linkId).map { ok =>
        Ok(Json.toJson(ok))
      }
    }
  }

  /**
   * Delete a link and an access point in one go.
   */
  def deleteLinkAndAccessPoint(id: String, did: String, accessPointId: String, linkId: String)(implicit ct: ContentType[MT]) = {
    WithItemPermissionAction(id, PermissionType.Annotate).async { implicit request =>
      for {
        oneOk <- userBackend.deleteLink(id, linkId)
        _ <- userBackend.deleteAccessPoint(id, did, accessPointId) if oneOk
      } yield Ok(Json.toJson(true))
    }
  }
}

