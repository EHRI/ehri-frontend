package services.fieldmeta

import akka.actor.ActorSystem
import anorm.{Column, SqlStringInterpolation, ToStatement, TypeDoesNotMatch}
import models.{EntityType, FieldMetadata, FieldMetadataInfo}
import org.postgresql.jdbc.PgArray
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlFieldMetadataService @Inject ()(db: Database, actorSystem: ActorSystem) extends FieldMetadataService {

  private implicit def ec: ExecutionContext = actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit def entityTypeRowToEnum: Column[EntityType.Value] = {
    Column.nonNull[EntityType.Value] { (value, _) =>
      try Right(EntityType.withName(value.toString)) catch {
        case _: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  private implicit def entityTypeToStatement: ToStatement[EntityType.Value] =
    (s: java.sql.PreparedStatement, index: Int, value: EntityType.Value) => s.setObject(index, value.toString)

  private implicit def entityTypeOptionToStatement: ToStatement[Option[EntityType.Value]] =
    (s: java.sql.PreparedStatement, index: Int, value: Option[EntityType.Value]) => s.setObject(index, value.map(_.toString).orNull)

  private implicit def stringSeqRowToSeq: Column[Seq[String]] = {
    Column.nonNull[Seq[String]] { (value, _) =>
      try Right(value.asInstanceOf[PgArray].getArray.asInstanceOf[Array[String]].toSeq) catch {
        case _: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  private implicit def usageOptionToStatement: ToStatement[Option[FieldMetadata.Usage.Value]] =
    (s: java.sql.PreparedStatement, index: Int, value: Option[FieldMetadata.Usage.Value]) => s.setObject(index, value.map(_.toString).orNull)
//
  private implicit def stringSeqToStatement: ToStatement[Seq[String]] =
    (s: java.sql.PreparedStatement, index: Int, value: Seq[String]) => s.setArray(index, s.getConnection.createArrayOf("text", value.toArray))

  private implicit val fieldMetaParser: anorm.RowParser[FieldMetadata] = anorm.Macro.parser[FieldMetadata](
    "entity_type",
    "id",
    "name",
    "description",
    "usage",
    "category",
    "see_other",
    "created",
    "updated"
  )


  def list(entityType: Option[EntityType.Value] = None): Future[Seq[FieldMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            SELECT * FROM field_meta
            WHERE COALESCE($entityType, '') = ''
            OR entity_type = ${entityType.map(_.toString)}
           """.as(fieldMetaParser.*)
    }
  }(ec)

  def get(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            SELECT * FROM field_meta
            WHERE entity_type = ${entityType}
            AND id = $id
           """.as(fieldMetaParser.singleOpt)
    }
  }(ec)

  def create(entityType: EntityType.Value, id: String, fieldMeta: FieldMetadataInfo): Future[FieldMetadata] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            INSERT INTO field_meta(entity_type, id, name, description, usage, category, see_other)
            VALUES (
              $entityType,
              $id,
              ${fieldMeta.name},
              ${fieldMeta.description},
              ${fieldMeta.usage},
              ${fieldMeta.category},
              ${fieldMeta.seeOther}
            ) ON CONFLICT (entity_type, id) DO UPDATE SET
              name = ${fieldMeta.name},
              description = ${fieldMeta.description},
              usage = ${fieldMeta.usage},
              category = ${fieldMeta.category},
              see_other = ${fieldMeta.seeOther}
            RETURNING *
           """.as(fieldMetaParser.single)
    }
  }(ec)

  def update(entityType: EntityType.Value, id: String, fieldMeta: FieldMetadataInfo): Future[FieldMetadata] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            UPDATE field_meta
            SET
              category = ${fieldMeta.category},
              name = ${fieldMeta.name},
              description = ${fieldMeta.description},
              usage = ${fieldMeta.usage},
              see_other = ${fieldMeta.seeOther},
              updated = NOW()
            WHERE entity_type = $entityType
            AND id = $id
            RETURNING *
           """.as(fieldMetaParser.single)
    }
  }(ec)

  def delete(entityType: EntityType.Value, id: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            DELETE FROM field_meta
            WHERE entity_type = ${entityType}
            AND id = $id
           """.executeUpdate() == 1
    }
  }(ec)
}
