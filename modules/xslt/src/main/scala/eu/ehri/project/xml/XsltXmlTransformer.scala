package eu.ehri.project.xml

import play.api.libs.json.JsObject

trait XsltXmlTransformer {
  def transform(input: String, mapping: String, params: JsObject): String

  /**
    * Strips BOM from the beginning of a string if present
    */
  protected def stripUTF8BOM(input: String): String = {
    if (input != null && input.nonEmpty) {
      // UTF-8 BOM is represented as \uFEFF in Java strings
      if (input.charAt(0) == '\uFEFF') return input.substring(1)
    }
    input
  }

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
