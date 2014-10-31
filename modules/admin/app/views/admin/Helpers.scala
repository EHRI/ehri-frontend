package views.admin

import models.base.AnyModel
import play.api.mvc.Call

/**
 * Admin view helpers.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Helpers {

  def linkTo(item: AnyModel): Call = {
    import defines.EntityType._
    item.isA match {
      case SystemEvent => controllers.events.routes.SystemEvents.get(item.id)
      case DocumentaryUnit => controllers.units.routes.DocumentaryUnits.get(item.id)
      case HistoricalAgent => controllers.authorities.routes.HistoricalAgents.get(item.id)
      case Repository => controllers.institutions.routes.Repositories.get(item.id)
      case Group => controllers.groups.routes.Groups.get(item.id)
      case UserProfile => controllers.users.routes.UserProfiles.get(item.id)
      case Annotation => controllers.annotation.routes.Annotations.get(item.id)
      case Link => controllers.linking.routes.Links.get(item.id)
      case Vocabulary => controllers.vocabularies.routes.Vocabularies.get(item.id)
      case AuthoritativeSet => controllers.sets.routes.AuthoritativeSets.get(item.id)
      case Concept => controllers.keywords.routes.Concepts.get(item.id)
      case Country => controllers.countries.routes.Countries.get(item.id)
      case VirtualUnit => controllers.virtual.routes.VirtualUnits.get(item.id)
      case _ => {
        play.api.Logger.logger.error(s"Link to unexpected item: ${item.toStringLang} ${item.isA}")
        Call("GET", "#")
      }
    }
  }
  
}
