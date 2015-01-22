package auth.sql

import java.sql.Connection

import auth.OAuth2AssociationManager
import models.OAuth2Association
import play.api.db.DB

import scala.concurrent.{ExecutionContext, Future}
import anorm.SqlParser._
import anorm._

import scala.language.postfixOps


object SqlOAuth2AssociationManager {
  val oAuthParser = {
    get[String]("oauth2_association.id") ~
      get[String]("oauth2_association.provider_id") ~
      get[String]("oauth2_association.provider") map {
      case id ~ providerId ~ provider => OAuth2Association(id, providerId, provider, None)
    }
  }

  val oAuthWithUser = {
    oAuthParser ~ SqlAccountManager.userParser map {
      case association ~ user => association.copy(user = Some(user))
    }
  }
}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class SqlOAuth2AssociationManager()(implicit app: play.api.Application, executionContext: ExecutionContext)
  extends OAuth2AssociationManager{

  import SqlOAuth2AssociationManager._

  def findByProviderInfo(providerUserId: String, provider: String): Future[Option[OAuth2Association]] = Future {
    DB.withConnection { implicit connection =>
      getByInfo(providerUserId, provider)
    }
  }(executionContext)

  def findForAccount(id: String): Future[Seq[OAuth2Association]] = Future {
    DB.withConnection { implicit conn =>
      getForAccount(id)
    }
  }(executionContext)

  def findAll: Future[Seq[OAuth2Association]] = Future {
    DB.withConnection { implicit connection =>
      SQL"""
        SELECT users.*, oauth2_association.*
        FROM oauth2_association
        JOIN users ON oauth2_association.id =  users.id
      """.as(oAuthWithUser *)
    }
  }(executionContext)

  def addAssociation(id: String, providerId: String, provider: String): Future[Option[OAuth2Association]] = Future {
    DB.withConnection { implicit connection =>
      SQL"""
        INSERT INTO oauth2_association (id, provider_id, provider) VALUES ($id, $providerId, $provider)
        """.executeInsert()
      getByInfo(providerId, provider)
    }
  }(executionContext)


  private def getForAccount(id: String)(implicit conn: Connection): Seq[OAuth2Association] = {
    SQL"""
      SELECT users.*, oauth2_association.*
      FROM oauth2_association
      JOIN users ON oauth2_association.id =  users.id
      WHERE users.id = $id
    """.as(oAuthWithUser *)
  }

  private def getByInfo(providerUserId: String, provider: String)(implicit conn: Connection): Option[OAuth2Association] = {
    SQL"""
      SELECT users.*, oauth2_association.*
      FROM oauth2_association
      JOIN users ON oauth2_association.id =  users.id
      WHERE oauth2_association.provider_id = $providerUserId
      AND oauth2_association.provider = $provider LIMIT 1
    """.as(oAuthWithUser.singleOpt)
  }
}
