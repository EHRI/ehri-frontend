package views.admin

import models.EntityType
import models.base.Model
import play.api.mvc.Call

/**
 * Admin view helpers.
 */
object Helpers {

  import scala.util.control.Exception.catching

  val mainMenu = Seq(
    ("contentTypes.Country",          controllers.countries.routes.Countries.search().url),
    ("contentTypes.Repository",       controllers.institutions.routes.Repositories.search().url),
    ("contentTypes.DocumentaryUnit",  controllers.units.routes.DocumentaryUnits.search().url),
    ("contentTypes.HistoricalAgent",  controllers.authorities.routes.HistoricalAgents.search().url),
    ("contentTypes.CvocConcept",      controllers.keywords.routes.Concepts.search().url),
  )

  val moreMenu = Seq(
    ("contentTypes.CvocVocabulary",   controllers.vocabularies.routes.Vocabularies.list().url),
    ("contentTypes.AuthoritativeSet", controllers.sets.routes.AuthoritativeSets.list().url),
    ("contentTypes.UserProfile",      controllers.users.routes.UserProfiles.search().url),
    ("contentTypes.Group",            controllers.groups.routes.Groups.search().url),
    ("contentTypes.Link",             controllers.links.routes.Links.search().url),
    ("s1", "-"),
    ("contentTypes.SystemEvent",      controllers.events.routes.SystemEvents.list().url),
    ("s2", "-"),
    ("cypherQuery.list",              controllers.cypher.routes.CypherQueries.listQueries().url),
  )

  val adminMenu = Seq(
    ("s3", "-"),
    ("search.index.update",         controllers.admin.routes.Indexing.updateIndex().url),
    ("admin.utils.findReplace",     controllers.tools.routes.Tools.findReplace().url),
    ("admin.utils.regenerateIds",   controllers.tools.routes.Tools.regenerateIds().url),
    ("admin.utils.renameItems",     controllers.tools.routes.Tools.renameItems().url),
    ("admin.utils.reparentItems",   controllers.tools.routes.Tools.reparentItems().url),
    ("admin.utils.movedItems",      controllers.tools.routes.Tools.addMovedItems().url),
    ("admin.utils.redirect",        controllers.tools.routes.Tools.redirect().url),
    ("admin.utils.batchDelete",     controllers.tools.routes.Tools.batchDelete().url),
    ("admin.utils.validate",        controllers.tools.routes.Tools.validateEad().url),
  )

  def linkTo(isA: EntityType.Value, id: String): Call = {
    import models.EntityType._
    isA match {
      case SystemEvent => controllers.events.routes.SystemEvents.get(id)
      case DocumentaryUnit => controllers.units.routes.DocumentaryUnits.get(id)
      case HistoricalAgent => controllers.authorities.routes.HistoricalAgents.get(id)
      case Repository => controllers.institutions.routes.Repositories.get(id)
      case Group => controllers.groups.routes.Groups.get(id)
      case UserProfile => controllers.users.routes.UserProfiles.get(id)
      case Annotation => controllers.annotation.routes.Annotations.get(id)
      case Link => controllers.links.routes.Links.get(id)
      case Vocabulary => controllers.vocabularies.routes.Vocabularies.get(id)
      case AuthoritativeSet => controllers.sets.routes.AuthoritativeSets.get(id)
      case Concept => controllers.keywords.routes.Concepts.get(id)
      case Country => controllers.countries.routes.Countries.get(id)
      case VirtualUnit => controllers.virtual.routes.VirtualUnits.get(id)
      case _ => throw new IllegalArgumentException(s"Link to unexpected item: $id $isA")
    }
  }

  def linkToOpt(isA: EntityType.Value, id: String): Option[Call] =
    catching(classOf[IllegalArgumentException]).opt(linkTo(isA, id))

  def linkTo(item: Model): Call = linkTo(item.isA, item.id)

  def linkToOpt(item: Model): Option[Call] =
    catching(classOf[IllegalArgumentException]).opt(linkTo(item))
}
