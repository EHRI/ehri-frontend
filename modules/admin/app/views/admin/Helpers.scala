package views.admin

import models.{EntityType, Model}
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
    ("ingest.datasets",             controllers.datasets.routes.ImportDatasets.dashboard().url),
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

  def linkToOpt(isA: EntityType.Value, id: String): Option[Call] = {
    import models.EntityType._
    isA match {
      case SystemEvent => Some(controllers.events.routes.SystemEvents.get(id))
      case DocumentaryUnit => Some(controllers.units.routes.DocumentaryUnits.get(id))
      case HistoricalAgent => Some(controllers.authorities.routes.HistoricalAgents.get(id))
      case Repository => Some(controllers.institutions.routes.Repositories.get(id))
      case Group => Some(controllers.groups.routes.Groups.get(id))
      case UserProfile => Some(controllers.users.routes.UserProfiles.get(id))
      case Annotation => Some(controllers.annotation.routes.Annotations.get(id))
      case Link => Some(controllers.links.routes.Links.get(id))
      case Vocabulary => Some(controllers.vocabularies.routes.Vocabularies.get(id))
      case AuthoritativeSet => Some(controllers.sets.routes.AuthoritativeSets.get(id))
      case Concept => Some(controllers.keywords.routes.Concepts.get(id))
      case Country => Some(controllers.countries.routes.Countries.get(id))
      case VirtualUnit => Some(controllers.virtual.routes.VirtualUnits.get(id))
      case _ => None
    }
  }

  def linkTo(isA: EntityType.Value, id: String): Call =
    linkToOpt(isA, id).getOrElse(throw new IllegalArgumentException(s"Link to unexpected item: $id $isA"))

  def linkTo(item: Model): Call = linkTo(item.isA, item.id)

  def linkToOpt(item: Model): Option[Call] = linkToOpt(item.isA, item.id)

  def publicLinkTo(isA: EntityType.Value, id: String): Option[Call] =
    linkToOpt(isA, id).map(call => Call(call.method, call.url.replaceAll("^/admin", ""), call.fragment))
}
