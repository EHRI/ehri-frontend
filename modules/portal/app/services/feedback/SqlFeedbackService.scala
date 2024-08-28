package services.feedback

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime

import akka.actor.ActorSystem
import anorm._
import javax.inject.{Inject, Singleton}
import models.{Feedback, FeedbackContext}
import org.apache.commons.io.IOUtils
import play.api.Mode
import play.api.db.Database
import play.api.libs.json.{JsError, JsSuccess, Json}
import services.data.ItemNotFound
import utils.{Page, PageParams}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
case class SqlFeedbackService @Inject ()(db: Database, actorSystem: ActorSystem) extends FeedbackService {

  private implicit def executionContext: ExecutionContext =
    actorSystem.dispatchers.lookup("contexts.simple-db-lookups")

  private implicit def columnToModeEnum: Column[play.api.Mode] = {
    Column.nonNull[play.api.Mode] { (value, meta) =>
      value.toString match {
        case "Dev" => Right(Mode.Dev)
        case "Test" => Right(Mode.Test)
        case "Prod" => Right(Mode.Prod)
        case _ => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  private implicit def modeEnumToStatement = new ToStatement[Option[play.api.Mode]] {
    def set(s: java.sql.PreparedStatement, index: Int, value: Option[play.api.Mode]): Unit =
      s.setObject(index, value.map(_.toString).orNull)
  }

  private implicit def columnToTypeEnum: Column[Feedback.Type.Value] = {
    Column.nonNull[Feedback.Type.Value] { (value, meta) =>
      try {
        Right(Feedback.Type.withName(value.toString))
      } catch {
        case e: Throwable => Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  private implicit def typeEnumToStatement = new ToStatement[Option[Feedback.Type.Value]] {
    def set(s: java.sql.PreparedStatement, index: Int, value: Option[Feedback.Type.Value]): Unit =
      s.setObject(index, value.map(_.toString).orNull)
  }

  private implicit def columnToContext: Column[FeedbackContext] = {
    Column.nonNull[FeedbackContext] { (value, meta) =>
      val s = value match {
        case t: java.sql.Clob =>
          val b = new ByteArrayOutputStream()
          IOUtils.copy(t.getCharacterStream, b, StandardCharsets.UTF_8)
          b.toString("UTF-8")
        case _ => value.toString
      }
      Json.parse(s).validate[FeedbackContext] match {
        case JsSuccess(ctx, _) => Right(ctx)
        case JsError(err) =>Left(TypeDoesNotMatch(
          s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to ${getClass.getName}"))
      }
    }
  }

  private implicit def contextToStatement = new ToStatement[Option[FeedbackContext]] {
    def set(s: java.sql.PreparedStatement, index: Int, value: Option[FeedbackContext]): Unit =
      s.setObject(index, value.map(v => Json.stringify(Json.toJson(v))).orNull)
  }

  private implicit val feedbackParser: RowParser[Feedback] =
    Macro.parser[Feedback]("id", "user_id", "name", "email", "text", "type", "copy", "context", "created", "updated", "mode")

  override def get(id: String): Future[Feedback] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM feedback WHERE id = $id"
        .as(feedbackParser.singleOpt).getOrElse(throw new ItemNotFound(id))
    }
  }

  override def create(data: Feedback): Future[String] = Future {
    db.withConnection { implicit conn =>
      val feedback = data.copy(objectId = data.objectId
          .orElse(Some(utils.db.newObjectId(10))),
        createdAt = data.createdAt.orElse(Some(ZonedDateTime.now)))
      SQL"""INSERT INTO feedback
        (id, user_id, name, email, text, type, copy, context, created, updated, mode)
        VALUES (
          ${feedback.objectId},
          ${feedback.userId},
          ${feedback.name},
          ${feedback.email},
          ${feedback.text},
          ${feedback.`type`},
          ${feedback.copyMe},
          ${feedback.context},
          ${feedback.createdAt},
          ${feedback.updatedAt},
          ${feedback.mode}
      )""".execute()
      feedback.objectId.get
    }
  }

  override def delete(id: String): Future[Boolean] = Future {
    db.withConnection { implicit  conn =>
      SQL"DELETE FROM feedback WHERE id = $id".executeUpdate() == 1
    }
  }

  override def list(params: PageParams, extra: Map[String, String]): Future[Page[Feedback]] = Future {
    db.withTransaction { implicit conn =>
      val items: List[Feedback] = SQL"""
        SELECT * FROM feedback
          ORDER BY created DESC
          LIMIT ${if (params.hasLimit) params.limit else Integer.MAX_VALUE}
          OFFSET ${params.offset}
        """.as(feedbackParser.*)
      val total: Int = SQL"SELECT COUNT(id) FROM feedback".as(SqlParser.scalar[Int].single)
      Page(items = items, total = total, offset = params.offset, limit = params.limit)
    }
  }
}
