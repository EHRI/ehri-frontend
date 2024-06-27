package services.fieldmeta

import akka.actor.ActorSystem
import anorm.SqlStringInterpolation
import models.{EntityType, FieldMetadata, FieldMetadataInfo, FieldMetadataSet}
import play.api.db.Database

import javax.inject.Inject
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

case class SqlFieldMetadataService @Inject ()(db: Database, actorSystem: ActorSystem) extends FieldMetadataService {
  import Implicits._

  private implicit def ec: ExecutionContext = actorSystem.dispatchers.lookup("contexts.simple-db-lookups")


  private implicit val fieldMetaParser: anorm.RowParser[FieldMetadata] = anorm.Macro.parser[FieldMetadata](
    "entity_type",
    "id",
    "name",
    "description",
    "usage",
    "category",
    "see_also",
    "created",
    "updated"
  )


  def list(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, FieldMetadataSet]] = Future {
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
        val fms = sections.flatMap { case (_, fieldIds) =>
          fieldIds.flatMap(id => unordered.getOrElse(entityType, Seq.empty).find(_.id == id))
        }
        entityType -> FieldMetadataSet(ListMap(fms.map(fm => fm.id -> fm): _*))
      }).filter(_._2.nonEmpty)
    }
  }(ec).flatten

  def get(entityType: EntityType.Value, id: String): Future[Option[FieldMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            SELECT * FROM field_meta
            WHERE entity_type = ${entityType}
            AND id = $id
           """.as(fieldMetaParser.singleOpt)
    }
  }(ec)

  def save(entityType: EntityType.Value, id: String, info: FieldMetadataInfo): Future[FieldMetadata] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            INSERT INTO field_meta(entity_type, id, name, description, usage, category, see_also)
            VALUES (
              $entityType,
              $id,
              ${info.name},
              ${info.description},
              ${info.usage},
              ${info.category},
              ARRAY[${info.seeAlso}]::text[]
            )
            ON CONFLICT (entity_type, id) DO UPDATE SET
              name = ${info.name},
              description = ${info.description},
              usage = ${info.usage},
              category = ${info.category},
              see_also = ARRAY[${info.seeAlso}]::text[],
              updated = NOW()
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
