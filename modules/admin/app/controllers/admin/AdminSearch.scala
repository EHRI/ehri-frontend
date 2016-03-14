package controllers.admin

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Concurrent, Enumerator}
import utils.MovedPageLookup
import concurrent.Future
import play.api.i18n.{MessagesApi, Messages}
import views.{MarkdownRenderer, Helpers}
import play.api.libs.json.{Writes, Json}

import javax.inject._
import models.base.{Description, AnyModel}
import utils.search._
import play.api.Logger
import controllers.generic.{Indexable, Search}
import backend.Backend
import scala.util.Failure
import scala.util.Success
import defines.EntityType
import controllers.base.AdminController
import defines.EnumUtils.enumMapping

@Singleton
case class AdminSearch @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  searchIndexer: SearchIndexMediator,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends AdminController
  with Search {

  // i.e. Everything

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = Description.LANG_CODE,
        name = Messages("documentaryUnit." + Description.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("search.type"),
        param = "type",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
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
    EntityType.VirtualUnit,
    EntityType.Annotation
  )

  private val searchTypes = Seq(
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

  def search = OptionalUserAction.async { implicit request =>
    find[AnyModel](
      defaultParams = SearchParams(sort = Some(SearchOrder.Score)),
      entities = searchTypes.toList,
      facetBuilder = entityFacets
    ).map { result =>
      render {
        case Accepts.Json() =>
          Ok(Json.toJson(Json.obj(
            "numPages" -> result.page.numPages,
            "page" -> Json.toJson(result.page.items.map(_._1))(Writes.seq(client.json.anyModelJson.clientFormat)),
            "facets" -> result.facetClasses
          ))
          )
        case _ => Ok(views.html.admin.search.search(
          result,
          controllers.admin.routes.AdminSearch.search())
        )
      }
    }
  }

  import play.api.data.Form
  import play.api.data.Forms._


  private val updateIndexForm = Form(
    tuple(
      "clearAll" -> default(boolean, false),
      "clearTypes" -> default(boolean, false),
      "type" -> list(enumMapping(defines.EntityType))
    )
  )

  def updateIndex() = AdminAction { implicit request =>
    Ok(views.html.admin.search.updateIndex(form = updateIndexForm, types = indexTypes,
      action = controllers.admin.routes.AdminSearch.updateIndexPost()))
  }

  /**
   * Perform the actual update, piping progress through a channel
   * and returning a chunked result.
   */
  def updateIndexPost() = AdminAction { implicit request =>

    val (deleteAll, deleteTypes, entities) = updateIndexForm.bindFromRequest.value.get

    def wrapMsg(m: String) = s"<message>$m</message>"

    // Create an unicast channel in which to feed progress messages
    val channel = Concurrent.unicast[String] { chan =>
      val optionallyClearIndex: Future[Unit] =
        if (!deleteAll) Future.successful(Unit)
        else searchIndexer.handle.clearAll()
          .map(_ => chan.push(wrapMsg("... finished clearing index")))

      val optionallyClearType: Future[Unit] =
        if (!deleteTypes || deleteAll) Future.successful(Unit)
        else searchIndexer.handle.clearTypes(entities)
          .map(_ => chan.push(wrapMsg(s"... finished clearing index for types: $entities")))

      val job = for {
        _ <- optionallyClearIndex
        _ <- optionallyClearType
        task <- searchIndexer.handle.withChannel(chan, wrapMsg).indexTypes(entityTypes = entities)
      } yield task

      job.map { _ =>
        chan.push(wrapMsg(Indexable.DONE_MESSAGE))
        chan.eofAndEnd()
      } recover {
        case t =>
          Logger.logger.error(t.getMessage)
          chan.push(wrapMsg("Indexing operation failed: " + t.getMessage))
          chan.push(wrapMsg(Indexable.ERR_MESSAGE))
          chan.eofAndEnd()
      }
    }

    Ok.chunked(channel.andThen(Enumerator.eof))
  }
}
