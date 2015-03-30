package views.export.ead

import play.api.test.PlaySpecification
import utils.ead.XmlFormatter

/**
 * Formatter should add a doctype and clean-up
 * whitespace. This is surprisingly difficult
 * to get right in Java 6 it seems.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
class XmlFormatterSpec extends PlaySpecification {

  private val unformatted =
    """
      |<ead>
      |
      | <eadheader>Test</eadheader>
      | </ead>
    """.stripMargin

  private val formatted =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<ead>
      |    <eadheader>Test</eadheader>
      |</ead>
      |""".stripMargin

  """XML formatted""" should {
    "correctly format XML" in {
      XmlFormatter.format(unformatted) must equalTo(formatted)
    }
  }
}
