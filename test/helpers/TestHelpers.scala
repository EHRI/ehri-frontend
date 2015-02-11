package helpers

import play.api.http.{ContentTypes, HeaderNames}
import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait TestHelpers {

  self: PlaySpecification =>


  val jsonPostHeaders: Map[String, String] = Map(
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )

  val formPostHeaders: Map[String,String] = Map(
    HeaderNames.CONTENT_TYPE -> ContentTypes.FORM
  )

}
