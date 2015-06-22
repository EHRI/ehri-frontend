package auth

import org.apache.commons.codec.digest.DigestUtils
import org.mindrot.jbcrypt.BCrypt

/**
 * Wrapper around a plain password string.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object HashedPassword {
  def fromPlain(plain: String) = new HashedPassword(BCrypt.hashpw(plain.toString, BCrypt.gensalt()))
  def fromHashed(hashed: String) = new HashedPassword(hashed)

  import anorm.{TypeDoesNotMatch, Column, ToStatement, ParameterMetaData}

  /**
   * Implicit conversion from HashedPassword to Anorm statement value
   */
  implicit def pwToStatement: ToStatement[HashedPassword] = new ToStatement[HashedPassword] {
    def set(s: java.sql.PreparedStatement, index: Int, aValue: HashedPassword): Unit =
      s.setString(index, aValue.s)
  }

  implicit object HashedPasswordParameterMetaData extends ParameterMetaData[HashedPassword] {
    val sqlType = ParameterMetaData.StringParameterMetaData.sqlType
    val jdbcType = ParameterMetaData.StringParameterMetaData.jdbcType
  }

  /**
   * Implicit conversion from Anorm row to HashedPassword
   */
  implicit def rowToPw: Column[HashedPassword] = {
    Column.nonNull1[HashedPassword] { (value, meta) =>
      value match {
        case v: String => Right(HashedPassword.fromHashed(v))
        case _ => Left(TypeDoesNotMatch(
          s"Cannot convert $value:${value.asInstanceOf[AnyRef].getClass} to hashed password for column ${meta.column}"))
      }
    }
  }
}

case class HashedPassword private(s: String) {
  def check(pw: String) =  BCrypt.checkpw(pw, s)

  /**
   * Legacy passwords from the Drupal 6 system were stored as
   * unsalted MD5. These were BCrypted when imported so the
   * incoming password needs MD5ing prior to checking.
   */
  def checkLegacy(pw: String) =  BCrypt.checkpw(DigestUtils.md5Hex(pw), s)

  override def toString = s
}
