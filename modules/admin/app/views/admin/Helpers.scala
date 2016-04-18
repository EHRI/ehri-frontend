package views.admin

import models.base.AnyModel
import play.api.mvc.Call

/**
 * Admin view helpers.
 */
object Helpers {

  import scala.util.control.Exception.catching

  val mainMenu = Seq(
    ("pages.search",                  controllers.admin.routes.AdminSearch.search().url),
    ("contentTypes.DocumentaryUnit",  controllers.units.routes.DocumentaryUnits.search().url),
    ("contentTypes.HistoricalAgent",  controllers.authorities.routes.HistoricalAgents.search().url),
    ("contentTypes.Repository",       controllers.institutions.routes.Repositories.search().url),
    ("contentTypes.CvocConcept",      controllers.keywords.routes.Concepts.search().url)
  )

  val moreMenu = Seq(
    ("contentTypes.UserProfile",      controllers.users.routes.UserProfiles.search().url),
    ("contentTypes.Group",            controllers.groups.routes.Groups.search().url),
    ("contentTypes.Country",          controllers.countries.routes.Countries.search().url),
    ("contentTypes.CvocVocabulary",   controllers.vocabularies.routes.Vocabularies.list().url),
    ("contentTypes.AuthoritativeSet", controllers.sets.routes.AuthoritativeSets.list().url),
    ("s1", "-"),
    ("contentTypes.SystemEvent",      controllers.events.routes.SystemEvents.list().url),
    ("s2", "-"),
    ("cypherQuery.list",           controllers.cypher.routes.CypherQueries.listQueries().url),
    ("s3", "-"),
    ("search.index.update",            controllers.admin.routes.AdminSearch.updateIndex().url)
  )

  def linkTo(isA: defines.EntityType.Value, id: String): Call = {
    import defines.EntityType._
    isA match {
      case SystemEvent => controllers.events.routes.SystemEvents.get(id)
      case DocumentaryUnit => controllers.units.routes.DocumentaryUnits.get(id)
      case HistoricalAgent => controllers.authorities.routes.HistoricalAgents.get(id)
      case Repository => controllers.institutions.routes.Repositories.get(id)
      case Group => controllers.groups.routes.Groups.get(id)
      case UserProfile => controllers.users.routes.UserProfiles.get(id)
      case Annotation => controllers.annotation.routes.Annotations.get(id)
      case Link => controllers.linking.routes.Links.get(id)
      case Vocabulary => controllers.vocabularies.routes.Vocabularies.get(id)
      case AuthoritativeSet => controllers.sets.routes.AuthoritativeSets.get(id)
      case Concept => controllers.keywords.routes.Concepts.get(id)
      case Country => controllers.countries.routes.Countries.get(id)
      case VirtualUnit => controllers.virtual.routes.VirtualUnits.get(id)
      case _ => throw new IllegalArgumentException(s"Link to unexpected item: $id $isA")
    }
  }

  def linkToOpt(isA: defines.EntityType.Value, id: String): Option[Call] =
    catching(classOf[IllegalArgumentException]).opt(linkTo(isA, id))

  def linkTo(item: AnyModel): Call = linkTo(item.isA, item.id)

  def linkToOpt(item: AnyModel): Option[Call] =
    catching(classOf[IllegalArgumentException]).opt(linkTo(item))
}
