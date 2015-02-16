package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB
import language.postfixOps
import utils.db.StorableEnum
import play.core.parsers.FormUrlEncodedParser
import models.sql.withIntegrityCheck

import scala.util.Try

/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class GuidePage(
  id: Option[Long] = None,
  layout: GuidePage.Layout.Value,
  name: String,
  path: String,
  position: GuidePage.MenuPosition.Value,
  content: String,
  parent: Option[Long] = None,
  description: Option[String] = None,
  params: Option[String] = None
) {
  /*
  * Edit a page
  */
  def update(): Try[Unit] = withIntegrityCheck { implicit connection =>
      SQL"""
      UPDATE
        research_guide_page
      SET
        layout = $layout,
        name = $name,
        path = $path,
        position = $position,
        content = $content,
        research_guide_id = $parent,
        params = $params,
        description = $description
      WHERE id = $id
      LIMIT 1
    """.executeUpdate()
  }

  /*
  * Delete a page
  */
  def delete(): Unit = DB.withConnection { implicit connection =>
      SQL"""DELETE FROM research_guide_page WHERE id = $id LIMIT 1""".executeUpdate()
  }

  def getParams: Map[String,Seq[String]] = {
    params match {
      case Some(str) => FormUrlEncodedParser.parse(str)
      case _ => Map.empty
    }
  }
}

object GuidePage {
  import defines.EnumUtils.enumMapping

  val PREFIX = "guidePage"
  val OBJECTID = "id"
  val PATH = "path"
  val NAME = "name"
  val LAYOUT = "layout"
  val POSITION = "position"
  val CONTENT = "content"
  val PARENT = "parent"
  val PARAMS = "params"
  val DESCRIPTION = "description"

  object Layout extends Enumeration with StorableEnum {
    val Markdown = Value("md")
    val Html = Value("html")
    val Organisation = Value("organisation")
    val Person = Value("person")
    val Map = Value("map")
    val Timeline = Value("timeline")
  }

  object MenuPosition extends Enumeration with StorableEnum {
    val Top = Value("top")
    val Side = Value("side")
    val Nowhere = Value("nowhere")
  }

  implicit val form = Form(
    mapping(
      OBJECTID -> ignored(Option.empty[Long]),
      LAYOUT -> enumMapping(Layout),
      NAME -> nonEmptyText,
      PATH -> nonEmptyText,
      POSITION -> enumMapping(MenuPosition),
      CONTENT -> nonEmptyText,
      PARENT -> optional(longNumber),
      DESCRIPTION -> optional(nonEmptyText),
      PARAMS -> optional(nonEmptyText)
    )(GuidePage.apply)(GuidePage.unapply)
  )

  def blueprint(guideId: Option[Long]): GuidePage = {
    GuidePage(None, Layout.Markdown, "", "", MenuPosition.Side, "", guideId)
  }

  val rowExtractor = {
    get[Option[Long]](OBJECTID) ~
    get[Layout.Value](LAYOUT) ~
    get[String](NAME) ~
    get[String](PATH) ~
    get[MenuPosition.Value](POSITION) ~
    get[String](CONTENT) ~
    get[Option[Long]]("research_guide_id") ~ 
    get[Option[String]](DESCRIPTION) ~ 
    get[Option[String]](PARAMS) map {
      case oid ~ layout ~ name ~ path ~ menu ~ query ~ pid ~ description ~ params  =>
        GuidePage(oid, layout, name, path, menu, query, pid, description, params)
    }
  }

  /*
  * Create a new page
  */
  def create(layout: Layout.Value, name: String, path: String, menu: MenuPosition.Value = MenuPosition.Side,
             cypher: String, parent: Option[Long] = None, description: Option[String] = None, params: Option[String] = None): Try[Option[GuidePage]] =
      withIntegrityCheck { implicit connection =>
    val id: Option[Long] = SQL"""
      INSERT INTO research_guide_page
        (layout, name, path, position, content, research_guide_id, description, params)
      VALUES
        ($layout, $name, $path, $menu, $cypher, $parent, $description, $params)
    """.executeInsert()
    id.flatMap { l =>
      SQL"SELECT * FROM research_guide_page WHERE id = $l".as(rowExtractor.singleOpt)
    }
  }

  /*
  * List or find data
  */
  def find(path: String): List[GuidePage] = DB.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide_page WHERE path = $path""".as(rowExtractor *)
  }

  def findAll(): List[GuidePage] = DB.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide_page""".as(rowExtractor *)
  }

  def faceted: GuidePage = {
    GuidePage(
      None,
      Layout.Markdown,
      "guides.faceted",
      "browse",
      MenuPosition.Top,
      "",
      None,
      None
    )
  }

  def document(title: Option[String] = None): GuidePage = {
    GuidePage(None, Layout.Markdown, title.getOrElse("Documents"), "documents", MenuPosition.Top, "", None, None)
  }

  def repository(title: Option[String] = None): GuidePage = {
    GuidePage(None, Layout.Markdown, title.getOrElse("Repository"), "repository", MenuPosition.Top, "", None, None)
  }
}


