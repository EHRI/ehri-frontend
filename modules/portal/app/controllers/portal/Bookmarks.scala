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
import controllers.base.SessionPreferences
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
import controllers.portal.base.PortalController

@Singleton
case class Bookmarks @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
    userDAO: AccountDAO, idGenerator: IdGenerator)
  extends PortalController
  with FacetConfig
  with Search {

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  private def defaultBookmarkSetId(implicit user: UserProfile): String =
    s"${user.id}-bookmarks"

  private def bookmarkLang(implicit lang: Lang): String =
    utils.i18n.lang2to3lookup.getOrElse(lang.language, utils.i18n.defaultLang)

  /**
   * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
   * into a user profile. Used by views that need a user profile but are only given
   * a `ProfileRequest`
   *
   * @param pr the profile request
   * @return an optional user profile
   */
  private implicit def profileRequest2profile(implicit pr: WithUserRequest[_]): UserProfile =
    pr.profile

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

  private def defaultBookmarkSet(lang: String)(implicit user: UserProfile, request: RequestHeader): VirtualUnitF = {
    import play.api.i18n.Messages
    bookmarkSetToVu(defaultBookmarkSetId, BookmarkSet(Messages("portal.bookmarkSet.defaultSetName"), lang = lang, description = None))
  }

  private def createVirtualCollection(bs: BookmarkSet, items: List[String] = Nil)(implicit user: UserProfile): Future[VirtualUnit] =
    for {
      nextid <- idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit)
      vuForm = bookmarkSetToVu(s"${user.id}-vu$nextid", bs)
      vu <- backend.create[VirtualUnit,VirtualUnitF](
        vuForm,
        accessors = Seq(user.id),
        params = Map(Constants.ID_PARAM -> items))
    } yield vu

  def removeBookmarksPost(set: String, ids: Seq[String]) = WithUserAction.async { implicit request =>
    backend.deleteBookmarks[VirtualUnit](set, ids).map(_ => Ok("ok"))
  }

  def moveBookmarksPost(fromSet: String, toSet: String, ids: Seq[String] = Seq.empty) = WithUserAction.async { implicit request =>
    backend.moveBookmarks[VirtualUnit](fromSet, toSet, ids).map(_ => Ok("ok"))
  }

  def bookmarkInNewSetPost(id: String) = createBookmarkSetPost(List(id))

  def bookmark(itemId: String, bsId: Option[String] = None) = WithUserAction.async { implicit request =>
    ???
  }

  /**
   * Bookmark an item, creating (if necessary) a default virtual
   * collection private to the user.
   */
  def bookmarkPost(itemId: String, bsId: Option[String] = None) = WithUserAction.async { implicit request =>

    def getOrCreateBS(idOpt: Option[String]): Future[VirtualUnit] = {
      backend.get[VirtualUnit](idOpt.getOrElse(defaultBookmarkSetId)).map { vu =>
        backend.addBookmark[VirtualUnit](vu.id, itemId)
        vu
      } recoverWith {
        case e: ItemNotFound => backend.create[VirtualUnit,VirtualUnitF](
          item = defaultBookmarkSet(bookmarkLang),
          accessors = Seq(request.profile.id),
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

  def listBookmarkSets = WithUserAction.async { implicit request =>
    val params: PageParams = PageParams.fromRequest(request)
    val pageF = backend.userBookmarks[VirtualUnit](request.profile.id, params)
    val watchedF = watchedItemIds(userIdOpt = Some(request.profile.id))
    for {
      page <- pageF
      watched <- watchedF
    } yield Ok(p.bookmarks.list(page, SearchParams.empty, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = WithUserAction { implicit request =>
    if (isAjax) Ok(p.bookmarks.form(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
    else Ok(p.bookmarks.create(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil) = WithUserAction.async { implicit request =>
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


  def contents(id: String) = WithUserAction.async { implicit request =>
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(request.profile.id))
    for {
      item <- itemF
      children <- includedChildren(id, item)
      watched <- watchedF
    } yield {
      Ok(p.bookmarks.itemList(
        Some(item),
        request.profile,
        children.page.copy(items = children.page.items.map(_._1)),
        children.params,
        children.page.hasMore,
        watched
      ))
    }
  }

  def moreContents(id: String, page: Int) = WithUserAction.async { implicit request =>
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(request.profile.id))
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

