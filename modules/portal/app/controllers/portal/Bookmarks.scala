package controllers.portal

import javax.inject._
import services.cypher.CypherService
import controllers.AppComponents
import controllers.generic.Search
import controllers.portal.base.PortalController
import models.{EntityType, Model, _}
import play.api.cache.SyncCacheApi
import play.api.http.HeaderNames
import play.api.i18n.Messages
import play.api.mvc._
import services.data.{Constants, IdGenerator, ItemNotFound}
import utils._
import services.search._

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Bookmarks @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  idGenerator: IdGenerator,
  cypher: CypherService,
  fc: FacetConfig,
  cache: SyncCacheApi,
) extends PortalController
  with Search {

  private val bmRoutes = controllers.portal.routes.Bookmarks
  private val vuRoutes = controllers.portal.routes.VirtualUnits

  private def defaultBookmarkSetId(implicit user: UserProfile): String =
    s"${user.id}-bookmarks"

  private def bookmarkLang(implicit messages: Messages): String =
    i18n.lang2to3lookup.getOrElse(messages.lang.language, i18n.defaultLang)

  /**
    * Implicit helper to transform an in-scope `ProfileRequest` (of any type)
    * into a user profile. Used by views that need a user profile but are only given
    * a `ProfileRequest`
    *
    * @param pr the profile request
    * @return an optional user profile
    */
  private implicit def profileRequest2profile(implicit pr: WithUserRequest[_]): UserProfile = pr.user

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
      vu <- userDataApi.create[VirtualUnit, VirtualUnitF](
        vuForm,
        accessors = Seq(user.id),
        params = Map(Constants.ID_PARAM -> items))
    } yield vu

  def removeBookmarksPost(set: String, ids: Seq[String]): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.deleteReferences[VirtualUnit](set, ids).map(_ => Ok("ok"))
  }

  def moveBookmarksPost(fromSet: String, toSet: String, ids: Seq[String] = Seq.empty): Action[AnyContent] = WithUserAction.async { implicit request =>
    userDataApi.moveReferences[VirtualUnit](fromSet, toSet, ids).map(_ => Ok("ok"))
  }

  def bookmarkInNewSetPost(id: String): Action[AnyContent] = createBookmarkSetPost(List(id))

  def bookmark(itemId: String, bsId: Option[String] = None): Action[AnyContent] = WithUserAction.apply { implicit request =>
    Ok(views.html.helpers.simpleForm("bookmark.item", bmRoutes.bookmarkPost(itemId, bsId)))
  }

  /**
    * Bookmark an item, creating (if necessary) a default virtual
    * collection private to the user.
    */
  def bookmarkPost(itemId: String, bsId: Option[String] = None): Action[AnyContent] = WithUserAction.async { implicit request =>

    def getOrCreateBS(idOpt: Option[String]): Future[VirtualUnit] = {
      userDataApi.get[VirtualUnit](idOpt.getOrElse(defaultBookmarkSetId)).map { vu =>
        userDataApi.addReferences[VirtualUnit](vu.id, Seq(itemId))
        vu
      } recoverWith {
        case e: ItemNotFound => userDataApi.create[VirtualUnit, VirtualUnitF](
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

  def listBookmarkSets(paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    val pageF = userDataApi.userBookmarks[VirtualUnit](request.user.id, paging)
    val watchedF = watchedItemIds(userIdOpt = Some(request.user.id))
    for {
      page <- pageF
      watched <- watchedF
      result = SearchResult(page, SearchParams.empty)
    } yield Ok(views.html.bookmarks.list(paging, result, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = WithUserAction { implicit request =>
    if (isAjax) Ok(views.html.bookmarks.form(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
    else Ok(views.html.bookmarks.create(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil): Action[AnyContent] = WithUserAction.async { implicit request =>
    BookmarkSet.bookmarkForm.bindFromRequest().fold(
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

  private def buildFilter(v: VirtualUnit): Map[String, Any] = {
    val pq = v.includedUnits.map(_.id)
    if (pq.isEmpty) Map(s"${SearchConstants.PARENT_ID}:${v.id}" -> Unit)
    else {
      val q = s"${SearchConstants.PARENT_ID}:${v.id} OR ${SearchConstants.ITEM_ID}:(${pq.mkString(" ")})"
      Map(q -> Unit)
    }
  }

  private def includedChildren(id: String, parent: Model, params: SearchParams, paging: PageParams = PageParams.empty)(implicit userOpt: Option[UserProfile], req: RequestHeader): Future[SearchResult[(Model, SearchHit)]] = {
    parent match {
      case d: DocumentaryUnit =>
        find[Model](
          filters = Map(SearchConstants.PARENT_ID -> d.id),
          paging = paging,
          params = params,
          entities = List(d.isA),
          facetBuilder = fc.docSearchFacets)
      case d: VirtualUnit => d.includedUnits match {
        case _ => find[Model](
          filters = buildFilter(d),
          paging = paging,
          params = params,
          entities = List(EntityType.VirtualUnit, EntityType.DocumentaryUnit),
          facetBuilder = fc.docSearchFacets)
      }
      case _ => Future.successful(SearchResult.empty)
    }
  }


  def contents(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = WithUserAction.async { implicit request =>
    val itemF: Future[Model] = userDataApi.getAny[Model](id)
    val watchedF: Future[Seq[String]] = watchedItemIds(userIdOpt = Some(request.user.id))
    for {
      item <- itemF
      children <- includedChildren(id, item, params, paging)
      watched <- watchedF
    } yield Ok(views.html.bookmarks.itemList(
      Some(item),
      request.user,
      paging,
      children.mapItems(_._1),
      children.page.hasMore,
      watched
    ))
  }
}

