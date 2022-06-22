package controllers.datasets

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import models._
import play.api.libs.json.{JsString, Json}
import play.api.mvc._
import services.cypher.CypherService
import services.datasets.ImportDatasetService
import services.ingest.{Coreference, CoreferenceService, IngestService}
import services.storage.FileStorage

import javax.inject._


@Singleton
case class CoreferenceTables @Inject()(
  controllerComponents: ControllerComponents,
  @Named("dam") storage: FileStorage,
  appComponents: AppComponents,
  datasets: ImportDatasetService,
  cypherServer: CypherService,
  coreferenceService: CoreferenceService,
  ingestService: IngestService,
)(implicit mat: Materializer) extends AdminController with StorageHelpers with Update[Repository] {

  def getTable(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    coreferenceService.get(id).map(refs => Ok(Json.toJson(refs)))
  }

  def saveTable(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asJson.map(Json.fromJson[Seq[Coreference]]).flatMap(_.asOpt) match {
      case Some(rows) => for {
        _ <- coreferenceService.save(id, rows)
      } yield Ok(Json.obj("ok" -> true))
      case None => val q =
        """
         MATCH (:Repository {__id:$scope})
            <-[:heldBy|childOf*]-(:DocumentaryUnit)
            <-[:describes]-(:DocumentaryUnitDescription)-[:relatesTo]->(ap:AccessPoint)
            <-[:hasLinkBody]-(:Link)-[:hasLinkTarget]->(c:CvocConcept)-[:inAuthoritativeSet]->(s:CvocVocabulary)
         RETURN DISTINCT ap.name as text, c.__id as target, s.__id as set
        """
        for {
          rows <- cypherServer
            .rows(q, Map("scope" -> JsString(id)))
            .collect { case JsString(t) :: JsString(c) :: JsString(s) :: Nil => Coreference(t, c, s) }
            .runWith(Sink.seq)
          _ <- coreferenceService.save(id, rows)
        } yield Ok(Json.obj("ok" -> true))
    }
  }

  def ingestTable(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    for {
      refs <- coreferenceService.get(id)
      log <- ingestService.importCoreferences(id, refs.map(r => r.text -> r.targetId))
    } yield Ok(Json.toJson(log))
  }
}
