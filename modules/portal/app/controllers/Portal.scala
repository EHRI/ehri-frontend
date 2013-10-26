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
import utils.search._
import play.api.libs.json.{Format, Writes, Json}
import solr.facet.{SolrQueryFacet, QueryFacetClass, FieldFacetClass}
import play.api.i18n.{Lang, Messages}
import views.Helpers
import play.api.cache.Cached
import views.Helpers
import defines.EntityType
import utils.ListParams
import play.api.libs.ws.WS
import play.api.templates.Html
import rest.EntityDAO
import solr.SolrConstants
import scala.concurrent.Future
import solr.facet.FieldFacetClass
import scala.Some
import rest.EntityDAO
import solr.facet.SolrQueryFacet
import solr.facet.QueryFacetClass


@Singleton
class Portal @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends Controller with EntitySearch {

  // This is a publically-accessible site
  override val staffOnly = false

  private val portalRoutes = controllers.portal.routes.Portal

  // i.e. Everything
  private val entityFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "type",
        name = Messages("search.type"),
        param = "type",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      )
    )
  }
  
  def pageAction[MT](entityType: EntityType.Value)(f: rest.Page[MT] => ListParams => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT]) = {
    userProfileAction.async { implicit userOpt => implicit request =>
      val params = ListParams.fromRequest(request)
      EntityDAO(entityType, userOpt).page(params).map { page =>
        f(page)(params)(userOpt)(request)
      }
    }
  }

  /**
   * Fetch a given item, along with its links and annotations.
   */
  object getAction {
    def async[MT](entityType: EntityType.Value, id: String)(
      f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                                       implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]): Action[AnyContent] = {
      itemAction.async[MT](entityType, id) { item => implicit userOpt => implicit request =>
        val annsReq = rest.AnnotationDAO(userOpt).getFor(id)
        val linkReq = rest.LinkDAO(userOpt).getFor(id)
        for { anns <- annsReq ; links <- linkReq ; r <- f(item)(anns)(links)(userOpt)(request) } yield r
      }
    }

    def apply[MT](entityType: EntityType.Value, id: String)(
      f: MT => Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], cfmt: ClientConvertable[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))
    }
  }

  /**
   * Fetch a given item with links, annotations, and a page of child items.
   */
  object getWithChildrenAction {
    def async[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => rest.Page[CT] => ListParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => Future[SimpleResult])(
                                   implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
      getAction.async[MT](entityType, id) { item => anns => links => implicit userOpt => implicit request =>
        val params = ListParams.fromRequest(request)
        rest.EntityDAO[MT](entityType, userOpt).pageChildren[CT](id, params).flatMap { children =>
          f(item)(children)(params)(anns)(links)(userOpt)(request)
        }
      }
    }

    def apply[CT, MT](entityType: EntityType.Value, id: String)(
      f: MT => rest.Page[CT] => ListParams =>  Map[String,List[Annotation]] => List[Link] => Option[UserProfile] => Request[AnyContent] => SimpleResult)(
      implicit rd: RestReadable[MT], crd: RestReadable[CT], cfmt: ClientConvertable[MT]) = {
      async(entityType, id)(f.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(_.andThen(t => Future.successful(t)))))))))
    }
  }



  /**
   * Full text search action that returns a complete page of item data.
   * @return
   */
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private val defaultSearchTypes = List(EntityType.Repository, EntityType.DocumentaryUnit, EntityType.HistoricalAgent)
  private val defaultSearchParams = SearchParams(entities = defaultSearchTypes, sort = Some(SearchOrder.Score))


  def search = searchAction[AnyModel](defaultParams = Some(defaultSearchParams), entityFacets = entityFacets, mode = SearchMode.DefaultNone) {
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
        case _ => Ok(views.html.portal.search(page, params, facets, portalRoutes.search))
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

    Ok(
      Routes.javascriptRouter("jsRoutes")(
      // TODO as necessary
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

  private val repositorySearchFacets: FacetBuilder = { implicit lang =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.itemsHeldOnline"),
        param="docs",
        render=s => Messages("portal.facet.itemsHeldOnline." + s),
        facets=List(
          SolrQueryFacet(value = "yes", solrValue = "[1 TO *]", name = Some("yes"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("isdiah.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  def browse = userProfileAction { implicit userOpt => implicit request =>
    Ok(portal.browse())
  }

  def browseCountries = Action.async { implicit request =>
    searchAction[Repository](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Country), entities = List(EntityType.Repository))),
      entityFacets = repositorySearchFacets) {
      page => params => facets => implicit userOpt => _ =>
        Ok(portal.repository.listByCountry(page, params, facets, portalRoutes.browseCountries))
    }.apply(request)
  }

  def browseCountry(id: String) = getAction.async[Country](EntityType.Country, id) {
      item => annotations => links => implicit userOpt => implicit request =>
    searchAction[Repository](Map("countryCode" -> item.id), entityFacets = repositorySearchFacets,
        defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(portal.country.show(item, page, params, facets,
          portalRoutes.browseCountry(id), annotations, links))
    }.apply(request)
  }

  private val docSearchFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key = IsadG.LANG_CODE,
        name = Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param = "lang",
        render = (s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.Choice,
        sort = FacetSort.Name
      ),
      QueryFacetClass(
        key="childCount",
        name=Messages("portal.facet.container"),
        param="items",
        render=s => Messages("portal.facet.container." + s),
        facets=List(
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        ),
        display = FacetDisplay.Boolean
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("portal.facet.location"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  def browseRepositories = searchAction[Repository](defaultParams = Some(SearchParams(entities = List(EntityType.Repository))),
    entityFacets = repositorySearchFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(portal.repository.list(page, params, facets, portalRoutes.browseRepositories))
  }

  def browseRepository(id: String) = getAction.async[Repository](EntityType.Repository, id) {
      item => annotations => links => implicit userOpt => implicit request =>
    val filters = (if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)
    searchAction[DocumentaryUnit](filters,
        defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
        entityFacets = docSearchFacets) {
        page => params => facets => _ => _ =>
      Ok(portal.repository.show(item, page, params, facets,
          portalRoutes.browseRepository(id), annotations, links))
    }.apply(request)
  }

  def browseDocuments = searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
    entityFacets = docSearchFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(portal.documentaryUnit.list(page, params, facets, portalRoutes.browseDocuments))
  }

  private val docSearchRepositoryFacets: FacetBuilder = { implicit lang =>
    docSearchFacets(lang) ++ List(
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      )
    )
  }

  def browseDocumentsByRepository = searchAction[DocumentaryUnit](defaultParams = Some(SearchParams(sort = Some(SearchOrder.Holder), entities = List(EntityType.DocumentaryUnit))),
    entityFacets = docSearchRepositoryFacets) {
    page => params => facets => implicit userOpt => implicit request =>
      Ok(portal.documentaryUnit.listByRepository(page, params, facets, portalRoutes.browseDocumentsByRepository))
  }

  def browseDocument(id: String) = getAction.async[DocumentaryUnit](EntityType.DocumentaryUnit, id) {
      item => annotations => links => implicit userOpt => implicit request =>
    val filters = Map(SolrConstants.PARENT_ID -> item.id)
    searchAction[DocumentaryUnit](filters,
      defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
      entityFacets = docSearchFacets) {
      page => params => facets => _ => _ =>
        Ok(portal.documentaryUnit.show(item, page, params, facets,
          portalRoutes.browseDocument(id), annotations, links))
    }.apply(request)
  }

  private val historicalAgentFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages(Isaar.FIELD_PREFIX + "." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  def browseHistoricalAgents = searchAction[HistoricalAgent](defaultParams = Some(SearchParams(entities = List(EntityType.HistoricalAgent))),
    entityFacets = historicalAgentFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(portal.historicalAgent.list(page, params, facets, portalRoutes.browseHistoricalAgents))
  }


  def browseAuthoritativeSet(id: String) = getAction.async[AuthoritativeSet](EntityType.AuthoritativeSet, id) {
    item => annotations => links => implicit userOpt => implicit request =>
      val filters = (if (request.getQueryString(SearchParams.QUERY).isEmpty)
        Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)
      searchAction[HistoricalAgent](filters,
          defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
          entityFacets = entityFacets) {
        page => params => facets => _ => _ =>
          Ok("TODO")
      }.apply(request)
  }

  def browseHistoricalAgent(id: String) = getAction[HistoricalAgent](EntityType.HistoricalAgent, id) {
      doc => anns => links => implicit userOpt => implicit request =>
    Ok(portal.historicalAgent.show(doc, anns, links))
  }

  def activity = userProfileAction { implicit userOpt => implicit request =>
    Ok(portal.activity())
  }

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
    Action.async { request =>
      WS.url("http://www.ehri-project.eu/rss.xml").get.map { r =>
        Ok(portal.newsFeed(NewsItem.fromRss(r.body)))
      }
    }
  }
}

