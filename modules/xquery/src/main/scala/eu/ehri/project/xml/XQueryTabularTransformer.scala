package eu.ehri.project.xml

trait XQueryTabularTransformer {
  def transform(input: String, config: String, params: Map[String, String]): String
}
