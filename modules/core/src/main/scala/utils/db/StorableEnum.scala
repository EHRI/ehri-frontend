package utils.db

import anorm.{ToStatement, TypeDoesNotMatch, Column}

trait StorableEnum {
  self: Enumeration =>

  implicit def rowToEnum: Column[Value] = {
    Column.nonNull[Value] { (value, _) =>
      try {
        Right(withName(value.toString))
      } catch {
        case e: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  implicit def enumToStatement: ToStatement[Value] =
    (s: java.sql.PreparedStatement, index: Int, value: Value) => s.setObject(index, value.toString)
}

