package auth.sql

import java.sql.Connection

import auth.OpenIdAssociationManager
import models.OpenIDAssociation
import play.api.db.DB

import scala.concurrent.{Future, ExecutionContext}
import anorm.SqlParser._
import anorm._

import scala.language.postfixOps

object SqlOpenIdAssociationManager {
  val openIdParser = {
    get[String]("openid_association.id") ~
      get[String]("openid_association.openid_url") map {
      case id ~ url => OpenIDAssociation(id, url, None)
    }
  }

  val openIdWithUser = {
    openIdParser ~ SqlAccountManager.userParser map {
      case association ~ user => association.copy(user = Some(user))
    }
  }
}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlOpenIdAssociationManager()(implicit app: play.api.Application, executionContext: ExecutionContext)
  extends OpenIdAssociationManager{

  import SqlOpenIdAssociationManager._

  def findByUrl(url: String): Future[Option[OpenIDAssociation]] = Future {
    DB.withConnection { implicit connection =>
      getByUrl(url)
    }
  }(executionContext)

  def addAssociation(id: String, assoc: String): Future[Option[OpenIDAssociation]] = Future {
    DB.withConnection { implicit connection =>
      SQL(
        """
        INSERT INTO openid_association (id, openid_url) VALUES ({id},{url})
        """
      ).on('id -> id, 'url -> assoc).executeInsert()
      getByUrl(assoc)
    }
  }(executionContext)

  def findAll: Future[Seq[OpenIDAssociation]] = Future {
    DB.withConnection { implicit conn =>
      SQL(
        """
        SELECT * FROM openid_association JOIN users ON openid_association.id =  users.id
        """
      ).as(openIdWithUser *)
    }
  }(executionContext)

  private def getByUrl(url: String)(implicit conn: Connection) = SQL(
    """
        SELECT * FROM openid_association
          JOIN users ON openid_association.id =  users.id WHERE
        openid_association.openid_url = {url} LIMIT 1
    """
  ).on('url -> url).as(openIdWithUser.singleOpt)
}
