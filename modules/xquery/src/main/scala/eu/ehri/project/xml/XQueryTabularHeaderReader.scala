package eu.ehri.project.xml

trait XQueryTabularHeaderReader {
  /**
    * Read just the header row of a CSV/TSV document.
    *
    * @return a JSON array of the header field names, as a string (this module has
    *         no JSON library dependency of its own - callers should parse it)
    */
  def readHeader(input: String, params: Map[String, String] = Map.empty): String
}
