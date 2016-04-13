package controllers.portal

import auth.AccountManager
import backend.rest.cypher.Cypher
import controllers.generic.Search
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import utils.search._
import defines.EntityType
import backend.{IdGenerator, DataApi}
import utils._
import javax.inject._
import views.MarkdownRenderer

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import backend.rest.{ItemNotFound, Constants}
import play.api.i18n.{MessagesApi, Messages}
import play.api.http.HeaderNames
import play.api.cache.CacheApi
import models.base.AnyModel
import controllers.portal.base.PortalController


@Singleton
case class Bookmarks @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  accounts: AccountManager,
  idGenerator: IdGenerator,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends PortalController
  with FacetConfig
  with Search {

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  private def defaultBookmarkSetId(implicit user: UserProfile): String =
    s"${user.id}-bookmarks"

  private def bookmarkLang(implicit messages: Messages): String =
    utils.i18n.lang2to3lookup.getOrElse(messages.lang.language, utils.i18n.defaultLang)

  /**
   * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
   * into a user profile. Used by views that need a user profile but are only given
   * a `ProfileRequest`
   *
   * @param pr the profile request
   * @return an optional user profile
   */
  private implicit def profileRequest2profile(implicit pr: WithUserRequest[_]): UserProfile =
    pr.user

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
    bookmarkSetToVu(defaultBookmarkSetId, BookmarkSet(Messages("bookmarkSet.defaultSetName"), lang = lang, description = None))
  }

  private def createVirtualCollection(bs: BookmarkSet, items: List[String] = Nil)(implicit user: UserProfile): Future[VirtualUnit] =
    for {
      nextid <- idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit, "%06d")
      vuForm = bookmarkSetToVu(s"${user.id}-vu$nextid", bs)
      vu <- userDataApi.create[VirtualUnit,VirtualUnitF](
        vuForm,
        accessors = Seq(user.id),
        params = Map(Constants.ID_PARAM -> items))
    } yield vu

  def removeBookmarksPost(set: String, ids: Seq[String]) = WithUserAction.async { implicit request =>
    userDataApi.deleteReferences[VirtualUnit](set, ids).map(_ => Ok("ok"))
  }

  def moveBookmarksPost(fromSet: String, toSet: String, ids: Seq[String] = Seq.empty) = WithUserAction.async { implicit request =>
    userDataApi.moveReferences[VirtualUnit](fromSet, toSet, ids).map(_ => Ok("ok"))
  }

  def bookmarkInNewSetPost(id: String) = createBookmarkSetPost(List(id))

  def bookmark(itemId: String, bsId: Option[String] = None) = WithUserAction.apply { implicit request =>
    Ok(views.html.helpers.simpleForm("bookmark.item", bmRoutes.bookmarkPost(itemId, bsId)))
  }

  /**
   * Bookmark an item, creating (if necessary) a default virtual
   * collection private to the user.
   */
  def bookmarkPost(itemId: String, bsId: Option[String] = None) = WithUserAction.async { implicit request =>

    def getOrCreateBS(idOpt: Option[String]): Future[VirtualUnit] = {
      userDataApi.get[VirtualUnit](idOpt.getOrElse(defaultBookmarkSetId)).map { vu =>
        userDataApi.addReferences[VirtualUnit](vu.id, Seq(itemId))
        vu
      } recoverWith {
        case e: ItemNotFound => userDataApi.create[VirtualUnit,VirtualUnitF](
          item = defaultBookmarkSet(bookmarkLang),
          accessors = Seq(request.user.id),
          params = Map(Constants.ID_PARAM -> Seq(itemId))
        )
      }
    }

    getOrCreateBS(bsId).map { vu =>
      cache.remove(vu.id)
      if (isAjax) Ok("ok")
        .withHeaders(HeaderNames.LOCATION -> vuRoutes.browseVirtualCollection(vu.id).url)
      else Redirect(bmRoutes.listBookmarkSets())
    }
  }

  def listBookmarkSets = WithUserAction.async { implicit request =>
    val params: PageParams = PageParams.fromRequest(request)
    val pageF = userDataApi.userBookmarks[VirtualUnit](request.user.id, params)
    val watchedF = watchedItemIds(userIdOpt = Some(request.user.id))
    for {
      page <- pageF
      watched <- watchedF
      result = SearchResult(page, SearchParams.empty)
    } yield Ok(views.html.bookmarks.list(result, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = WithUserAction { implicit request =>
    if (isAjax) Ok(views.html.bookmarks.form(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
    else Ok(views.html.bookmarks.create(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil) = WithUserAction.async { implicit request =>
    BookmarkSet.bookmarkForm.bindFromRequest.fold(
      errs => immediate {
        if (isAjax) Ok(views.html.bookmarks.form(errs, bmRoutes.createBookmarkSetPost(items)))
        else Ok(views.html.bookmarks.create(errs, bmRoutes.createBookmarkSetPost(items)))
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
    if (pq.isEmpty) Map(s"${SearchConstants.PARENT_ID}:${v.id}" -> Unit)
    else {
      val q = s"${SearchConstants.PARENT_ID}:${v.id} OR ${SearchConstants.ITEM_ID}:(${pq.mkString(" ")})"
      Map(q -> Unit)
    }
  }

  private def includedChildren(id: String, parent: AnyModel, page: Int = 1)(implicit userOpt: Option[UserProfile], req: RequestHeader): Future[SearchResult[(AnyModel,SearchHit)]] = {
    val params: SearchParams = SearchParams.empty.copy(page = Some(page))
    parent match {
      case d: DocumentaryUnit =>
        find[AnyModel](
          filters = Map(SearchConstants.PARENT_ID -> d.id),
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
      case _ => Future.successful(SearchResult.empty)
    }
  }


  def contents(id: String) = WithUserAction.async { implicit request =>
    val itemF: Future[AnyModel] = userDataApi.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(request.user.id))
    for {
      item <- itemF
      children <- includedChildren(id, item)
      watched <- watchedF
    } yield {
      Ok(views.html.bookmarks.itemList(
        Some(item),
        request.user,
        children.mapItems(_._1),
        children.page.hasMore,
        watched
      ))
    }
  }

  def moreContents(id: String, page: Int) = WithUserAction.async { implicit request =>
    val itemF: Future[AnyModel] = userDataApi.getAny[AnyModel](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(request.user.id))
    for {
      item <- itemF
      children <- includedChildren(id, item, page = page)
      watched <- watchedF
    } yield {
      Ok(views.html.bookmarks.itemListItems(
        Some(item),
        children.page.copy(items = children.page.items.map(_._1)),
        children.params,
        children.page.hasMore,
        watched
      ))
    }
  }
}

