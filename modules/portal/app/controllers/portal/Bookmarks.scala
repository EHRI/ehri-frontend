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
    val pageF = backend.userBookmarks[VirtualUnit](user.id, PageParams.fromRequest(request))
    val watchedF = watchedItemIds(userIdOpt = Some(user.id))
    for {
      page <- pageF
      watched <- watchedF
    } yield Ok(p.bookmarks.list(page, watched))
  }

  def createBookmarkSet(items: List[String] = Nil) = withUserAction { implicit user => implicit request =>
    if (isAjax) Ok(p.bookmarks.renderForm(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
    else Ok(p.bookmarks.create(BookmarkSet.bookmarkForm, bmRoutes.createBookmarkSetPost(items)))
  }

  def createBookmarkSetPost(items: List[String] = Nil) = withUserAction.async { implicit user => implicit request =>
    BookmarkSet.bookmarkForm.bindFromRequest.fold(
      errs => immediate {
        if (isAjax) Ok(p.bookmarks.renderForm(errs, bmRoutes.createBookmarkSetPost(items)))
        else Ok(p.bookmarks.create(errs, bmRoutes.createBookmarkSetPost(items)))
      },
      bs => createVirtualCollection(bs, items).map { vu =>
        if (isAjax) Ok("ok")
          .withHeaders(HeaderNames.LOCATION -> vuRoutes.browseVirtualCollection(vu.id).url)
        else Redirect(bmRoutes.listBookmarkSets())
      }
    )
  }
}

