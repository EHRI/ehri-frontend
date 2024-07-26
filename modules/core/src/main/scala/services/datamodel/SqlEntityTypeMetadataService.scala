package services.datamodel

import akka.actor.ActorSystem
import anorm.{Column, SqlStringInterpolation, ToStatement, TypeDoesNotMatch}
import models._
import org.postgresql.jdbc.PgArray
import play.api.db.Database

import javax.inject.Inject
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}


case class SqlEntityTypeMetadataService @Inject()(db: Database, actorSystem: ActorSystem) extends EntityTypeMetadataService {

  private implicit def ec: ExecutionContext = actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

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

  private implicit val entityTypeMetaParser: anorm.RowParser[EntityTypeMetadata] = anorm.Macro.parser[EntityTypeMetadata](
    "entity_type",
    "name",
    "description",
    "created",
    "updated"
  )

  private implicit val fieldMetaParser: anorm.RowParser[FieldMetadata] = anorm.Macro.parser[FieldMetadata](
    "entity_type",
    "id",
    "name",
    "description",
    "usage",
    "category",
    "default_val",
    "see_also",
    "created",
    "updated"
  )


  override def list(): Future[Map[EntityType.Value, EntityTypeMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
      SELECT * FROM entity_type_meta
      ORDER BY entity_type
     """.as(entityTypeMetaParser.*).map(em => em.entityType -> em).toMap
    }
  }(ec)

  override def get(entityType: EntityType.Value): Future[Option[EntityTypeMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT * FROM entity_type_meta
        WHERE entity_type = $entityType
       """.as(entityTypeMetaParser.singleOpt)
    }
  }(ec)

  override def save(entityType: EntityType.Value, info: EntityTypeMetadataInfo): Future[EntityTypeMetadata] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            INSERT INTO entity_type_meta(entity_type, name, description)
            VALUES (
              $entityType,
              ${info.name},
              ${info.description}
            )
            ON CONFLICT (entity_type) DO UPDATE SET
              name = ${info.name},
              description = ${info.description},
              updated = NOW()
            RETURNING *
           """.as(entityTypeMetaParser.single)
    }
  }(ec)

  override def delete(entityType: EntityType.Value): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            DELETE FROM entity_type_meta
            WHERE entity_type = $entityType
           """.executeUpdate() == 1
    }
  }(ec)

  override def listFields(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, FieldMetadataSet]] = Future {
    templates().map { tmpl =>
      val unordered = db.withConnection { implicit conn =>
        SQL"""
        SELECT * FROM field_meta
        WHERE COALESCE($entityType, '') = ''
        OR entity_type = ${entityType.map(_.toString)}
        ORDER BY
          entity_type,
          category
       """.as(fieldMetaParser.*)
      }.groupBy(_.entityType)
      (for ((entityType, sections) <- tmpl) yield {
        val fms = sections.toSeq.flatMap { case (_, fieldIds) =>
          fieldIds.flatMap(id => unordered.getOrElse(entityType, Seq.empty).find(_.id == id))
        }
        entityType -> FieldMetadataSet(ListMap(fms.map(fm => fm.id -> fm): _*))
      }).filter(_._2.nonEmpty)
    }
  }(ec).flatten

  override def listEntityTypeFields(entityType: EntityType.Value): Future[FieldMetadataSet] =
    listFields(Some(entityType)).map(_.getOrElse(entityType, FieldMetadataSet(ListMap.empty)))

  override def getField(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            SELECT * FROM field_meta
            WHERE entity_type = $entityType
            AND id = $id
           """.as(fieldMetaParser.singleOpt)
    }
  }(ec)

  override def saveField(entityType: EntityType.Value, id: String, info: FieldMetadataInfo): Future[FieldMetadata] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            INSERT INTO field_meta(entity_type, id, name, description, usage, category, default_val, see_also)
            VALUES (
              $entityType,
              $id,
              ${info.name},
              ${info.description.filter(_.trim.nonEmpty)},
              ${info.usage},
              ${info.category},
              ${info.defaultVal.filter(_.trim.nonEmpty)},
              ARRAY[${info.seeAlso}]::text[]
            )
            ON CONFLICT (entity_type, id) DO UPDATE SET
              name = ${info.name},
              description = ${info.description.filter(_.trim.nonEmpty)},
              usage = ${info.usage},
              category = ${info.category},
              default_val = ${info.defaultVal.filter(_.trim.nonEmpty)},
              see_also = ARRAY[${info.seeAlso}]::text[],
              updated = NOW()
            RETURNING *
           """.as(fieldMetaParser.single)
    }
  }(ec)

  override def deleteField(entityType: EntityType.Value, id: String): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            DELETE FROM field_meta
            WHERE entity_type = ${entityType}
            AND id = $id
           """.executeUpdate() == 1
    }
  }(ec)
}
