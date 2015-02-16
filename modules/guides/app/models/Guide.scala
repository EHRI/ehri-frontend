package models

import play.api.data.Form
import play.api.data.Forms._

import anorm._
import anorm.SqlParser._
import play.api.Play.current
import play.api.db.DB
import language.postfixOps
import scala.util.Try
import models.sql.withIntegrityCheck

/**
 * @author Thibault Clérice (http://github.com/ponteineptique)
 */
case class Guide(
  id: Option[Long] = None,
  name: String,
  path: String,
  picture: Option[String] = None,
  virtualUnit: String,
  description: Option[String] = None,
  css: Option[String] = None,
  active: Int = 0,
  default: Long = 0
) {
  def update(): Try[Unit] = withIntegrityCheck { implicit connection =>
      SQL"""
      UPDATE
        research_guide
      SET
        name = $name,
        path = $path,
        picture = $picture,
        virtual_unit = $virtualUnit,
        description = $description,
        css = $css,
        active = $active,
        `default` = $default
      WHERE
        id = $id
      LIMIT 1
      """.executeUpdate()
  }

  def delete(): Unit = DB.withConnection { implicit connection =>
    SQL"""DELETE FROM research_guide WHERE id = $id LIMIT 1""".executeUpdate()
  }
  
  def findPages(): List[GuidePage] = DB.withConnection { implicit connection =>
    id.map { id =>
        SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = $id"""
          .as(GuidePage.rowExtractor *)
    }.toList.flatten
  }

  def findPage(path: String): Option[GuidePage] = DB.withConnection { implicit connection =>
    id.flatMap { id =>
      SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = $id AND path = $path"""
        .as(GuidePage.rowExtractor.singleOpt)
    }
  }

  // FIXME: This function seems dubious, and fails if `default` doesn't exist
  // shouldn't it fall back to any page?
  def getDefaultPage: Option[GuidePage] = DB.withConnection { implicit connection =>
    id.flatMap { id =>
      SQL"""SELECT * FROM research_guide_page WHERE research_guide_id = $id AND id = $default"""
        .as(GuidePage.rowExtractor.singleOpt)
    }
  }
}

object Guide {
  val PREFIX = "guide"
  val OBJECTID = "id"
  val PATH = "path"
  val NAME = "name"
  val VIRTUALUNIT = "virtual_unit"
  val DESCRIPTION = "description"
  val PICTURE = "picture"
  val ACTIVE = "active"
  val DEFAULT = "default"
  val CSS = "css"

  implicit val form = Form(
    mapping(
      OBJECTID -> ignored(Option.empty[Long]),
      NAME -> nonEmptyText,
      PATH -> nonEmptyText,
      PICTURE -> optional(nonEmptyText),
      VIRTUALUNIT -> nonEmptyText,
      DESCRIPTION -> optional(text),
      CSS -> optional(text),
      // FIXME: Active really shouldn't be an int
      ACTIVE -> optional(boolean).transform[Int](f => if(f.getOrElse(false)) 1 else 0, i => Some(i > 0)),
      DEFAULT -> longNumber
    )(Guide.apply)(Guide.unapply)
  )

  val rowExtractor = {
    get[Option[Long]](OBJECTID) ~
      get[String](NAME) ~
      get[String](PATH) ~
      get[Option[String]](PICTURE) ~
      get[String](VIRTUALUNIT) ~
      get[Option[String]](DESCRIPTION) ~
      get[Option[String]](CSS) ~
      get[Int](ACTIVE) ~
      get[Long](DEFAULT) map {
      case gid ~ name ~ path ~ pic ~ virtualUnit ~ desc ~ css ~ active ~ deft =>
        Guide(gid, name, path, pic, virtualUnit, desc, css, active, deft)
    }
  }

  def blueprint(): Guide = Guide(Some(0), "", "", Some(""), "", Some(""), Some(""), active = 0, 0)

  /*
  *   Create function
  */
  def create(name: String, path: String, picture: Option[String] = None, virtualUnit: String, description: Option[String] = None, css: Option[String] = None, active: Int): Try[Option[Guide]] = withIntegrityCheck { implicit connection =>
    val id: Option[Long] = SQL"""
    INSERT INTO research_guide
      (name, path, picture, virtual_unit, description, css, active)
    VALUES ($name, $path, $picture, $virtualUnit, $description, $css, $active)
    """.executeInsert()
    id.flatMap(findById)
  }

  /*
  *   Listing functions
  */
  def findAll(activeOnly: Boolean = false): List[Guide] = DB.withConnection { implicit connection =>
    (if (activeOnly) SQL"""SELECT * FROM research_guide WHERE active = 1"""
      else SQL"""SELECT * FROM research_guide"""
    ).as(rowExtractor *)
  }

  def find(path: String, activeOnly: Boolean = false): Option[Guide] = DB.withConnection { implicit connection =>
    (if (activeOnly) SQL"""SELECT * FROM research_guide WHERE path = $path AND active = 1 LIMIT 1"""
      else SQL"""SELECT * FROM research_guide WHERE path = $path LIMIT 1"""
    ).as(rowExtractor.singleOpt)
  }

  def findById(id: Long): Option[Guide] = DB.withConnection { implicit connection =>
    SQL"""SELECT * FROM research_guide WHERE id = $id""".as(rowExtractor.singleOpt)
  }

}


