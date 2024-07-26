package controllers.generic

import forms.{FieldMetaFormFieldHintsBuilder, FormFieldHints}
import models.{ContentType, EventType, Model, ModelData, PermissionType, Persistable, UserProfile, Writable}
import play.api.data.Form
import play.api.mvc._
import services.data.ValidationError

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

/**
  * Controller trait which updates items based on data from a submitted form,
  * wrapped with preSave and postSave lifecycle handlers.
  */
trait Update[MT <: Model{type T <: ModelData with Persistable}] extends Read[MT] with Write {

  case class EditRequest[A](
    item: MT,
    fieldHints: FormFieldHints,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  case class UpdateRequest[A](
    item: MT,
    formOrItem: Either[Form[MT#T], MT],
    fieldHints: FormFieldHints,
    userOpt: Option[UserProfile],
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithOptionalUser

  protected def EditAction(itemId: String)(implicit ct: ContentType[MT]): ActionBuilder[EditRequest, AnyContent] =
    WithItemPermissionAction(itemId, PermissionType.Update) andThen new CoreActionTransformer[ItemPermissionRequest, EditRequest] {
      def transform[A](request: ItemPermissionRequest[A]): Future[EditRequest[A]] = {
        implicit val req: ItemPermissionRequest[A] = request
        val formConfig = FieldMetaFormFieldHintsBuilder(ct.entityType, entityTypeMetadata, conf.configuration)
        formConfig.forUpdate.map { fieldHints =>
          EditRequest(request.item, fieldHints, request.userOpt, request.request)
        }
      }
    }

  protected def UpdateAction(id: String, form: Form[MT#T])(
    implicit ct: ContentType[MT], wd: Writable[MT#T]): ActionBuilder[UpdateRequest, AnyContent] =
    EditAction(id) andThen new CoreActionTransformer[EditRequest, UpdateRequest] {
      def transform[A](request: EditRequest[A]): Future[UpdateRequest[A]] = {
        implicit val req: EditRequest[A] = request
        form.bindFromRequest().fold(
          errorForm => {
            logger.debug(s"UpdateAction: form binding failed for item $id: ${errorForm.errors}")
            immediate(UpdateRequest(request.item, Left(errorForm), request.fieldHints, request.userOpt, request.request))
          },
          mod => {
            (for {
              pre <- itemLifecycle.preSave(Some(id), Some(request.item), mod, EventType.modification)
              saved <- userDataApi.update[MT, MT#T](id, pre, logMsg = getLogMessage)
              post <- itemLifecycle.postSave(id, saved, EventType.modification)
            } yield UpdateRequest(request.item, Right(post), request.fieldHints, request.userOpt, request)) recover {
              case ValidationError(errorSet) =>
                val filledForm = mod.getFormErrors(errorSet, form.fill(mod))
                UpdateRequest(request.item, Left(filledForm), request.fieldHints, request.userOpt, request)
            }
          }
        )
      }
    }
}
