package controllers.generic

import play.api.libs.concurrent.Execution.Implicits._
import models.base._
import play.api.mvc._
import play.api.data.Form
import defines.{EntityType, PermissionType}
import models.UserProfile
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.rest.{ItemNotFound, ValidationError}
import backend.{BackendReadable, BackendWriteable, BackendContentType}

/**
 * Controller trait for creating, updating, and deleting auxiliary descriptions
 * for entities that can be multiply described.
 *
 */
trait Descriptions[D <: Description with Persistable, T <: Model with Described[D], MT <: MetaModel[T]] extends Read[MT] {

  case class ManageDescriptionRequest[A](
    item: MT,
    formOrDescription: Either[Form[D], D],
    userOpt: Option[UserProfile],
    request:Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class DeleteDescriptionRequest[A](
    item: MT,
    description: D,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser


  def CreateDescriptionAction(id: String, form: Form[D])(
    implicit fmt: BackendWriteable[D], rd: BackendReadable[MT], drd: BackendReadable[D], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, ManageDescriptionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ManageDescriptionRequest[A]] = {
        implicit val req = request
        form.bindFromRequest.fold(
          ef => immediate(ManageDescriptionRequest(request.item, Left(ef), request.userOpt, request)),
          desc => backend.createDescription(id, desc, logMsg = getLogMessage).map { updated =>
            ManageDescriptionRequest(request.item, Right(updated), request.userOpt, request)
          } recover {
            case ValidationError(errorSet) =>
              val badForm = desc.getFormErrors(errorSet, form.fill(desc))
              ManageDescriptionRequest(request.item, Left(badForm), request.userOpt, request)
          }
        )
      }
    }

  def UpdateDescriptionAction(id: String, did: String, form: Form[D])(
    implicit fmt: BackendWriteable[D], rd: BackendReadable[MT], drd: BackendReadable[D], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, ManageDescriptionRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[ManageDescriptionRequest[A]] = {
        implicit val req = request
        form.bindFromRequest.fold(
          ef => immediate(ManageDescriptionRequest(request.item, Left(ef), request.userOpt, request)),
          desc => backend.updateDescription(id, did, desc, logMsg = getLogMessage).map { updated =>
            ManageDescriptionRequest(request.item, Right(updated), request.userOpt, request)
          } recover {
            case ValidationError(errorSet) =>
              val badForm = desc.getFormErrors(errorSet, form.fill(desc))
              ManageDescriptionRequest(request.item, Left(badForm), request.userOpt, request)
          }
        )
      }
    }

  def WithDescriptionAction(id: String, did: String)(
    implicit fmt: BackendWriteable[D], rd: BackendReadable[MT], drd: BackendReadable[D], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionRefiner[ItemPermissionRequest,DeleteDescriptionRequest] {
      override protected def refine[A](request: ItemPermissionRequest[A]): Future[Either[Result, DeleteDescriptionRequest[A]]] = {
        request.item.model.description(did) match {
          case Some(d) => immediate(Right(DeleteDescriptionRequest(request.item, d, request.userOpt, request)))
          case None => notFoundError(request).map(r => Left(r))
        }
      }
    }

  def DeleteDescriptionAction(id: String, did: String)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) =
    WithItemPermissionAction(id, PermissionType.Update) andThen new ActionTransformer[ItemPermissionRequest, OptionalUserRequest] {
      override protected def transform[A](request: ItemPermissionRequest[A]): Future[OptionalUserRequest[A]] = {
        implicit val req = request
        backend.deleteDescription(id, did, logMsg = getLogMessage).map { _ =>
          OptionalUserRequest(request.userOpt, request)
        }
      }
    }

  @deprecated(message = "Use CreateDescriptionAction instead", since = "1.0.2")
  def createDescriptionPostAction(id: String, descriptionType: EntityType.Value, form: Form[D])(
      f: MT => Either[Form[D], D] => Option[UserProfile] => Request[AnyContent] => Result)(
        implicit fmt: BackendWriteable[D], rd: BackendReadable[MT], drd: BackendReadable[D], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold(
        ef => immediate(f(item)(Left(ef))(userOpt)(request)),
        desc => backend.createDescription(id, desc, logMsg = getLogMessage).map { updated =>
          f(item)(Right(updated))(userOpt)(request)
        } recoverWith {
          case ValidationError(errorSet) => {
            val badForm = desc.getFormErrors(errorSet, form.fill(desc))
            immediate(f(item)(Left(badForm))(userOpt)(request))
          }
        }
      )
    }
  }

  @deprecated(message = "Use UpdateDescriptionAction instead", since = "1.0.2")
  def updateDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String, form: Form[D])(
    f: MT => Either[Form[D],D] => Option[UserProfile] => Request[AnyContent] => Result)(
           implicit fmt: BackendWriteable[D], rd: BackendReadable[MT], drd: BackendReadable[D], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) {
        item => implicit userOpt => implicit request =>
      form.bindFromRequest.fold(
        ef => immediate(f(item)(Left(ef))(userOpt)(request)),
        desc => backend.updateDescription(id, did, desc, logMsg = getLogMessage).map { updated =>
          f(item)(Right(updated))(userOpt)(request)
        } recoverWith {
          case ValidationError(errorSet) => {
            val badForm = desc.getFormErrors(errorSet, form.fill(desc))
            immediate(f(item)(Left(badForm))(userOpt)(request))
          }
        }
      )
    }
  }

  @deprecated(message = "Use WithDescriptionAction instead", since = "1.0.2")
  def deleteDescriptionAction(id: String, did: String)(
      f: MT => D => Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission[MT](id, PermissionType.Update) {
        item => implicit userOpt => implicit request =>
      item.model.description(did).map { desc =>
        f(item)(desc)(userOpt)(request)
      }.getOrElse {
        throw new ItemNotFound(key = Some("id"), value = Some(did))
      }
    }
  }

  @deprecated(message = "Use DeleteDescriptionAction instead", since = "1.0.2")
  def deleteDescriptionPostAction(id: String, descriptionType: EntityType.Value, did: String)(
      f: Option[UserProfile] => Request[AnyContent] => Result)(implicit rd: BackendReadable[MT], ct: BackendContentType[MT]) = {
    withItemPermission.async[MT](id, PermissionType.Update) {
        item => implicit userOpt => implicit request =>
      backend.deleteDescription(id, did, logMsg = getLogMessage).map { _ =>
        f(userOpt)(request)
      }
    }
  }
}

