package controllers.portal

import play.api.http.{ContentTypes, MimeTypes}
import play.api.Routes
import play.api.mvc.{Cookie, Controller, Action}
import play.api.cache.Cached
import play.api.Play.current



/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object PortalData extends Controller {

  def jsRoutes = Cached.status(_ => "pages:portalJsRoutes", OK, 3600) {
    Action { implicit request =>
      Ok(
        Routes.javascriptRouter("jsRoutes")(
          controllers.portal.routes.javascript.Bookmarks.moveBookmarksPost,
          controllers.portal.routes.javascript.Bookmarks.contents,
          controllers.portal.routes.javascript.Bookmarks.bookmarkPost,
          controllers.portal.routes.javascript.Bookmarks.bookmarkInNewSetPost,
          controllers.portal.users.routes.javascript.UserProfiles.watchItemPost,
          controllers.portal.users.routes.javascript.UserProfiles.unwatchItemPost,
          controllers.portal.users.routes.javascript.UserProfiles.updatePrefs,
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
          controllers.portal.routes.javascript.Portal.personalisedActivity,
          controllers.portal.routes.javascript.Portal.filterItems,
          controllers.portal.routes.javascript.Portal.browseItem
        )
      ).as(MimeTypes.JAVASCRIPT)
    }
  }

  /**
   * Render entity types into the view to serve as JS constants.
   * @return
   */
  def globalData = Cached.status(_ => "pages:globalData", OK, 3600) {
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
   * Provide functionality for changing the current locale.
   *
   * This is borrowed from:
   * https://github.com/julienrf/chooze/blob/master/app/controllers/CookieLang.scala
   */
  private val LANG = "lang"

  def changeLocale(lang: String) = Action { implicit request =>
    val referrer = request.headers.get(REFERER).getOrElse("/")
    Redirect(referrer).withCookies(Cookie(LANG, lang))
  }

  /**
   * Handle trailing slashes with a permanent redirect.
   */
  def untrail(path: String) = Action { request =>
    val query = if (request.rawQueryString != "") "?" + request.rawQueryString else ""
    MovedPermanently("/" + path + query)
  }

  def localeData(lang: String) = Cached.status(_ => "pages:localeData", OK, 3600) {
    Action { request =>
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
