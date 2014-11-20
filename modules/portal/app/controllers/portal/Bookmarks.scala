package controllers.portal

import play.api.Play.current
import controllers.generic.Search
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.p
import utils.search._
import defines.EntityType
import backend.{IdGenerator, Backend}
import controllers.base.{SessionPreferences, ControllerHelpers}
import jp.t2v.lab.play2.auth.LoginLogout
import utils._

import com.google.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.rest.{ItemNotFound, Constants}
import play.api.i18n.Lang
import play.api.http.HeaderNames
import play.api.cache.Cache
import models.base.AnyModel
import solr.SolrConstants

@Singleton
case class Bookmarks @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO, idGenerator: IdGenerator)
  extends Controller
  with LoginLogout
  with ControllerHelpers
  with Search
  with FacetConfig
  with PortalBase
  with SessionPreferences[SessionPrefs] {

  val defaultPreferences = new SessionPrefs

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)

  private def defaultBookmarkSetId(implicit user: UserProfile): String =
    s"${user.id}-bookmarks"

  private def bookmarkLang(implicit lang: Lang): String =
    utils.i18n.lang2to3lookup.getOrElse(lang.language, utils.i18n.defaultLang)

  private def bookmarkSetToVu(genId: String, bs: BookmarkSet): VirtualUnitF = {
    VirtualUnitF(
      identifier = genId,
      descriptions = List(
        DocumentaryUnitDescriptionF(
          id = None, languageCode = bs.lang, identity = IsadGIdentity(name = bs.name),
          content = IsadGContent(scopeAndContent = bs.description)
        )
      )
    )
  }

  private def defaultBookmarkSet(lang: String)(implicit user: UserProfile): VirtualUnitF =
    bookmarkSetToVu(defaultBookmarkSetId, BookmarkSet(s"Bookmarked Items", lang = lang, description = None))

  private def createVirtualCollection(bs: BookmarkSet, items: List[String] = Nil)(implicit user: UserProfile): Future[VirtualUnit] =
    for {
      nextid <- idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit)
      vuForm = bookmarkSetToVu(s"${user.id}-vu$nextid", bs)
      vu <- backend.create[VirtualUnit,VirtualUnitF](
        vuForm,
        accessors = Seq(user.id),
        params = Map(Constants.ID_PARAM -> items))
    } yield vu

  def removeBookmarksPost(set: String, ids: Seq[String]) = withUserAction.async { implicit user => implicit request =>
    backend.deleteBookmarks(set, ids).map(_ => Ok("ok"))
  }

  def moveBookmarksPost(fromSet: String, toSet: String, ids: Seq[String] = Seq.empty) = withUserAction.async {
      implicit user => implicit request =>
    backend.moveBookmarks(fromSet, toSet, ids).map(_ => Ok("ok"))
  }

  def bookmarkInNewSetPost(id: String) = createBookmarkSetPost(List(id))

  def bookmark(itemId: String, bsId: Option[String] = None) = withUserAction.async {
      implicit user => implicit request =>
    ???
  }

  /**
   * Bookmark an item, creating (if necessary) a default virtual
   * collection private to the user.
   */
  def bookmarkPost(itemId: String, bsId: Option[String] = None) = withUserAction.async {
      implicit user => implicit request =>

    def getOrCreateBS(idOpt: Option[String]): Future[VirtualUnit] = {
      backend.get[VirtualUnit](idOpt.getOrElse(defaultBookmarkSetId)).map { vu =>
        backend.addBookmark(vu.id, itemId)
        vu
      } recoverWith {
        case e: ItemNotFound => backend.create[VirtualUnit,VirtualUnitF](
          item = defaultBookmarkSet(bookmarkLang),
          accessors = Seq(user.id),
          params = Map(Constants.ID_PARAM -> Seq(itemId))
        )
      }
    }

    getOrCreateBS(bsId).map { vu =>
      Cache.remove(vu.id)
      if (isAjax) Ok("ok")
        .withHeaders(HeaderNames.LOCATION -> vuRoutes.browseVirtualCollection(vu.id).url)
      else Redirect(bmRoutes.listBookmarkSets())
    }
  }

  def listBookmarkSets = withUserAction.async { implicit user => implicit request =>
    val params: PageParams = PageParams.fromRequest(request)
    val pageF = backend.userBookmarks[VirtualUnit](user.id, params)
    val watchedF = watchedItemIds(userIdOpt = Some(user.id))
    for {
      page <- pageF
      watched <- watchedF
    } yield Ok(p.bookmarks.list(page, SearchParams.empty, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = withUserAction { implicit user => implicit request =>
    if (isAjax) Ok(p.bookmarks.form(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
    else Ok(p.bookmarks.create(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil) = withUserAction.async { implicit user => implicit request =>
    BookmarkSet.bookmarkForm.bindFromRequest.fold(
      errs => immediate {
        if (isAjax) Ok(p.bookmarks.form(errs, bmRoutes.createBookmarkSetPost(items)))
        else Ok(p.bookmarks.create(errs, bmRoutes.createBookmarkSetPost(items)))
      },
      bs => createVirtualCollection(bs, items).map { vu =>
        if (isAjax) Ok("ok")
          .withHeaders(HeaderNames.LOCATION -> vuRoutes.browseVirtualCollection(vu.id).url)
        else Redirect(bmRoutes.listBookmarkSets())
      }
    )
  }

  private def buildFilter(v: VirtualUnit): Map[String,Any] = {
    val pq = v.includedUnits.map(_.id)
    if (pq.isEmpty) Map(s"${SolrConstants.PARENT_ID}:${v.id}" -> Unit)
    else {
      val q = s"${SolrConstants.PARENT_ID}:${v.id} OR ${SolrConstants.ITEM_ID}:(${pq.mkString(" ")})"
      Map(q -> Unit)
    }
  }

  private def includedChildren(id: String, parent: AnyModel, page: Int = 1)(implicit userOpt: Option[UserProfile], req: RequestHeader): Future[QueryResult[AnyModel]] = {
    val params: SearchParams = SearchParams.empty.copy(page = Some(page))
    parent match {
      case d: DocumentaryUnit =>
        find[AnyModel](
          filters = Map(SolrConstants.PARENT_ID -> d.id),
          defaultParams = params,
          entities = List(d.isA),
          facetBuilder = docSearchFacets)
      case d: VirtualUnit => d.includedUnits match {
        case _ => find[AnyModel](
          filters = buildFilter(d),
          defaultParams = params,
          entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
          facetBuilder = docSearchFacets)
      }
      case _ => Future.successful(QueryResult.empty)
    }
  }


  def contents(id: String) = withUserAction.async { implicit user => implicit request =>
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = userOpt.map(_.id))
    for {
      item <- itemF
      children <- includedChildren(id, item)
      watched <- watchedF
    } yield {
      Ok(p.bookmarks.itemList(
        Some(item),
        user,
        children.page.copy(items = children.page.items.map(_._1)),
        children.params,
        children.page.hasMore,
        watched
      ))
    }
  }

  def moreContents(id: String, page: Int) = withUserAction.async { implicit user => implicit request =>
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = userOpt.map(_.id))
    for {
      item <- itemF
      children <- includedChildren(id, item, page = page)
      watched <- watchedF
    } yield {
      Ok(p.bookmarks.itemListItems(
        Some(item),
        children.page.copy(items = children.page.items.map(_._1)),
        children.params,
        children.page.hasMore,
        watched
      ))
    }
  }
}

