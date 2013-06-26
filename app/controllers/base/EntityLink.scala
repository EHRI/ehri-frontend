package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models._
import play.api.data.Form
import rest.LinkDAO
import models.forms.LinkForm
import play.api.mvc.Result
import play.api.libs.json.{Writes, JsError, Json}
import solr.SearchParams
import solr.facet.AppliedFacet
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestReadable


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
trait EntityLink[MT] extends EntityRead[MT] with EntitySearch {

  def linkSelectAction(id: String, toType: String)(
      f: MT => solr.ItemPage[(MetaModel[_],String)] => SearchParams => List[AppliedFacet] => EntityType.Value => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Annotate, contentType) {
        item => implicit userOpt => implicit request =>
      val linkSrcEntityType = EntityType.withName(toType)
      searchAction(defaultParams = Some(SearchParams(entities = List(linkSrcEntityType), excludes=Some(List(id))))) {
          page => params => facets => _ => _ =>
        f(item)(page)(params)(facets)(linkSrcEntityType)(userOpt)(request)
      }(request)
    }
  }

  def linkAction(id: String, toType: String, to: String)(f: MT => MetaModel[_] => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Annotate, contentType) {
        item => implicit userOpt => implicit request =>

      getEntity[MetaModel[_]](EntityType.withName(toType), to) { srcitem =>
        f(item)(srcitem)(userOpt)(request)
      }
    }
  }

  def linkPostAction(id: String, toType: String, to: String)(
      f: Either[(MT, MetaModel[_],Form[LinkF]),LinkMeta] => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {

    implicit val linkWrites: Writes[LinkF] = models.json.rest.linkFormat

    withItemPermission[MT](id, PermissionType.Annotate, contentType) {
        item => implicit userOpt => implicit request =>
      LinkForm.form.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          getEntity[MetaModel[_]](EntityType.withName(toType), to) { srcitem =>
            f(Left((item,srcitem,errorForm)))(userOpt)(request)
          }
        },
        ann => {
          AsyncRest {
            rest.LinkDAO(userOpt).link(id, to, ann).map { annOrErr =>
              annOrErr.right.map { ann =>
                f(Right(ann))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }

  def linkMultiAction(id: String)(f: MT => Option[UserProfileMeta] => Request[AnyContent] => Result)(implicit rd: RestReadable[MT]) = {
    withItemPermission[MT](id, PermissionType.Annotate, contentType) {
        item => implicit userOpt => implicit request =>
      f(item)(userOpt)(request)
    }
  }

  def linkPostMultiAction(id: String)(
      f: Either[(MT,Form[List[(String,LinkF,Option[String])]]),List[LinkMeta]] => Option[UserProfileMeta] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      implicit val linkWrites: Writes[LinkF] = models.json.rest.linkFormat
      val multiForm: Form[List[(String,LinkF,Option[String])]] = models.forms.LinkForm.multiForm
      multiForm.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          f(Left((item,errorForm)))(userOpt)(request)
        },
        links => {
          AsyncRest {
            rest.LinkDAO(userOpt).linkMultiple(id, links).map { linksOrErr =>
              linksOrErr.right.map { outLinks =>
                f(Right(outLinks))(userOpt)(request)
              }
            }
          }
        }
      )
    }
  }


  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   * @return
   */
  def createLink(id: String, apid: String) = Action(parse.json) { request =>
    request.body.validate[AccessPointLink].fold(
      errors => { // oh dear, we have an error...
        BadRequest(JsError.toFlatJson(errors))
      },
      ann => {
        withItemPermission(id, PermissionType.Annotate, contentType) {
            item => implicit userOpt => implicit request =>
          AsyncRest {
            val link = new LinkF(id = None, linkType=LinkF.LinkType.Associative, description=ann.description)
            rest.LinkDAO(userOpt).link(id, ann.target, link, Some(apid)).map { annOrErr =>
              annOrErr.right.map { ann =>
                Created(Json.toJson(ann)(LinkMeta.Converter.clientFormat))
              }
            }
          }
          // TODO: Fix AuthController so we can use the
          // various auth action composers with body parsers
          // other than AnyContent
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }

  /**
   * Create a link, via Json, for the object with the given id and a set of
   * other objects.
   * @return
   */
  def createMultipleLinks(id: String) = Action(parse.json) { request =>
    request.body.validate[List[AccessPointLink]].fold(
      errors => { // oh dear, we have an error...
        BadRequest(JsError.toFlatJson(errors))
      },
      anns => {
        withItemPermission(id, PermissionType.Annotate, contentType) {
            item => implicit userOpt => implicit request =>
          AsyncRest {
            val links = anns.map(ann =>
              (ann.target, new LinkF(id = None, linkType=ann.`type`.getOrElse(LinkF.LinkType.Associative), description=ann.description), None)
            )
            rest.LinkDAO(userOpt).linkMultiple(id, links).map { linksOrErr =>
              linksOrErr.right.map { newlinks =>
                Created(Json.toJson(newlinks)(Writes.list(LinkMeta.Converter.clientFormat)))
              }
            }
          }
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }

  /**
   * Create a link, via Json, for any arbitrary two objects, via an access point.
   * @return
   */
  def createAccessPoint(id: String, did: String) = Action(parse.json) { request =>
    request.body.validate[AccessPointF](AccessPointLink.accessPointFormat).fold(
      errors => { // oh dear, we have an error...
        BadRequest(JsError.toFlatJson(errors))
      },
      ap => {
        withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
          AsyncRest {
            import models.json.AccessPointFormat._
            rest.DescriptionDAO(entityType, userOpt).createAccessPoint(id, did, ap).map { apOrErr =>
              apOrErr.right.map { ann =>
                Created(Json.toJson(ann)(models.json.client.accessPointFormat))
              }
            }
          }
          // TODO: Fix AuthController so we can use the
          // various auth action composers with body parsers
          // other than AnyContent
        }(request.map(js => AnyContentAsEmpty))
      }
    )
  }

  /**
   * Get the link, if any, for a document and an access point.
   * @param id
   * @param apid
   * @return
   */
  def getLink(id: String, apid: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).getFor(id).map { linksOrErr =>
        linksOrErr.right.map { linkList =>
          val linkOpt = linkList.find(link => link.bodies.exists(b => b.id == apid))
          val res = for {
            link <- linkOpt
            target <- link.opposingTarget(item)
          } yield {
            new AccessPointLink(
              target = target.id,
              `type` = None, // TODO: Add
              description = link.model.description
            )
          }
          Ok(Json.toJson(res))
        }
      }
    }
  }

  /**
   * Delete an access point by ID. FIXME: This should probably be moved elsewhere.
   * @param id
   * @return
   */
  def deleteAccessPoint(id: String, accessPointId: String) = withItemPermission(id, PermissionType.Update, contentType) {
      bool => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).deleteAccessPoint(accessPointId).map { boolOrErr =>
        boolOrErr.right.map { ok =>
          Cache.remove(id)
          Ok(Json.toJson(ok))
        }
      }
    }
  }

  /**
   * Delete a link.
   * @param id
   * @return
   */
  def deleteLink(id: String, linkId: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      bool => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).deleteLink(id, linkId).map { boolOrErr =>
        boolOrErr.right.map { ok =>
          Cache.remove(id)
          Ok(Json.toJson(ok))
        }
      }
    }
  }

}

