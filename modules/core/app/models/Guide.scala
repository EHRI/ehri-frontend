package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB
import language.postfixOps

/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class Guide(
  objectId: Option[Long] = None,
  name: String,
  path: String,
  picture: Option[String] = None,
  description: Option[String] = None,
  active: Boolean,
  default: Long = 0
) {
  def update(): Unit = DB.withConnection {
    implicit connection =>
      SQL(
        """
      UPDATE
        research_guide
      SET 
        name = {n},
        path = {p},
        picture = {pi},
        description = {de},
        active = {active},
        `default` = {default}
      WHERE 
        id = {i}
      LIMIT 1
        """
      ).on('n -> name, 'p -> path, 'pi -> picture, 'de -> description, 'i -> objectId, 'active -> active, 'default -> default).executeUpdate()
  }

  def delete(): Unit = DB.withConnection {
    implicit connection =>
      SQL("""DELETE FROM research_guide WHERE id = {i} LIMIT 1""")
        .on('i -> objectId).executeUpdate()
  }
  
  def getPages: List[GuidePage] = DB.withConnection { implicit connection =>
    objectId.map { id =>
        SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id}""")
          .on('id -> id).as(GuidePage.rowExtractor *)
    }.toList.flatten
  }

  def getPage(page: String): Option[GuidePage] = DB.withConnection { implicit connection =>
    objectId.flatMap { id =>
      SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id} AND path = {page}""")
        .on('id -> id, 'page -> page).as(GuidePage.rowExtractor.singleOpt)
    }
  }

  def getDefaultPage: Option[GuidePage] = DB.withConnection { implicit connection =>
    objectId.flatMap { id =>
      SQL( """SELECT * FROM research_guide_page WHERE research_guide_id = {id} AND id = {gid}""")
        .on('id -> id, 'gid -> default).as(GuidePage.rowExtractor.singleOpt)
    }
  }
}

object Guide {
  val PREFIX = "guide"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT = "default"

  implicit val form = Form(
    mapping(
      "objectId" -> optional(longNumber),
      "name" -> text,
      "path" -> text,
      "picture" -> optional(nonEmptyText),
      "description" -> optional(text),
      "active" -> boolean,
      "default" -> longNumber
    )(Guide.apply)(Guide.unapply)
  )

  val rowExtractor = {
    get[Option[Long]]("id") ~
      get[String]("name") ~
      get[String]("path") ~
      get[Option[String]]("picture") ~
      get[Option[String]]("description") ~
      get[Boolean]("active") ~
      get[Long]("default") map {
      case gid ~ name ~ path ~ pic ~ desc ~ active ~ deft =>
        Guide(gid, name, path, pic, desc, active, deft)
    }
  }

  def blueprint(): Guide = Guide(Some(0), "", "", Some(""), Some(""), active = false, 0)

  /*
  *   Create function
  */
  def create(name: String, path: String, picture: Option[String] = None, description: Option[String] = None, active: Boolean): Option[Guide] = DB.withConnection { implicit connection =>
    val id: Option[Long] = SQL("""
      INSERT INTO research_guide
      (name, path, picture, description, active) VALUES ({n}, {p}, {pi}, {de}, {a})""")
      .on('n -> name, 'p -> path, 'pi -> picture, 'de -> description, 'a -> active).executeInsert()
    id.flatMap(findById)
  }

  /*
  *   Listing functions
  */
  def findAll(activeOnly: Boolean = false): List[Guide] = DB.withConnection {
    implicit connection =>
      SQL(
        if (activeOnly) """SELECT * FROM research_guide WHERE active = 1"""
        else """SELECT * FROM research_guide"""
      ).as(rowExtractor *)
  }

  def find(param: String, activeOnly: Boolean = false): Option[Guide] = DB.withConnection {
    implicit connection => (
      if (activeOnly) SQL("""SELECT * FROM research_guide WHERE path = {param} AND active = 1 LIMIT 1""")
      else SQL("""SELECT * FROM research_guide WHERE path = {param} LIMIT 1""")
    ).on('param -> param).as(rowExtractor.singleOpt)
  }

  def findById(id: Long): Option[Guide] = DB.withConnection {
    implicit connection =>
      SQL("""SELECT * FROM research_guide WHERE id = {id}""")
        .on('id -> id).as(rowExtractor.singleOpt)
  }
}


