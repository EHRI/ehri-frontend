package controllers.portal

import javax.inject.{Inject, Singleton}

import controllers.Components
import play.api.cache.Cached
import play.api.http.{ContentTypes, MimeTypes}
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{Action, Controller, EssentialAction, RequestHeader}


@Singleton
case class PortalData @Inject()(statusCache: Cached)(implicit val messagesApi: MessagesApi) extends Controller
  with play.api.i18n.I18nSupport {

  def jsRoutes: EssentialAction = statusCache.status(_ => "pages:portalJsRoutes", OK, 3600) {
    Action { implicit request =>
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
          controllers.portal.annotate.routes.javascript.Annotations.setAnnotationVisibilityPost,
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
   * @return
   */
  def globalData: EssentialAction = statusCache.status(_ => "pages:globalData", OK, 3600) {
    Action { implicit request =>
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
  }

  /**
   * Handle trailing slashes with a permanent redirect.
   */
  def untrail(path: String) = Action { request =>
    val query = if (request.rawQueryString != "") "?" + request.rawQueryString else ""
    MovedPermanently("/" + path + query)
  }

  def localeData(lang: String): EssentialAction = statusCache.status(_ => "pages:localeData", OK, 3600) {
    Action { implicit request =>
      implicit val locale = play.api.i18n.Lang(lang)

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
          utils.i18n.languagePairList.map{ case (code, name) =>
            code + ": \"" + name + "\""
          }.mkString(",\n  "),
          utils.i18n.countryPairList.map{ case (code, name) =>
            code.toLowerCase + ": \"" + name + "\""
          }.mkString(",\n  ")
        )

      Ok(js).as(ContentTypes.JAVASCRIPT)
    }
  }
}
