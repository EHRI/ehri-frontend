package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.Account



// -- Associations

case class OAuth2Association(id: String, providerId: String, provider: String, user: Option[Account] = None) {

  lazy val users: Seq[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users
        join oauth2_association on oauth2_association.id = users.id
        where oauth2_association.id = {id}
      """
    ).on('id -> id).as(SqlAccount.simple *)
  }
}

object OAuth2Association {

  val simple = {
    get[String]("oauth2_association.id") ~
    get[String]("oauth2_association.provider_id") ~
    get[String]("oauth2_association.provider") map {
      case id ~ providerId ~ provider => OAuth2Association(id, providerId, provider, None)
    }
  }

  val withUser = {
    simple ~ SqlAccount.simple map {
      case association ~ user => association.copy(user = Some(user))
    }
  }

  def findAll: Seq[OAuth2Association] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from oauth2_association join users on oauth2_association.id =  users.id
      """
    ).as(OAuth2Association.withUser *)
  }

  def findByProviderInfo(providerId: String, provider: String): Option[OAuth2Association] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from oauth2_association
          join users on oauth2_association.id =  users.id where
        oauth2_association.provider_id = {provider_id}
         AND oauth2_association.provider = {provider} LIMIT 1
      """
    ).on('provider_id -> providerId, 'provider -> provider).as(OAuth2Association.withUser.singleOpt)
  }

  def addAssociation(acc: Account, providerId: String, provider: String): Unit = DB.withConnection { implicit connection =>
    SQL(
      """
        INSERT INTO oauth2_association (id, provider_id, provider) VALUES ({id},{provider_id},{provider})
      """
    ).on('id -> acc.id, 'provider_id -> providerId, 'provider -> provider).executeInsert()
  }
}
