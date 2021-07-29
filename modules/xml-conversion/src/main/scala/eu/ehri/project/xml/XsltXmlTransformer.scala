package eu.ehri.project.xml

import play.api.libs.json.JsObject

trait XsltXmlTransformer {
  def transform(input: String, mapping: String, params: JsObject): String

  @throws[InvalidMappingError]
  def validationMapping(mapping: String): Unit = {
    val lines = mapping.split('\n').toSeq
    if (lines.size < 1) {
      throw new InvalidMappingError("XQuery mapping TSV must include a header row")
    }
    if (!lines.forall(_.split('\t').length >= 4)) {
      throw InvalidMappingError("XQuery mapping TSV must contain 4 columns in all rows")
    }
  }
}
