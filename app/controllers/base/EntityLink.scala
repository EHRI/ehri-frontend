package controllers.base

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import defines._
import models._
import play.api.data.Form
import rest.{LinkDAO, EntityDAO}
import controllers.ListParams
import models.forms.LinkForm
import play.api.mvc.Result
import play.api.libs.json.{JsError, Json}

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
 * @tparam T the entity's build class
 */
trait EntityLink[T <: LinkableEntity] extends EntityRead[T] {

  def linkSelectAction(id: String, toType: String)(f: LinkableEntity => rest.Page[LinkableEntity] => ListParams => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      val linkSrcEntityType = EntityType.withName(toType)
      LinkableEntity.fromEntity(item).map { ann =>
        val params = ListParams.bind(request)

        // Need to process params!

        val rp = params.toRestParams(EntityAnnotate.listFilterMappings, EntityAnnotate.orderMappings, Some(EntityAnnotate.DEFAULT_SORT))

        AsyncRest {
          EntityDAO(linkSrcEntityType, userOpt).page(rp).map { pageOrErr =>
            pageOrErr.right.map { page =>
              f(ann)(page.copy(items = page.items.flatMap(e => LinkableEntity.fromEntity(e))))(params)(userOpt)(request)
            }
          }
        }
      } getOrElse {
        NotFound(views.html.errors.itemNotFound())
      }
    }
  }

  def linkAction(id: String, toType: String, to: String)(f: LinkableEntity => LinkableEntity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      getEntity(EntityType.withName(toType), to) { srcitem =>
        // If neither items are annotatable throw a 404
        val res: Option[Result] = for {
          target <- LinkableEntity.fromEntity(item)
          source <- LinkableEntity.fromEntity(srcitem)
        } yield {
            f(target)(source)(userOpt)(request)
        }
        res.getOrElse(NotFound(views.html.errors.itemNotFound()))
      }
    }
  }

  def linkPostAction(id: String, toType: String, to: String)(
      f: Either[(LinkableEntity, LinkableEntity,Form[LinkF]),Link] => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      LinkForm.form.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          getEntity(EntityType.withName(toType), to) { srcitem =>
          // If neither items are annotatable throw a 404
            val res: Option[Result] = for {
              target <- LinkableEntity.fromEntity(item)
              source <- LinkableEntity.fromEntity(srcitem)
            } yield {
              f(Left((target,source,errorForm)))(userOpt)(request)
            }
            res.getOrElse(NotFound(views.html.errors.itemNotFound()))
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

  def linkMultiAction(id: String)(f: LinkableEntity => Option[UserProfile] => Request[AnyContent] => Result) = {
    withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
      val res: Option[Result] = for {
        target <- LinkableEntity.fromEntity(item)
      } yield {
        f(target)(userOpt)(request)
      }
      res.getOrElse(NotFound(views.html.errors.itemNotFound()))
    }
  }

  def linkPostMultiAction(id: String)(
      f: Either[(LinkableEntity,Form[List[(String,LinkF,Option[String])]]),List[Link]] => Option[UserProfile] => Request[AnyContent] => Result): Action[AnyContent] = {
    withItemPermission(id, PermissionType.Update, contentType) { item => implicit userOpt => implicit request =>
      println("Form data: " + request.body.asFormUrlEncoded)
      val multiForm: Form[List[(String,LinkF,Option[String])]] = models.forms.LinkForm.multiForm
      multiForm.bindFromRequest.fold(
        errorForm => { // oh dear, we have an error...
          println(errorForm)
          val res: Option[Result] = for {
            target <- LinkableEntity.fromEntity(item)
          } yield {
            f(Left((target,errorForm)))(userOpt)(request)
          }
          res.getOrElse(NotFound(views.html.errors.itemNotFound()))
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
        withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
          AsyncRest {
            val link = new LinkF(id = None, linkType=LinkF.LinkType.Associative, description=ann.description)
            rest.LinkDAO(userOpt).link(id, ann.target, link, Some(apid)).map { annOrErr =>
              annOrErr.right.map { ann =>
                import models.json.LinkFormat.linkFormat
                Created(Json.toJson(ann.formable))
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
        withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
          AsyncRest {
            val links = anns.map(ann =>
              (ann.target, new LinkF(id = None, linkType=ann.`type`.getOrElse(LinkF.LinkType.Associative), description=ann.description), None)
            )
            rest.LinkDAO(userOpt).linkMultiple(id, links).map { linksOrErr =>
              linksOrErr.right.map { newlinks =>
                import models.json.LinkFormat.linkFormat
                Created(Json.toJson(newlinks.map(_.formable)))
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
            rest.DescriptionDAO(userOpt).createAccessPoint(id, did, ap).map { apOrErr =>
              apOrErr.right.map { ann =>
                import models.json.AccessPointFormat._
                Created(Json.toJson(AccessPoint(ann).formable)(AccessPointLink.accessPointFormat))
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
  def getLink(id: String, apid: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).getFor(id).map { linksOrErr =>
        linksOrErr.right.map { linkList =>
          val linkOpt = linkList.find(link => link.bodies.exists(b => b.id == apid))
          val itemOpt = LinkableEntity.fromEntity(item)
          val res = for (link <- linkOpt ; item <- itemOpt ; linkData <- link.formableOpt ; target <- link.opposingTarget(item)) yield {
            new AccessPointLink(
              target = target.id,
              `type` = None, // TODO: Add
              description = linkData.description
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
  def deleteAccessPoint(id: String, accessPointId: String) = withItemPermission(id, PermissionType.Update, contentType) { bool => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).deleteAccessPoint(accessPointId).map { boolOrErr =>
        boolOrErr.right.map { ok =>
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
  def deleteLink(id: String, linkId: String) = withItemPermission(id, PermissionType.Annotate, contentType) { bool => implicit userOpt => implicit request =>
    AsyncRest {
      LinkDAO(userOpt).deleteLink(id, linkId).map { boolOrErr =>
        boolOrErr.right.map { ok =>
          Ok(Json.toJson(ok))
        }
      }
    }
  }

}

