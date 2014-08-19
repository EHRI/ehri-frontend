package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import models.base.AnyModel
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.p
import utils.search._
import defines.EntityType
import solr.SolrConstants
import backend.{IdGenerator, Backend}
import controllers.base.{SessionPreferences, ControllerHelpers}
import jp.t2v.lab.play2.auth.LoginLogout
import utils._

import com.google.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.rest.{ItemNotFound, Constants}

@Singleton
case class VirtualUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO, idGenerator: IdGenerator)
  extends Controller
  with LoginLogout
  with ControllerHelpers
  with Search
  with FacetConfig
  with PortalActions
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val vuRoutes = controllers.portal.routes.VirtualUnits

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  private def buildFilter(v: VirtualUnit): Map[String,Any] = {
    // Nastiness. We want a Solr query that will allow searching
    // both the child virtual collections of a VU as well as the
    // physical documentary units it includes. Since there is no
    // connection from the DU to VUs it belongs to (and creating
    // one is not feasible) we need to do this badness:
    // - load the VU from the graph along with its included DUs
    // - query for anything that has the VUs parent ID *or* anything
    // with an itemId among its included DUs
    val pairs: List[(String, String)] =
      SolrConstants.PARENT_ID -> v.id :: v.includedUnits.map(inc => SolrConstants.ITEM_ID -> inc.id)
    Map(s"(${pairs.map(t => t._1 + ":" + t._2).mkString(" OR ")})" -> Unit)
  }

  private def defaultBookmarkSetId(implicit user: UserProfile): String =
    s"${user.id}-bookmarks"

  private def bookmarkLang(implicit request: RequestHeader): String =
    utils.i18n.lang2to3lookup.getOrElse(request2lang.language, "eng")

  private def bookmarkSetToVu(genId: String, bs: BookmarkSet)(implicit request: RequestHeader): VirtualUnitF = {
    VirtualUnitF(
      identifier = genId,
      descriptions = List(
        DocumentaryUnitDescriptionF(
          id = None, languageCode = bookmarkLang, identity = IsadGIdentity(name = bs.name),
          content = IsadGContent(scopeAndContent = bs.description)
        )
      )
    )
  }

  private def defaultBookmarkSet(implicit user: UserProfile, request: RequestHeader): VirtualUnitF =
    bookmarkSetToVu(defaultBookmarkSetId, BookmarkSet(s"Bookmarked Items", description = None))

  /**
   * Bookmark an item, creating (if necessary) a default virtual
   * collection private to the user.
   */
  def bookmark(itemId: String, bsId: Option[String] = None) = withUserAction.async {
      implicit user => implicit request =>

    def getOrCreateBS(idOpt: Option[String]): Future[VirtualUnit] = {
      backend.get[VirtualUnit](idOpt.getOrElse(defaultBookmarkSetId)).map { vu =>
        backend.addBookmark(vu.id, itemId)
        vu
      } recoverWith {
        case e: ItemNotFound => backend.create[VirtualUnit,VirtualUnitF](
          item = defaultBookmarkSet,
          accessors = Seq(user.id),
          params = Map(Constants.ID_PARAM -> Seq(itemId))
        )
      }
    }

    getOrCreateBS(bsId).map { vu =>
      if (isAjax) Ok("ok")
      else Redirect(vuRoutes.browseVirtualCollection(id = vu.id))
    }
  }

  def listBookmarkSets = withUserAction.async { implicit user => implicit request =>
    val pageF = backend.userBookmarks(user.id, PageParams.fromRequest(request))
    val watchedF = watchedItems
    for {
      page <- pageF
      watched <- watchedF
    } yield Ok(p.profile.bookmarkSets(page, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = withUserAction { implicit user => implicit request =>
    if (isAjax) Ok(p.profile.bookmarkSetForm(BookmarkSet.bookmarkForm, vuRoutes.createBookmarkSetPost(items)))
    else Ok(p.profile.createBookmarkSet(BookmarkSet.bookmarkForm, vuRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil) = withUserAction.async { implicit user => implicit request =>

    BookmarkSet.bookmarkForm.bindFromRequest.fold(
      errs => immediate {
        if (isAjax) Ok(p.profile.bookmarkSetForm(errs, vuRoutes.createBookmarkSetPost(items)))
        else Ok(p.profile.createBookmarkSet(errs, vuRoutes.createBookmarkSetPost(items)))
      },
      bs => for {
        nextid <- idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit)
        vuForm = bookmarkSetToVu(s"${user.id}-vu$nextid", bs)
        vu <- backend.create[VirtualUnit,VirtualUnitF](
          vuForm,
          accessors = Seq(user.id),
          params = Map(Constants.ID_PARAM -> items))
      } yield {
        if (isAjax) Ok("ok")
        else Redirect(vuRoutes.listBookmarkSets())
      }
    )
  }

  def browseVirtualCollection(id: String) = getAction[VirtualUnit](EntityType.VirtualUnit, id) {
    item => details => implicit userOpt => implicit request =>
      if (isAjax) Ok(p.virtualUnit.itemDetailsVc(item, details.annotations, details.links, details.watched))
      else Ok(p.virtualUnit.show(item, details.annotations, details.links, details.watched))
  }

  def searchVirtualCollection(id: String) = getAction.async[VirtualUnit](EntityType.VirtualUnit, id) {
    item => details => implicit userOpt => implicit request =>
      find[AnyModel](
        filters = buildFilter(item),
        entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
        facetBuilder = docSearchFacets
      ).map { case QueryResult(page, params, facets) =>
        if (isAjax) Ok(p.virtualUnit.childItemSearch(item, page, params, facets,
          vuRoutes.searchVirtualCollection(id), details.watched))
        else Ok(p.virtualUnit.search(item, page, params, facets,
          vuRoutes.searchVirtualCollection(id), details.watched))
      }
  }

  def browseVirtualCollections = userBrowseAction.async { implicit userDetails => implicit request =>
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[VirtualUnit](
      filters = filters,
      entities = List(EntityType.VirtualUnit),
      facetBuilder = docSearchFacets
    ).map { case QueryResult(page, params, facets) =>
      Ok(p.virtualUnit.list(page, params, facets, vuRoutes.browseVirtualCollections(),
        userDetails.watchedItems))
    }
  }

  def browseVirtualUnit(pathStr: String, id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    val pathIds = pathStr.split(",").toSeq
    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val linksF: Future[Seq[Link]] = backend.getLinksForItem(id)
    val annsF: Future[Seq[Annotation]] = backend.getAnnotationsForItem(id)
    val watchedF: Future[Page[AnyModel]] = watchedItems
    for {
      watched <- watchedF
      item <- itemF
      links <- linksF
      annotations <- annsF
      path <- pathF
    } yield {
      if (isAjax) Ok(p.virtualUnit.itemDetailsVc(item, annotations, links, watched, path))
      else Ok(p.virtualUnit.show(item, annotations, links, watched, path))
    }
  }

  def searchVirtualUnit(pathStr: String, id: String) = userProfileAction.async { implicit userOpt => implicit request =>
    val pathIds = pathStr.split(",").toSeq

    def includedChildren(parent: AnyModel): Future[QueryResult[AnyModel]] = parent match {
      case d: DocumentaryUnit => find[AnyModel](
        filters = Map(SolrConstants.PARENT_ID -> d.id),
        entities = List(d.isA),
        facetBuilder = docSearchFacets)
      case d: VirtualUnit => d.includedUnits match {
        case _ => find[AnyModel](
          filters = buildFilter(d),
          entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
          facetBuilder = docSearchFacets)
      }
      case _ => Future.successful(QueryResult.empty)
    }

    val pathF: Future[Seq[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val linksF: Future[Seq[Link]] = backend.getLinksForItem(id)
    val annsF: Future[Seq[Annotation]] = backend.getAnnotationsForItem(id)
    val watchedF: Future[Page[AnyModel]] = watchedItems
    for {
      watched <- watchedF
      item <- itemF
      links <- linksF
      annotations <- annsF
      path <- pathF
      children <- includedChildren(item)
    } yield {
      if (isAjax)
        Ok(p.virtualUnit.childItemSearch(item, children.page, children.params, children.facets,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
      else Ok(p.virtualUnit.search(item, children.page, children.params, children.facets,
          vuRoutes.searchVirtualUnit(pathStr, id), watched, path))
    }
  }
}

