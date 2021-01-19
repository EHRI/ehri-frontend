package models

import play.api.i18n.Messages

/**
 * Class representing a user bookmark set, e.g. a
 * container for a bunch of bookmarks. On the server
 * this is represented as a virtual unit.
 */
case class BookmarkSet(name: String, lang: String, description: Option[String])
object BookmarkSet {
  val NAME = "name"
  val DESCRIPTION = "description"
  val LANG_CODE = "langCode"

  import play.api.data.Form
  import play.api.data.Forms._
  def bookmarkForm(implicit messages: Messages): Form[BookmarkSet] = Form(
    mapping(
      NAME -> nonEmptyText,
      LANG_CODE -> ignored(i18n.lang2to3lookup.getOrElse(messages.lang.language, i18n.defaultLang)),
      DESCRIPTION -> optional(text)
    )(BookmarkSet.apply)(BookmarkSet.unapply)
  )
}

