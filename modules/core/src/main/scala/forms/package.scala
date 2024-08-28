import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, seq, single}

import java.net.{MalformedURLException, URL}

/**
  * Form-related utilities
  */
package object forms {


  /**
    * Check if a string is a valid URL.
    */
  val isValidUrl: String => Boolean = s => try {
    new URL(s)
    true
  } catch {
    case _: MalformedURLException => false
  }

  /**
    * Form for a set of user or group identifiers that can
    * access a given resource.
    */
  val visibilityForm: Form[Seq[String]] = Form(single(
    services.data.Constants.ACCESSOR_PARAM -> seq(nonEmptyText)
  ))
}
