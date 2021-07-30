package eu.ehri.project.xml

trait XQueryXmlTransformer {
  def transform(input: String, mapping: String, params: Map[String, String]): String
}
