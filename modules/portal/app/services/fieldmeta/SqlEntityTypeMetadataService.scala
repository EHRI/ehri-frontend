package services.fieldmeta

import akka.actor.ActorSystem
import anorm.SqlStringInterpolation
import models.{EntityType, EntityTypeMetadata, EntityTypeMetadataInfo}
import play.api.db.Database

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class SqlEntityTypeMetadataService @Inject()(db: Database, actorSystem: ActorSystem) extends EntityTypeMetadataService {
  import Implicits._

  private implicit def ec: ExecutionContext = actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit val entityTypeMetaParser: anorm.RowParser[EntityTypeMetadata] = anorm.Macro.parser[EntityTypeMetadata](
    "entity_type",
    "name",
    "description",
    "created",
    "updated"
  )


  def list(entityType: Option[EntityType.Value] = None): Future[Map[EntityType.Value, EntityTypeMetadata]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT * FROM entity_type_meta
        WHERE COALESCE($entityType, '') = ''
        OR entity_type = ${entityType.map(_.toString)}
        ORDER BY entity_type
       """.as(entityTypeMetaParser.*).map(em => em.entityType -> em).toMap
    }
  }(ec)

  def save(entityType: EntityType.Value, info: EntityTypeMetadataInfo): Future[EntityTypeMetadata] = Future {
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

  def delete(entityType: EntityType.Value): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"""
            DELETE FROM entity_type_meta
            WHERE entity_type = $entityType
           """.executeUpdate() == 1
    }
  }(ec)
}
