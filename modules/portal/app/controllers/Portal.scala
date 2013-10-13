package controllers.portal

import _root_.models.json.{ClientConvertable, RestReadable}
import play.api.Play.current
import _root_.models._
import controllers.base.EntitySearch
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import views.html._
import play.api.http.MimeTypes

import com.google.inject._
import utils.search.{Dispatcher, SearchOrder, SearchParams}
import play.api.libs.json.{Format, Writes, Json}
import solr.facet.FieldFacetClass
import play.api.i18n.Messages
import views.Helpers
import play.api.cache.Cached
import solr.facet.FieldFacetClass
import play.api.i18n.Messages
import views.Helpers
import defines.EntityType
import utils.ListParams
import solr.facet.FieldFacetClass
import scala.Some
import play.api.libs.ws.WS
import play.api.templates.Html


@Singleton
class Portal @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends Controller with EntitySearch {

  // This is a publically-accessible site
  override val staffOnly = false

  // i.e. Everything
  private val entityFacets = List(
    FieldFacetClass(
      key = IsadG.LANG_CODE,
      name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
      param = "lang",
      render = Helpers.languageCodeToName
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

  /**
   * Fetch a given item, along with its links and annotations.
   */
  def getAction[MT](entityType: EntityType.Value, id: String)(
    f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
                                     implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
    itemAction[MT](entityType, id) { item => implicit userOpt => implicit request =>
      AsyncRest {
        val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
        val linkReq = rest.LinkDAO(userOpt).getFor(id)
        for { annOrErr <- annsReq ; linkOrErr <- linkReq } yield {
          for { anns <- annOrErr.right ; links <- linkOrErr.right } yield {
            f(item)(anns)(links)(userOpt)(request)
          }
        }
      }
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  def getWithChildrenAction[CT, MT](entityType: EntityType.Value, id: String)(
    f: MT => rest.Page[CT] => ListParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Result)(
                                 implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
    getAction[MT](entityType, id) { item => anns => links => implicit userOpt => implicit request =>
      AsyncRest {
        val params = ListParams.fromRequest(request)
        val cReq = rest.EntityDAO[MT](entityType, userOpt).pageChildren[CT](id, params)
        for { cOrErr <- cReq  } yield {
          for { children <- cOrErr.right } yield {
            f(item)(children)(params)(anns)(links)(userOpt)(request)
          }
        }
      }
    }
  }



  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads

  def search = searchAction[AnyModel](
    defaultParams = Some(SearchParams(
      entities = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent),
      sort = Some(SearchOrder.Score))),
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
        case _ => Ok(views.html.portal.search(page, params, facets,
          controllers.portal.routes.Portal.search))
      }
  }


  /**
   * Quick filter action that searches applies a 'q' string filter to
   * only the name_ngram field and returns an id/name pair.
   * @return
   */
  def filter = filterAction() { page => implicit userOpt => implicit request =>
    Ok(Json.obj(
      "numPages" -> page.numPages,
      "page" -> page.page,
      "items" -> page.items.map { case (id, name, t) =>
        Json.arr(id, name, t.toString)
      }
    ))
  }




  def jsRoutes = Action { implicit request =>

    import controllers.core.routes.javascript._
    import controllers.archdesc.routes.javascript.DocumentaryUnits
    import controllers.archdesc.routes.javascript.Countries
    import controllers.archdesc.routes.javascript.Repositories
    import controllers.vocabs.routes.javascript.Vocabularies
    import controllers.vocabs.routes.javascript.Concepts
    import controllers.authorities.routes.javascript.AuthoritativeSets
    import controllers.authorities.routes.javascript.HistoricalAgents

    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.portal.routes.javascript.Portal.account,
        controllers.portal.routes.javascript.Portal.profile,
        controllers.portal.routes.javascript.Portal.search,
        controllers.portal.routes.javascript.Portal.filter,
        Application.getType,
        Application.getGeneric,
        UserProfiles.list,
        UserProfiles.get,
        Groups.list,
        Groups.get,
        DocumentaryUnits.search,
        DocumentaryUnits.get,
        Countries.search,
        Countries.get,
        Repositories.search,
        Repositories.get,
        Vocabularies.list,
        Vocabularies.get,
        Concepts.search,
        Concepts.get,
        AuthoritativeSets.list,
        AuthoritativeSets.get,
        HistoricalAgents.search,
        HistoricalAgents.get
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  /**
   * Render entity types into the view to serve as JS constants.
   * @return
   */
  def globalData = Action { implicit request =>
    import defines.EntityType
    Ok(
      """
        |var EntityTypes = {
        |%s
        |};
      """.stripMargin.format(
        "\t" + EntityType.values.map(et => s"$et: '$et'").mkString(",\n\t"))
    ).as(MimeTypes.JAVASCRIPT)
  }

  def account = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt.flatMap(_.account)))
  }

  def profile = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt)(Format.optionWithNull(UserProfile.Converter.clientFormat)))
  }

  def index = userProfileAction { implicit userOpt => implicit request =>
    Ok(views.html.portal.portal())
  }

  def browseCountries = TODO

  def browseCountry(id: String) = getWithChildrenAction[Repository, Country](EntityType.Country, id) {
      ct => repos => params => anns => links => implicit userOpt => implicit request =>
    Ok(ct.toStringLang)
  }

  def browseRepository(id: String) = getWithChildrenAction[DocumentaryUnit, Repository](EntityType.Repository, id) {
      repo => docs => params => anns => links => implicit userOpt => implicit request =>
    Ok(portal.repository.show(repo, docs, anns, links))
  }

  def browseDocument(id: String) = getWithChildrenAction[DocumentaryUnit, DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      doc => children => params => anns => links => implicit userOpt => implicit request =>
    Ok(portal.documentaryUnit.show(doc, children, anns, links))
  }

  def browseHistoricalAgents = TODO

  def browseAuthoritativeSet(id: String) = getAction[AuthoritativeSet](EntityType.AuthoritativeSet, id) {
    set => anns => links => implicit userOpt => implicit request =>
      TODO(request)
  }

  def browseHistoricalAgent(id: String) = getAction[HistoricalAgent](EntityType.HistoricalAgent, id) {
      doc => anns => links => implicit userOpt => implicit request =>
    Ok(portal.historicalAgent.show(doc, anns, links))
  }

  def activity = TODO

  def placeholder = Cached("pages:portalPlaceholder") {
    Action { implicit request =>
      Ok(views.html.placeholder())
    }
  }


  case class NewsItem(title: String, link: String, description: Html)

  object NewsItem {
    def fromRss(feed: String): Seq[NewsItem] = {
      (xml.XML.loadString(feed) \\ "item").map { item =>
        new NewsItem(
          (item \ "title").text,
          (item \ "link").text,
          Html((item \ "description").text))
      }
    }
  }

  def newsFeed = Cached("pages.newsFeed", 3600) {
    Action { request =>
      Async {
        WS.url("http://www.ehri-project.eu/rss.xml").get.map { r =>
          Ok(portal.newsFeed(NewsItem.fromRss(r.body)))
        }
      }
    }
  }
}

