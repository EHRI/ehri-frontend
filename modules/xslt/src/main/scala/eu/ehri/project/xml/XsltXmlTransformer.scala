package eu.ehri.project.xml

import play.api.libs.json.JsObject

trait XsltXmlTransformer {
  def transform(input: String, mapping: String, params: JsObject): String

  @throws[XsltConfigError]
  def validationMapping(mapping: String): Unit = {
    val lines = mapping.split('\n').toSeq
    if (lines.size < 1) {
      throw new XsltConfigError("XQuery mapping TSV must include a header row")
    }
    if (!lines.forall(_.split('\t').length >= 4)) {
      throw XsltConfigError("XQuery mapping TSV must contain 4 columns in all rows")
    }
  }
}
