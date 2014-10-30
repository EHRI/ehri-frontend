package utils

import play.api.data.{Forms, Form}
import backend.rest.Constants

/**
 * Enum defining modes of visibility for contributed
 * content.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object ContributionVisibility extends Enumeration {

  val PARAM = "visibility"

  /**
   * Only the author.
   */
  val Me = Value("me")

  /**
   * The author and those who belong to groups
   * the author belongs to.
   */
  val Groups = Value("groups")

  /**
   * A specific mix of users and/or groups.
   */
  val Custom = Value("custom")

  /**
   * Binding/unbinding from a form.
   */
  val form = Form(Forms.single(PARAM -> Forms.default(utils.forms.enum(this), ContributionVisibility.Me)))

  /**
   * Binding/unbinding from JSON.
   */
  val format = defines.EnumUtils.enumFormat(this)
}
