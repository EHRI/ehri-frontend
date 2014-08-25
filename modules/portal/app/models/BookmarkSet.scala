package models

import play.api.i18n.Lang

/**
 * Class representing a user bookmark set, e.g. a
 * container for a bunch of bookmarks. On the server
 * this is represented as a virtual unit.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class BookmarkSet(name: String, lang: String, description: Option[String])
object BookmarkSet {
  val NAME = "name"
  val DESCRIPTION = "description"
  val LANG_CODE = "langCode"

  import play.api.data.Form
  import play.api.data.Forms._
  def bookmarkForm(implicit lang: Lang): Form[BookmarkSet] = Form(
    mapping(
      NAME -> nonEmptyText,
      LANG_CODE -> ignored(utils.i18n.lang2to3lookup.getOrElse(lang.language, utils.i18n.defaultLang)),
      DESCRIPTION -> optional(text)
    )(BookmarkSet.apply)(BookmarkSet.unapply)
  )
}

