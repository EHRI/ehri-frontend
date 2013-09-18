package models.sql

import models.{HashedPassword, Account}
import play.api.db.DB
import play.api.Play.current
import anorm._
import anorm.SqlParser._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PasswordManager {
  self: Account =>

  val id: String

  override def password: Option[HashedPassword] = DB.withConnection { implicit connection =>
    SQL(
      """SELECT data FROM user_auth WHERE user_auth.id = {id} LIMIT 1"""
    ).on('id -> id).as(str("data").singleOpt).map(HashedPassword.fromHashed)
  }

  def setPassword(data: HashedPassword): Account = DB.withConnection{ implicit connection =>
    val res = SQL("INSERT INTO user_auth (id, data) VALUES ({id},{data})")
      .on('id -> id, 'data -> data.toString).executeInsert()
    this
  }

  def updatePassword(data: HashedPassword): Account = DB.withConnection{ implicit connection =>
    val res = SQL("UPDATE user_auth SET data={data} WHERE id={id}")
      .on('id -> id, 'data -> data.toString).executeUpdate()
    this
  }
}
