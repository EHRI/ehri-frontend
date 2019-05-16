package views.admin

import models.base.Model
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
    ("contentTypes.Link", controllers.links.routes.Links.search().url),
    ("s1", "-"),
    ("contentTypes.SystemEvent",      controllers.events.routes.SystemEvents.list().url),
    ("s2", "-"),
    ("cypherQuery.list",           controllers.cypher.routes.CypherQueries.listQueries().url)
  )

  val adminMenu = Seq(
    ("s3", "-"),
    ("search.index.update",         controllers.admin.routes.Indexing.updateIndex().url),
    ("admin.utils.findReplace",     controllers.admin.routes.Utils.findReplace().url),
    ("admin.utils.regenerateIds",   controllers.admin.routes.Utils.regenerateIds().url),
    ("admin.utils.renameItems",     controllers.admin.routes.Utils.renameItems().url),
    ("admin.utils.reparentItems",   controllers.admin.routes.Utils.reparentItems().url),
    ("admin.utils.movedItems",   controllers.admin.routes.Utils.addMovedItems().url),
    ("admin.utils.redirect",   controllers.admin.routes.Utils.redirect().url),
    ("admin.utils.batchDelete",   controllers.admin.routes.Utils.batchDelete().url)
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
      case Link => controllers.links.routes.Links.get(id)
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

  def linkTo(item: Model): Call = linkTo(item.isA, item.id)

  def linkToOpt(item: Model): Option[Call] =
    catching(classOf[IllegalArgumentException]).opt(linkTo(item))
}
