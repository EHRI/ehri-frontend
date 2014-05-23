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
                  objectId: Option[Int],
                  name: String,
                  path: String,
                  picture: Option[String],
                  description: Option[String],
                  active: Int = 0,
                  default: Int = 0
                  ) {
  def update(): Int = DB.withConnection {
    implicit connection =>
      SQL(
        """
      UPDATE
        research_guide
      SET 
        name_research_guide = {n},
        path_research_guide = {p},
        picture_research_guide = {pi},
        description_research_guide = {de},
        active_research_guide = {active},
        default_research_guide = {default}
      WHERE 
        id_research_guide = {i}
      LIMIT 1
        """
      ).on('n -> name, 'p -> path, 'pi -> picture, 'de -> description, 'i -> objectId, 'active -> active, 'default -> default).executeUpdate()
  }

  def delete(): Int = DB.withConnection {
    implicit connection =>
      SQL(
        """
      DELETE FROM
        research_guide
      WHERE 
        id_research_guide = {i}
      LIMIT 1
        """
      ).on('i -> objectId).executeUpdate()            
  }
  
  def getPages: List[GuidesPage] = DB.withConnection { implicit connection =>
    objectId.map { id =>
        SQL( """
        SELECT
          *
        FROM
          research_guide_page
        WHERE
          id_research_guide = {id}
             """).on('id -> id).as(GuidesPage.rowExtractor *)
    }.toList.flatten
  }

  def getPage(page: String): Option[GuidesPage] = DB.withConnection { implicit connection =>
    objectId.flatMap { id =>
      SQL( """
        SELECT
          *
        FROM
          research_guide_page
        WHERE
          id_research_guide = {id} AND
          path_research_guide_page = {page}
           """
      ).on('id -> id, 'page -> page).as(GuidesPage.rowExtractor.singleOpt)
    }
  }

  def getDefaultPage: Option[GuidesPage] = DB.withConnection { implicit connection =>
    objectId.flatMap { id =>
      SQL( """
        SELECT
          *
        FROM
          research_guide_page
        WHERE
          id_research_guide = {id} AND
          id_research_guide_page = {gid}
           """
      ).on('id -> id, 'gid -> default).as(GuidesPage.rowExtractor.singleOpt)
    }
  }
}

object Guide {
  val PREFIX = "guides"
  val OBJECTID = "objectId"
  val PATH = "path"
  val NAME = "name"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT = "default"

  implicit val form = Form(
    mapping(
      "objectId" -> optional(number),
      "name" -> text,
      "path" -> text,
      "picture" -> optional(nonEmptyText),
      "description" -> optional(text),
      "active" -> number,
      "default" -> number
    )(Guide.apply _)(Guide.unapply _)
  )

  val rowExtractor = {
    get[Option[Int]]("id_research_guide") ~
      get[String]("name_research_guide") ~
      get[String]("path_research_guide") ~
      get[Option[String]]("picture_research_guide") ~
      get[Option[String]]("description_research_guide") ~
      get[Int]("active_research_guide") ~
      get[Int]("default_research_guide") map {
      case gid ~ name ~ path ~ pic ~ desc ~ active ~ deft =>
        Guide(gid, name, path, pic, desc, active, deft)
    }
  }

  def blueprint(): Guide = Guide(Some(0), "", "", Some(""), Some(""), 0, 0)
  /*
  *   Create function
  */
  def create(name: String, path: String, picture: Option[String], description: Option[String]): Option[Long] = DB.withConnection {
    implicit connection =>
      SQL(
        """
      INSERT INTO
        research_guide
      (name_research_guide,path_research_guide, picture_research_guide, description_research_guide)
      VALUES
      ({n}, {p}, {pi}, {de})
        """
      ).on('n -> name, 'p -> path, 'pi -> picture, 'de -> description).executeInsert()
  }

  /*
  *   Listing functions
  */
  def findAll(active: Boolean = false): List[Guide] = DB.withConnection {
    implicit connection =>
      SQL(
        if (active) """
      SELECT * FROM research_guide WHERE active_research_guide = 1
                    """
        else """
      SELECT * FROM research_guide
             """
      ).as(rowExtractor *)
  }

  def find(param: String, active: Boolean = true): Option[Guide] = DB.withConnection {
    implicit connection =>
      (if (active) SQL("""
          SELECT
            *
          FROM
            research_guide
          WHERE
            path_research_guide = {param} AND
            active_research_guide = 1
          LIMIT 1
        """) else SQL("""
          SELECT
            *
          FROM
            research_guide
          WHERE
            path_research_guide = {param}
          LIMIT 1
        """)
        ).on('param -> param).as(rowExtractor.singleOpt)
  }
}


