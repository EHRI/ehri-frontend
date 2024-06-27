package services.fieldmeta

import anorm.{Column, ToStatement, TypeDoesNotMatch}
import models.{EntityType, FieldMetadata}
import org.postgresql.jdbc.PgArray


private[fieldmeta] object Implicits {
  implicit def entityTypeRowToEnum: Column[EntityType.Value] = {
    Column.nonNull[EntityType.Value] { (value, _) =>
      try Right(EntityType.withName(value.toString)) catch {
        case _: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  implicit def entityTypeToStatement: ToStatement[EntityType.Value] =
    (s: java.sql.PreparedStatement, index: Int, value: EntityType.Value) => s.setObject(index, value.toString)

  implicit def entityTypeOptionToStatement: ToStatement[Option[EntityType.Value]] =
    (s: java.sql.PreparedStatement, index: Int, value: Option[EntityType.Value]) => s.setObject(index, value.map(_.toString).orNull)

  implicit def stringSeqRowToSeq: Column[Seq[String]] = {
    Column.nonNull[Seq[String]] { (value, _) =>
      try Right(value.asInstanceOf[PgArray].getArray.asInstanceOf[Array[String]].toSeq) catch {
        case _: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  implicit def usageOptionToStatement: ToStatement[Option[FieldMetadata.Usage.Value]] =
    (s: java.sql.PreparedStatement, index: Int, value: Option[FieldMetadata.Usage.Value]) => s.setObject(index, value.map(_.toString).orNull)

}
