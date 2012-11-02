package models

trait Accessor[T<:Accessor[T]] {
	val identifier: String
	val groups: List[T]
	
	def getAccessor(id: String): Option[T] = getAccessor(List(this.asInstanceOf[T]), id)
	
	// Search up the tree(?) if parent groups, looking
	// for one with the desired id.
	def getAccessor(groups: List[T], id: String): Option[T] = {
	  groups match {
	    case lst @ head :: rest => {
	      if (head.identifier == id)
	        Some(head)
	      else {
	        getAccessor(head.groups, id) match {
	          case s @ Some(g) => s
	          case None => getAccessor(rest, id)
	        }
	      }
	    }
	    case Nil => None
	  }
	}
}