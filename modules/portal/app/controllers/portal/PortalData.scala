package controllers.portal

import play.api.http.MimeTypes
import play.api.Routes
import play.api.mvc.{Controller,Action}
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
          controllers.portal.routes.javascript.Profile.watchItemPost,
          controllers.portal.routes.javascript.Profile.unwatchItemPost,
          controllers.portal.routes.javascript.Profile.updatePrefs,
          controllers.portal.routes.javascript.Profile.profile,
          controllers.portal.routes.javascript.Profile.updateProfile,
          controllers.portal.routes.javascript.Profile.updateProfilePost,
          controllers.portal.routes.javascript.Social.personalisedActivity,
          controllers.portal.routes.javascript.Social.followUserPost,
          controllers.portal.routes.javascript.Social.unfollowUserPost,
          controllers.portal.routes.javascript.Social.followersForUser,
          controllers.portal.routes.javascript.Social.blockUserPost,
          controllers.portal.routes.javascript.Social.unblockUserPost,
          controllers.portal.routes.javascript.Social.sendMessage,
          controllers.portal.routes.javascript.Annotations.annotate,
          controllers.portal.routes.javascript.Annotations.annotatePost,
          controllers.portal.routes.javascript.Annotations.annotateField,
          controllers.portal.routes.javascript.Annotations.annotateFieldPost,
          controllers.portal.routes.javascript.Annotations.editAnnotation,
          controllers.portal.routes.javascript.Annotations.editAnnotationPost,
          controllers.portal.routes.javascript.Annotations.deleteAnnotation,
          controllers.portal.routes.javascript.Annotations.deleteAnnotationPost,
          controllers.portal.routes.javascript.Annotations.setAnnotationVisibilityPost,
          controllers.core.routes.javascript.SearchFilter.filter,
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


  
}
