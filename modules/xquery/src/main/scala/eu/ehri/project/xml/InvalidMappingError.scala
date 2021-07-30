package eu.ehri.project.xml

case class InvalidMappingError(
  error: String
) extends Exception(error)


