package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB
import language.postfixOps
import defines.{StorableEnum, BindableEnum}


/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class GuidePage(
  objectId: Option[Long] = None,
  layout: GuidePage.Layout.Value,
  name: String,
  path: String,
  position: GuidePage.MenuPosition.Value,
  content: String,
  parent: Long
) {
  /*
  * Edit a page
  */
  def update(): Unit = DB.withConnection {
    implicit connection =>
      SQL(
        """
      UPDATE
        research_guide_page
      SET
        layout = {l},
        name = {n},
        path = {p},
        menu = {m},
        cypher = {c},
        research_guide_id = {parent}
      WHERE id = {id}
      LIMIT 1
        """
      ).on('l -> layout, 'n -> name, 'p -> path, 'm -> position, 'c -> content, 'parent -> parent, 'id -> objectId).executeUpdate()
  }

  /*
  * Delete a page
  */
  def delete(): Unit = DB.withConnection {
    implicit connection =>
      SQL("""DELETE FROM research_guide_page WHERE id = {i} LIMIT 1""")
        .on('i -> objectId).executeUpdate()
  }
}

object GuidePage {

  val PREFIX = "guidePage"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val LAYOUT = "layout"
  val POSITION = "position"
  val CONTENT = "content"
  val PARENT = "parent"

  object Layout extends Enumeration with StorableEnum {
    val Markdown = Value("md")
    val Organisation = Value("organisation")
    val Person = Value("person")
    val Map = Value("map")
    val Keyword = Value("keyword")
  }

  object MenuPosition extends Enumeration with StorableEnum {
    val Top = Value("top")
    val Side = Value("side")
  }

  implicit val form = Form(
    mapping(
      "objectId" -> optional(longNumber),
      "layout" -> models.forms.enum(Layout),
      "name" -> nonEmptyText,
      "path" -> nonEmptyText,
      "position" -> models.forms.enum(MenuPosition),
      "content" -> nonEmptyText,
      "parent" -> longNumber
    )(GuidePage.apply)(GuidePage.unapply)
  )

  def blueprint(guideId: Option[Long]): GuidePage = {
    GuidePage(None, Layout.Markdown, "", "", MenuPosition.Side, "", guideId.getOrElse(0))
  }

  val rowExtractor = {
    get[Option[Long]]("id") ~
    get[Layout.Value]("layout") ~
    get[String]("name") ~
    get[String]("path") ~
    get[MenuPosition.Value]("menu") ~
    get[String]("cypher") ~
    get[Long]("research_guide_id") map {
      case pid ~ layout ~ name ~ path ~ menu ~ query ~ id  =>
        GuidePage(pid, layout, name, path, menu, query, id)
    }
  }

  /*
  * Create a new page
  */
  def create(layout: Layout.Value, name: String, path: String, menu: MenuPosition.Value = MenuPosition.Side,
             cypher: String, parent: Option[Long]): Option[Long] = DB.withConnection {
    implicit connection =>
      SQL(
        """INSERT INTO research_guide_page (layout, name, path, menu, cypher, research_guide_id)
           VALUES ({l}, {n}, {p}, {m}, {c}, {parent})""")
        .on('l -> layout, 'n -> name, 'p -> path, 'm -> menu, 'c -> cypher, 'parent -> parent).executeInsert()
  }

  /*
  * List or find data
  */
  def find(path: String): List[GuidePage] = DB.withConnection { implicit connection =>
    SQL( """SELECT * FROM research_guide_page WHERE path = {path}""")
      .on('path -> path).as(rowExtractor *)
  }

  def findAll(): List[GuidePage] = DB.withConnection { implicit connection =>
    SQL( """SELECT * FROM research_guide_page""")
      .as(rowExtractor *)
  }

  def faceted: GuidePage = {
    GuidePage(
      None,
      Layout.Markdown,
      "portal.guides.faceted",
      "browse",
      MenuPosition.Top,
      "",
      0
    )
  }
}


