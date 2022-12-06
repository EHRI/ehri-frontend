package controllers.vocabularies

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models.{EntityType, _}
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.cypher.CypherService
import services.data.{DataUser, AuthenticatedUser, IdGenerator, ValidationError}
import services.search.{SearchConstants, SearchParams}
import utils.PageParams

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class VocabularyEditor @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  idGenerator: IdGenerator,
  ws: WSClient,
  cypher: CypherService
) extends AdminController with Read[Vocabulary] with Search {

  private val maxSize = config.underlying.getBytes("ehri.admin.vocabEditor.maxPayloadSize")
  private val clientFormFormat = client.json.conceptJson.fFormat
  private val clientFormat = client.json.conceptJson.clientFormat

  private case class AccountRequest[A](account: Account, request: Request[A]) extends WrappedRequest[A](request)

  private case class UserAction[B](parser: BodyParser[B]) extends ActionBuilder[AccountRequest, B] {
    override def invokeBlock[A](request: Request[A], block: AccountRequest[A] => Future[Result]): Future[Result] = {
      authHandler.restoreAccount(request).recover({
        case _ => None -> identity[Result] _
      })(controllerComponents.executionContext).flatMap({
        case (Some(acc), cookieUpdater) => block(AccountRequest[A](acc, request))
          .map(cookieUpdater)(controllerComponents.executionContext)
        case (None, _) => Future.successful(Forbidden)
      })(controllerComponents.executionContext)
    }

    override protected def executionContext: ExecutionContext = controllerComponents.executionContext
  }

  private implicit def userAction2ApiUser(implicit request: AccountRequest[_]): DataUser =
    AuthenticatedUser(request.account.id)

  private implicit def userAction2UserOpt(implicit request: AccountRequest[_]): Option[UserProfile] =
    Some(UserProfile(UserProfileF(id = Some(request.account.id), identifier = request.account.id, name = "")))

  def editor(id: String): Action[AnyContent] = WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
    Ok(views.html.admin.vocabulary.vocabeditor(request.item))
  }

  def search(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    filter(params, paging, filters = Map(SearchConstants.HOLDER_ID -> id)).map { page =>
      Ok(Json.toJson(page.items))
    }
  }

  def langs(id: String): Action[AnyContent] = UserAction(parse.anyContent).apply { implicit request =>
    Ok.chunked(cypher.legacy(scriptBody =
      """
        MATCH (v:CvocVocabulary {__id: $vocab})
                <-[:inAuthoritativeSet]-()
                <-[:describes]-(d:CvocConceptDescription)
        RETURN DISTINCT(d.languageCode) as lang
        ORDER BY lang
      """.stripMargin, params = Map("vocab" -> JsString(id))))
  }

  def nextIdentifier(id: String): Action[AnyContent] = UserAction(parse.anyContent).async { implicit request =>
    idGenerator.getNextChildNumericIdentifier(id, EntityType.Concept, "%d").map { newId =>
      Ok(Json.toJson(newId))
    }
  }

  def list(id: String, query: Option[String], lang: String): Action[AnyContent] = UserAction(parse.anyContent).apply { implicit request =>
    query.filterNot(_.trim.isEmpty).map { q =>
      Ok.chunked(cypher.legacy(scriptBody =
        """
        MATCH (v:CvocVocabulary {__id: $vocab})
                <-[:inAuthoritativeSet]-(c:CvocConcept)
                <-[:describes]-(d:CvocConceptDescription)
         WHERE toLower(d.name) CONTAINS toLower($q)
          OR c.identifier = $q
          OR c.__id = $q
         WITH
            c,
            COLLECT(d) as all
          WITH c,
            all,
            [d in all where d.languageCode = $lang][0].name as langName,
            size((c)-[:narrower]->()) as childCount
         RETURN c.__id, coalesce(langName, all[0].name, c.__id) as name, childCount
         ORDER BY name
      """.stripMargin, params = Map("lang" -> JsString(lang), "q" -> JsString(q), "vocab" -> JsString(id))))
    }.getOrElse {
      Ok.chunked(cypher.legacy(scriptBody =
        """
        MATCH (v:CvocVocabulary {__id: $vocab})
                <-[:inAuthoritativeSet]-(c:CvocConcept)
         WHERE NOT (c)<-[:narrower]-()
         WITH c, [(c)<-[:describes]-(d:CvocConceptDescription)| d] as all
         WITH c,
            [d in all where d.languageCode = $lang][0].name as langName,
            all,
            size((c)-[:narrower]->()) as childCount
         RETURN c.__id, coalesce(langName, all[0].name, c.__id) as name, childCount
         ORDER BY name
      """.stripMargin, params = Map("lang" -> JsString(lang), "vocab" -> JsString(id))))

    }
  }

  def get(id: String, cid: String): Action[AnyContent] = UserAction(parse.anyContent).async { implicit request =>
    userDataApi.get[Concept](cid).map { item =>
      Ok(Json.toJson(item)(clientFormat))
    }
  }

  def narrower(id: String, cid: String, lang: String): Action[AnyContent] = UserAction(parse.anyContent).apply { implicit request =>
    Ok.chunked(cypher.legacy(scriptBody =
      """
        MATCH (p:CvocConcept {__id: $parent})
                -[:narrower]->(c:CvocConcept)
         WITH c,
          [(c)<-[:describes]-(d:CvocConceptDescription)| d] as all
         WITH c,
          [d in all where d.languageCode = $lang][0].name as langName,
          all,
          size((c)-[:narrower]->()) as childCount
         RETURN c.__id, coalesce(langName, all[0].name, c.__id) as name, childCount
         ORDER BY name
      """.stripMargin, params = Map("lang" -> JsString(lang), "parent" -> JsString(cid))))
  }

  def broader(id: String, cid: String): Action[Seq[String]] = UserAction(parse.json[Seq[String]]).async { implicit request =>
    userDataApi.parent[Concept, Concept](cid, request.body).map { item =>
      Ok(Json.toJson(item)(clientFormat))
    }
  }

  def updateItem(id: String, cid: String): Action[JsValue] = UserAction(parse.json(maxSize)).async { implicit request =>
    request.body.validate[Concept](clientFormat).fold(
      invalid => Future.successful(BadRequest(JsError.toJson(invalid))),
      data => {
        userDataApi.update[Concept, ConceptF](cid, data.data).map { item =>
          implicit val conceptFormat: Writes[Concept] = client.json.conceptJson.clientFormat
          Ok(Json.toJson(item)(clientFormat))
        }.recover {
          case e: ValidationError => BadRequest(Json.toJson(e.errorSet.errors))
        }
      }
    )
  }

  def createItem(id: String): Action[JsValue] = UserAction(parse.json(maxSize)).async { implicit request =>
    request.body.validate[ConceptF](clientFormFormat).fold(
      invalid => Future.successful(BadRequest(JsError.toJson(invalid))),
      data => {
        userDataApi.createInContext[Vocabulary, ConceptF, Concept](id, data).map { item =>
          Created(Json.toJson(item)(clientFormat))
        }.recover {
          case e: ValidationError => BadRequest(Json.toJson(e.errorSet.errors))
        }
      }
    )
  }

  def deleteItem(id: String, cid: String): Action[AnyContent] = UserAction(parse.anyContent).async { implicit request =>
    userDataApi.delete[Concept](cid).map(_ => NoContent)
  }
}


