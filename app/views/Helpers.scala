package views

import java.util.Locale

import views.html.helper.FieldConstructor
import models.base.AccessibleEntity
import play.api.mvc.Call


// Pimp my 
package object Helpers {
  /*
   * Helper to provide Digg-style pagination, like:
   *    1, 2 ... 18, 19, 20, 21, 22 ... 55, 56
   * Logic borrowed from here:
   *   http://www.strangerstudios.com/sandbox/pagination/diggstyle_code.txt
   */
  def paginationRanges(page: Int, lastPage: Int, adjacents: Int = 3): List[Range] = {
    val window = adjacents * 2
    lastPage match {
      // Last page is the same as single page... no ranges
      case lp if lp <= 1 => Nil
      // Not enough pages to bother hiding any...
      case lp if lp < 7 + window =>  
        List((1 to lp))
      // Close to start, so only hide later pages
      case lp if lp > 5 + window && page < 1 + window =>
        List(1 until (4 + window), ((lp - 1) to lp))  
      // Around the middle, hide both start and end pages
      case lp if lp - window > page && page > window =>
        List((1 to 2), ((page - adjacents) to (page + adjacents)), ((lp - 1) to lp))
      // Close to end, hide beginning pages...
      case lp =>
        List((1 to 2), ((lp - (2 + window)) to lp))
    }
  }

  import defines.EntityType
  import controllers.routes
  import models.Entity

  def urlFor(a: AccessibleEntity) = urlForEntity(a.e)

  def urlForEntity(e: Entity): Call = e.isA match {
    case EntityType.SystemEvent => routes.SystemEvents.get(e.id)
    case EntityType.DocumentaryUnit => routes.DocumentaryUnits.get(e.id)
    case EntityType.Agent => routes.Agents.get(e.id)
    case EntityType.Group => routes.Groups.get(e.id)
    case EntityType.UserProfile => routes.UserProfiles.get(e.id)
    case EntityType.Annotation => routes.Annotations.get(e.id)
    case EntityType.Vocabulary => routes.Vocabularies.get(e.id)
    case EntityType.Concept => routes.Concepts.get(e.id)
    case EntityType.ContentType => Call("GET", "#")
    case i => sys.error("Cannot fetch URL for entity type: " + i)
  }
}
