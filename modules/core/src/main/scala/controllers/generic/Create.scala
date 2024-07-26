package controllers.generic

import forms._
import models.{ContentType, EventType, Model, ModelData, PermissionType, Persistable, UserProfile, UsersAndGroups, Writable}
import play.api.data._
import play.api.mvc._
import services.data._

import scala.concurrent.Future

/**
  * Controller trait for creating entities..
  */
trait Create[MT <: Model{type T <: ModelData with Persistable}] extends Read[MT] with Write {

  protected def dataHelpers: DataHelpers

  /**
    * A request containing id->name tuples for available users
    * and groups.
    *
    * @param usersAndGroups data about user and groups
    * @param user           a profile
    * @param request        the underlying request
    * @tparam A the type of the underlying request
    */
  case class NewItemRequest[A](
    fieldHints: FormFieldHints,
    usersAndGroups: UsersAndGroups,
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  case class CreateRequest[A](
    formOrItem: Either[(Form[MT#T], Form[Seq[String]], FormFieldHints, UsersAndGroups), MT],
    user: UserProfile,
    request: Request[A]
  ) extends WrappedRequest[A](request)
    with WithUser

  protected def NewItemAction(implicit ct: ContentType[MT]): ActionBuilder[NewItemRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, NewItemRequest] {
      override protected def transform[A](request: WithUserRequest[A]): Future[NewItemRequest[A]] = {
        val formConfig = FieldMetaFormFieldHintsBuilder(ct.entityType, entityTypeMetadata, config)
        for {
          fieldHints <- formConfig.forCreate
          usersAndGroups <- dataHelpers.getUserAndGroupList
        } yield NewItemRequest(fieldHints, usersAndGroups, request.user, request)
      }
    }

  protected def CreateItemAction(form: Form[MT#T], pf: Request[_] => Map[String, Seq[String]] = _ => Map.empty)(
    implicit fmt: Writable[MT#T], ct: ContentType[MT]): ActionBuilder[CreateRequest, AnyContent] =
    WithContentPermissionAction(PermissionType.Create, ct.contentType) andThen new CoreActionTransformer[WithUserRequest, CreateRequest] {
      def transform[A](request: WithUserRequest[A]): Future[CreateRequest[A]] = {
        implicit val req: WithUserRequest[A] = request
        val visForm = visibilityForm.bindFromRequest()
        val formConfig = FieldMetaFormFieldHintsBuilder(ct.entityType, entityTypeMetadata, config)
        form.bindFromRequest().fold(
          errorForm => for {
            fieldHints <- formConfig.forCreate
            usersAndGroups <- dataHelpers.getUserAndGroupList
            _ = logger.debug(s"CreateItemAction: form binding failed: ${errorForm.errors}")
          } yield CreateRequest(Left((errorForm, visForm, fieldHints, usersAndGroups)), request.user, request.request),
          doc => {
            val accessors = visForm.value.getOrElse(Nil)
            (for {
              pre <- itemLifecycle.preSave(None, None, doc, EventType.creation)
              saved <- userDataApi.create(pre, accessors, params = pf(request), logMsg = getLogMessage)
              post <- itemLifecycle.postSave(saved.id, saved, EventType.creation)
            } yield CreateRequest(Right(post), request.user, request)) recoverWith {
              // If we have an error, check if it's a validation error.
              // If so, we need to merge those errors back into the form
              // and redisplay it...
              case ValidationError(errorSet) =>
                val filledForm = doc.getFormErrors(errorSet, form.fill(doc))
                for {
                  fieldHints <- formConfig.forCreate
                  usersAndGroups <- dataHelpers.getUserAndGroupList
                } yield CreateRequest(Left((filledForm, visForm, fieldHints, usersAndGroups)), request.user, request)
            }
          }
        )
      }
    }
}

