package controllers.generic

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models._
import play.api.data.Form
import play.api.libs.json.{JsValue, Writes, JsError, Json}
import utils.search._
import play.api.Play.current
import play.api.cache.Cache
import scala.concurrent.Future.{successful => immediate}
import utils.search.SearchHit
import play.api.mvc.Result
import backend.{BackendReadable, BackendContentType}

/**
 * Class representing an access point link.
 * @param target id of the destination item
 * @param `type`  type field, i.e. associative
 * @param description description of link
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

  def linkSelectAction(id: String, toType: EntityType.Value, facets: FacetBuilder = emptyFacets)(
      f: MT => ItemPage[(AnyModel,SearchHit)] => SearchParams => List[AppliedFacet] => EntityType.Value => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>
      find[AnyModel](facetBuilder = facets, defaultParams = SearchParams(entities = List(toType), excludes=Some(List(id)))).map { r =>
        f(item)(r.page)(r.params)(r.facets)(toType)(userOpt)(request)
      }
    }
  }

  def linkAction(id: String, toType: EntityType.Value, to: String)(f: MT => AnyModel => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>

      getEntityT[AnyModel](AnyModel.resourceFor(toType), to) { srcitem =>
        f(item)(srcitem)(userOpt)(request)
      }
    }
  }

  def linkPostAction(id: String, toType: EntityType.Value, to: String)(
      f: Either[(MT, AnyModel,Form[LinkF]),Link] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {

    implicit val linkWrites: Writes[LinkF] = models.LinkF.linkWrites

    withItemPermission.async[MT](id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>
      Link.form.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          getEntityT[AnyModel](AnyModel.resourceFor(toType), to) { srcitem =>
            f(Left((item,srcitem,errorForm)))(userOpt)(request)
          }
        },
        ann => backend.linkItems(id, to, ann).map { ann =>
          f(Right(ann))(userOpt)(request)
        }
      )
    }
  }

  def linkMultiAction(id: String)(f: MT => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def linkPostMultiAction(id: String)(
      f: Either[(MT,Form[List[(String,LinkF,Option[String])]]),Seq[Link]] => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]): Action[AnyContent] = {
    withItemPermission.async[MT](id, PermissionType.Update) { item => implicit userOpt => implicit request =>
      val multiForm: Form[List[(String,LinkF,Option[String])]] = Link.multiForm
      multiForm.bindFromRequest.fold(
        errorForm => immediate(f(Left((item,errorForm)))(userOpt)(request)),
        links => backend.linkMultiple(id, links).map { outLinks =>
          f(Right(outLinks))(userOpt)(request)
        }
      )
    }
  }

  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   */
  def createLink(id: String, apid: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[JsValue,MT](parse.json, id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>
      request.body.validate[AccessPointLink].fold(
        errors => immediate(BadRequest(JsError.toFlatJson(errors))),
        ann => {
          val link = new LinkF(id = None, linkType=LinkF.LinkType.Associative, description=ann.description)
          backend.linkItems(id, ann.target, link, Some(apid)).map { ann =>
            Cache.remove(id)
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
  def createMultipleLinks(id: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[JsValue, MT](parse.json, id, PermissionType.Annotate) {
        item => implicit userOpt => implicit request =>
      request.body.validate[List[AccessPointLink]].fold(
        errors => immediate(BadRequest(JsError.toFlatJson(errors))),
        anns => {
          val links = anns.map(ann =>
            (ann.target, new LinkF(id = None, linkType=ann.`type`.getOrElse(LinkF.LinkType.Associative), description=ann.description), None)
          )
          backend.linkMultiple(id, links).map { newLinks =>
            Cache.remove(id)
            Created(Json.toJson(newLinks.map(_.model)))
          }
        }
      )
    }
  }

  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   */
  def createAccessPoint(id: String, did: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[JsValue,MT](parse.json, id, PermissionType.Update) {
        item => implicit userOpt => implicit request =>
      request.body.validate[AccessPointF](AccessPointLink.accessPointFormat).fold(
        errors => immediate(BadRequest(JsError.toFlatJson(errors))),
        ap => backend.createAccessPoint(id, did, ap).map { ann =>
          Created(Json.toJson(ann)(Json.format[AccessPointF]))
        }
      )
    }
  }

  /**
   * Fetch links for a given item.
   */
  def getLinksAction(id: String)(f: Seq[Link] => Option[UserProfile] => Request[AnyContent] => Result) = {
    userProfileAction.async { implicit  userOpt => implicit request =>
      backend.getLinksForItem(id).map { links =>
        f(links)(userOpt)(request)
      }
    }
  }

  /**
   * Get the link, if any, for a document and an access point.
   */
  def getLink(id: String, apid: String) = getLinksAction(id) { linkList => implicit userOpt => implicit request =>
    val linkOpt = linkList.find(link => link.bodies.exists(b => b.id == Some(apid)))
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

  /**
   * Delete an access point by ID.
   */
  def deleteAccessPoint(id: String, did: String, accessPointId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) {
        bool => implicit userOpt => implicit request =>
      backend.deleteAccessPoint(id, did, accessPointId).map { ok =>
        Cache.remove(id)
        Ok(Json.toJson(true))
      }
    }
  }

  /**
   * Delete a link.
   */
  def deleteLink(id: String, linkId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate) {
        bool => implicit userOpt => implicit request =>
      backend.deleteLink(id, linkId).map { ok =>
        Cache.remove(id)
        Ok(Json.toJson(ok))
      }
    }
  }

  /**
   * Delete a link and an access point in one go.
   */
  def deleteLinkAndAccessPoint(id: String, did: String, accessPointId: String, linkId: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Annotate) {
        bool => implicit userOpt => implicit request =>
      for {
        oneOk <- backend.deleteLink(id, linkId)
        _ <- backend.deleteAccessPoint(id, did, accessPointId) if oneOk
      } yield {
        Cache.remove(id)
        Ok(Json.toJson(true))
      }
    }
  }
}

