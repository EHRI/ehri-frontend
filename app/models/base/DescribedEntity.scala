package models.base

object DescribedEntity {
  final val DESCRIBES_REL = "describes"  
}

trait DescribedEntity {

	this: AccessibleEntity =>
	  
	def descriptions: List[Description]
}