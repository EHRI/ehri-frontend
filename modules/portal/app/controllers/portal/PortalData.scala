package controllers.portal

import javax.inject.{Inject, Singleton}
import controllers.AppComponents
import play.api.cache.Cached
import play.api.http.{ContentTypes, MimeTypes}
import play.api.i18n.MessagesApi
import play.api.mvc._


@Singleton
case class PortalData @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  statusCache: Cached,
)(override implicit val messagesApi: MessagesApi) extends BaseController
  with play.api.i18n.I18nSupport {

  import scala.concurrent.duration._

  private val cacheTime = 1.hour

  def jsRoutes: EssentialAction = statusCache.status((_: RequestHeader) => "pages:portalJsRoutes", OK, cacheTime) {
    controllerComponents.actionBuilder { implicit request =>
      Ok(
        play.api.routing.JavaScriptReverseRouter("jsRoutes")(
          controllers.portal.routes.javascript.Bookmarks.moveBookmarksPost,
          controllers.portal.routes.javascript.Bookmarks.contents,
          controllers.portal.routes.javascript.Bookmarks.bookmarkPost,
          controllers.portal.routes.javascript.Bookmarks.bookmarkInNewSetPost,
          controllers.portal.users.routes.javascript.UserProfiles.watchItemPost,
          controllers.portal.users.routes.javascript.UserProfiles.unwatchItemPost,
          controllers.portal.users.routes.javascript.UserProfiles.profile,
          controllers.portal.users.routes.javascript.UserProfiles.updateProfile,
          controllers.portal.users.routes.javascript.UserProfiles.updateProfilePost,
          controllers.portal.social.routes.javascript.Social.followUserPost,
          controllers.portal.social.routes.javascript.Social.unfollowUserPost,
          controllers.portal.social.routes.javascript.Social.followersForUser,
          controllers.portal.social.routes.javascript.Social.followingForUser,
          controllers.portal.social.routes.javascript.Social.blockUserPost,
          controllers.portal.social.routes.javascript.Social.unblockUserPost,
          controllers.portal.social.routes.javascript.Social.sendMessage,
          controllers.portal.annotate.routes.javascript.Annotations.annotate,
          controllers.portal.annotate.routes.javascript.Annotations.annotatePost,
          controllers.portal.annotate.routes.javascript.Annotations.annotateField,
          controllers.portal.annotate.routes.javascript.Annotations.annotateFieldPost,
          controllers.portal.annotate.routes.javascript.Annotations.editAnnotation,
          controllers.portal.annotate.routes.javascript.Annotations.editAnnotationPost,
          controllers.portal.annotate.routes.javascript.Annotations.deleteAnnotation,
          controllers.portal.annotate.routes.javascript.Annotations.deleteAnnotationPost,
          controllers.portal.annotate.routes.javascript.Annotations.promoteAnnotationPost,
          controllers.portal.annotate.routes.javascript.Annotations.demoteAnnotationPost,
          controllers.portal.routes.javascript.Portal.updatePrefs,
          controllers.portal.routes.javascript.Portal.personalisedActivity,
          controllers.portal.routes.javascript.Portal.filterItems,
          controllers.portal.routes.javascript.Portal.browseItem,
          controllers.portal.routes.javascript.Portal.externalFeed
        )
      ).as(MimeTypes.JAVASCRIPT)
    }
  }

  /**
    * Render entity types into the view to serve as JS constants.
    *
    * @return
    */
  def globalData: EssentialAction = statusCache.status((_: RequestHeader) => "pages:globalData", OK, cacheTime) {
    controllerComponents.actionBuilder { implicit request =>
      import models.EntityType
      Ok(
        """
          |var EntityTypes = {
          |%s
          |};
        """.stripMargin.format(
          "\t" + EntityType.values.map(et => s"$et: '$et'").mkString(",\n\t"))
      ).as(MimeTypes.JAVASCRIPT)
    }
  }

  def localeData(lang: String): EssentialAction = statusCache.status((_: RequestHeader) => "pages:localeData", OK, cacheTime) {
    controllerComponents.actionBuilder { implicit request =>
      //implicit val locale: Lang = request.lang

      val js =
        """
          |var __languageData = {
          |  %s
          |};
          |
          |var __countryData = {
          |  %s
          |};
          |
          |var LocaleData = {
          |  languageCodeToName: function(code) {
          |    return __languageData[code] || code;
          |  },
          |  countryCodeToName: function(code) {
          |    return __countryData[code] || code;
          |  },
          |}
        """.stripMargin.format(
          i18n.languagePairList.map { case (code, name) =>
            code + ": \"" + name + "\""
          }.mkString(",\n  "),
          i18n.countryPairList.map { case (code, name) =>
            code.toLowerCase + ": \"" + name + "\""
          }.mkString(",\n  ")
        )

      Ok(js).as(ContentTypes.JAVASCRIPT)
    }
  }
}
