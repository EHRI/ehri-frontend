package models.sql

import language.postfixOps
import play.api.db._
import play.api.Play.current

import anorm._
import anorm.SqlParser._
import models.Account



// -- Associations

case class OpenIDAssociation(id: String, url: String, user: Option[Account] = None) {

  lazy val users: Seq[Account] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from users
        join openid_association on openid_association.id = users.id
        where openid_association.id = {id}
      """
    ).on('id -> id).as(SqlAccount.simple *)
  }
}

object OpenIDAssociation {

  val simple = {
    get[String]("openid_association.id") ~
    get[String]("openid_association.openid_url") map {
      case id ~ url => OAuth2Association(id, url, None)
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
        select * from openid_association join users on openid_association.id =  users.id
      """
    ).as(OAuth2Association.withUser *)
  }

  def findByUrl(url: String): Option[OAuth2Association] = DB.withConnection { implicit connection =>
    SQL(
      """
        select * from openid_association
          join users on openid_association.id =  users.id where
        openid_association.openid_url = {url} LIMIT 1
      """
    ).on('url -> url).as(OAuth2Association.withUser.singleOpt)
  }

  def addAssociation(acc: Account, assoc: String): Unit = DB.withConnection { implicit connection =>
    SQL(
      """
        INSERT INTO openid_association (id, openid_url) VALUES ({id},{url})
      """
    ).on('id -> acc.id, 'url -> assoc).executeInsert()
  }
}
