package controllers.portal

import play.api.http.MimeTypes
import play.api.Routes
import play.api.mvc.{Controller,Action}


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object PortalData extends Controller {


  def jsRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        controllers.portal.routes.javascript.Profile.updatePrefs,
        controllers.portal.routes.javascript.Profile.profile,
        controllers.portal.routes.javascript.Profile.updateProfile,
        controllers.portal.routes.javascript.Profile.updateProfilePost,
        controllers.portal.routes.javascript.Social.personalisedActivity,
        controllers.portal.routes.javascript.Social.followUserPost,
        controllers.portal.routes.javascript.Social.unfollowUserPost,
        controllers.portal.routes.javascript.Social.watchItemPost,
        controllers.portal.routes.javascript.Social.unwatchItemPost,
        controllers.portal.routes.javascript.Social.followersForUser,
        controllers.portal.routes.javascript.Portal.annotate,
        controllers.portal.routes.javascript.Portal.annotatePost,
        controllers.portal.routes.javascript.Portal.annotateField,
        controllers.portal.routes.javascript.Portal.annotateFieldPost,
        controllers.portal.routes.javascript.Portal.editAnnotation,
        controllers.portal.routes.javascript.Portal.editAnnotationPost,
        controllers.portal.routes.javascript.Portal.deleteAnnotation,
        controllers.portal.routes.javascript.Portal.deleteAnnotationPost,
        controllers.portal.routes.javascript.Portal.setAnnotationVisibilityPost,
        controllers.core.routes.javascript.SearchFilter.filter,
        controllers.portal.routes.javascript.Portal.browseItem
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  /**
   * Render entity types into the view to serve as JS constants.
   * @return
   */
  def globalData = Action { implicit request =>
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
