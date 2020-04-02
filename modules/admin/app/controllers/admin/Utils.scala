package controllers.admin

import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import controllers.AppComponents
import controllers.base.AdminController
import javax.inject._
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents}
import services.cypher.CypherService
import services.data.AuthenticatedUser
import services.ingest.{EadValidator, S3ObjectSpec, XmlValidationError}
import services.search.SearchIndexMediator
import services.storage.DOFileStorage
import utils.PageParams

import scala.concurrent.Future


/**
  * Controller for various monitoring functions and admin utilities.
  */
@Singleton
case class Utils @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  searchIndexer: SearchIndexMediator,
  ws: WSClient,
  eadValidator: EadValidator,
  cypher: CypherService
)(implicit mat: Materializer) extends AdminController {

  override val staffOnly = false

  private def logger = play.api.Logger(classOf[Utils])

  /** Check the database is up by trying to load the admin account.
    */
  def checkServices: Action[AnyContent] = Action.async { implicit request =>
    val checkDbF = dataApi.withContext(AuthenticatedUser("admin")).status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"ehri\t$s")
    val checkSearchF = searchEngine.status()
      .recover { case e => s"ko: ${e.getMessage}" }.map(s => s"solr\t$s")

    Future.sequence(Seq(checkDbF, checkSearchF)).map(_.mkString("\n")).map { s =>
      if (s.contains("ko")) ServiceUnavailable(s) else Ok(s)
    }
  }

  /** Check users in the accounts DB have profiles in
    * the graph DB, and vice versa.
    */
  def checkUserSync: Action[AnyContent] = Action.async { implicit request =>

    for {
      allAccounts <- accounts.findAll(PageParams.empty.withoutLimit)
      profileIds <- cypher.get("MATCH (n:UserProfile) RETURN n.__id").map {
        res => res.data.collect { case JsString(id) :: _ => id }.flatten
      }
      accountIds = allAccounts.map(_.id)
    } yield {
      val noProfile = accountIds.diff(profileIds)
      // Going nicely imperative here - sorry!
      var out = ""
      if (noProfile.nonEmpty) {
        out += "Users have account but no profile\n"
        noProfile.foreach { u =>
          out += s"  $u\n"
        }
      }
      Ok(out)
    }
  }

  private val errorsToBytes: Flow[XmlValidationError, ByteString, akka.NotUsed] = Flow[XmlValidationError]
    .map(e => Json.toJson(e))
    .map(Json.prettyPrint)
    .map(ByteString.apply)
    .intersperse(ByteString("["), ByteString(","), ByteString("]"))

  private val eadValidatingBodyParser: BodyParser[Source[ByteString, _]] = BodyParser { req =>
    val validateFlow: Flow[ByteString, ByteString, akka.NotUsed] = Flow[ByteString]
        .prefixAndTail(0)
        .mapAsync(1) { case (_, src) => eadValidator.validateEad(src) }
        .flatMapConcat(errs => Source.apply(errs.toList))
        .via(errorsToBytes)

    Accumulator.source[ByteString]
      .map(_.via(validateFlow))
      .map(Right.apply)
  }

  def validateEadStream: Action[Source[ByteString, _]] = Action(eadValidatingBodyParser) { implicit request =>
    Ok.chunked(request.body).as(ContentTypes.JSON)
  }

  def validateEadS3Object: Action[S3ObjectSpec] = Action(parse.json[S3ObjectSpec]).async { implicit request =>
    val storage = DOFileStorage(config)(mat.system, mat)
    val uri = storage.uri(request.body.classifier, request.body.path)
    eadValidator.validateEad(Uri(uri.toString)).map { errs =>
      val src = Source.apply(errs.toList).via(errorsToBytes)
      Ok.chunked(src).as(ContentTypes.JSON)
    }
  }
}
