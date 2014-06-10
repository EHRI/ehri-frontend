package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Concurrent, Enumerator}
import models.{AccountDAO, IsadG}
import concurrent.Future
import play.api.i18n.Messages
import views.Helpers
import play.api.libs.json.{Writes, Json}

import com.google.inject._
import models.base.AnyModel
import utils.search._
import play.api.Logger
import controllers.generic.{Indexable, Search}
import backend.Backend
import scala.util.Failure
import solr.facet.FieldFacetClass
import scala.util.Success
import defines.EntityType


@Singleton
case class AdminSearch @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, searchIndexer: Indexer, backend: Backend, userDAO: AccountDAO) extends Search {

  // i.e. Everything
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages("documentaryUnit." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s)
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("search.type"),
        param = "type",
        render = s => Messages("contentTypes." + s)
      ),
      FieldFacetClass(
        key = "copyrightStatus",
        name = Messages("copyrightStatus.copyright"),
        param = "copyright",
        render = s => Messages("copyrightStatus." + s)
      ),
      FieldFacetClass(
        key = "scope",
        name = Messages("scope.scope"),
        param = "scope",
        render = s => Messages("scope." + s)
      )
    )
  }

  private val indexTypes = Seq(
    EntityType.Country,
    EntityType.DocumentaryUnit,
    EntityType.HistoricalAgent,
    EntityType.Repository,
    EntityType.Concept,
    EntityType.Vocabulary,
    EntityType.AuthoritativeSet,
    EntityType.UserProfile,
    EntityType.Group,
    EntityType.VirtualUnit
  )

  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = searchAction[AnyModel](
      defaultParams = SearchParams(sort = Some(SearchOrder.Score)),
      entityFacets = entityFacets) {
        page => params => facets => implicit userOpt => implicit request =>
    render {
      case Accepts.Json() => {
        Ok(Json.toJson(Json.obj(
          "numPages" -> page.numPages,
          "page" -> Json.toJson(page.items.map(_._1))(Writes.seq(AnyModel.Converter.clientFormat)),
          "facets" -> facets
        ))
        )
      }
      case _ => Ok(views.html.search.search(page, params, facets,
        controllers.admin.routes.AdminSearch.search()))
    }
  }

  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   */
  def filter = filterAction() {
    page => implicit userOpt => implicit request =>
      Ok(Json.obj(
        "numPages" -> page.numPages,
        "page" -> page.page,
        "items" -> page.items.map {
          case (id, name, t) =>
            Json.arr(id, name, t.toString)
        }
      ))
  }


  import play.api.data.Form
  import play.api.data.Forms._
  import models.forms.enum


  private val updateIndexForm = Form(
    tuple(
      "all" -> default(boolean, false),
      "type" -> list(enum(defines.EntityType))
    )
  )

  def updateIndex() = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.search.updateIndex(form = updateIndexForm, types = indexTypes,
      action = controllers.admin.routes.AdminSearch.updateIndexPost()))
  }

  /**
   * Perform the actual update, piping progress through a channel
   * and returning a chunked result.
   */
  def updateIndexPost() = adminAction { implicit userOpt => implicit request =>

    val (deleteAll, entities) = updateIndexForm.bindFromRequest.value.get

    def wrapMsg(m: String) = s"<message>$m</message>"

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>

      def optionallyClearIndex: Future[Unit] = {
        if (!deleteAll) Future.successful(Unit)
        else {
          val f = searchIndexer.clearAll()
          f.onSuccess {
            case () => chan.push(wrapMsg("... finished clearing index"))
          }
          f
        }
      }

      val job = optionallyClearIndex.flatMap { _ =>
        searchIndexer.withChannel(chan, wrapMsg).indexTypes(entityTypes = entities)
      }

      job.onComplete {
        case Success(()) => {
          chan.push(wrapMsg(Indexable.DONE_MESSAGE))
          chan.eofAndEnd()
        }
        case Failure(t) => {
          Logger.logger.error(t.getMessage)
          chan.push(wrapMsg("Indexing operation failed: " + t.getMessage))
          chan.push(wrapMsg(Indexable.ERR_MESSAGE))
          chan.eofAndEnd()
        }
      }
    }

    Ok.chunked(channel.andThen(Enumerator.eof))
  }
}
