package models.base

import defines.EntityType
import models.Entity
import models.UserProfileRepr
import models.GroupRepr
import models.UserProfileRepr

object Accessor {
  final val BELONGS_REL = "belongsTo"
    
	def apply(e: Entity): Accessor = e.isA match {
	  case EntityType.UserProfile => UserProfileRepr(e)
	  case EntityType.Group => GroupRepr(e)
	  case _ => sys.error("Unknow entity type for Accessor: " + e.isA.toString())
	} 	
}

trait Accessor extends AccessibleEntity with NamedEntity {
    val groups: List[Accessor] = e.relations(Accessor.BELONGS_REL).map(Accessor(_))

	// Search up the tree(?) if parent groups, looking
	// for one with the desired id.
	def getAccessor(groups: List[Accessor], id: String): Option[Accessor] = {
	  groups match {
	    case lst @ head :: rest => {
	      if (head.id == id) Some(head)	        
	      else getAccessor(head.groups, id) match {
	          case s @ Some(g) => s
	          case None => getAccessor(rest, id)
	      }
	    }
	    case Nil => None
	  }
	}
}