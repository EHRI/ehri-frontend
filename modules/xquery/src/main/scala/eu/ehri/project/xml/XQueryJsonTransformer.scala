package eu.ehri.project.xml

trait XQueryJsonTransformer {
  def transform(input: String, mapping: String, params: Map[String, String]): String
}
