package models.base

object DescribedEntity {
  final val DESCRIBES_REL = "describes"

  final val DESCRIPTIONS = "descriptions"
}

trait DescribedEntity {

	this: AccessibleEntity =>
	  
	def descriptions: List[Description]
}