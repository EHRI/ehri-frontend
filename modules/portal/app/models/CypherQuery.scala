package models

import java.time.ZonedDateTime

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.json._

import scala.util.matching.Regex


/**
  * A pre-baked Cypher query.
  */
case class CypherQuery(
  objectId: Option[String] = None,
  userId: Option[String] = None,
  name: String,
  query: String,
  description: Option[String] = None,
  public: Boolean = false,
  created: Option[ZonedDateTime] = None,
  updated: Option[ZonedDateTime] = None
)

object CypherQuery {

  val ID = "userId"
  val NAME = "name"
  val QUERY = "query"
  val DESCRIPTION = "description"
  val PUBLIC = "public"
  val CREATED = "created"
  val UPDATED = "updated"

  implicit val _format: Format[CypherQuery] = Json.format[CypherQuery]

  // Mutation Cypher queries - if they add more and we don't update this
  // we're a bit screwed :|
  val dangerousClauses: Regex = "\\b(CREATE|SET|UPDATE|DELETE|MERGE|REMOVE|DETACH)\\b".r
  val isReadOnly: Constraint[String] = Constraint[String]("cypherQuery.mutatingClauses") { q =>
    dangerousClauses.findFirstIn(q.toUpperCase) match {
      case Some(clause) => Invalid("cypherQuery.mutatingClauses.error", clause)
      case None => Valid
    }
  }

  implicit val form = Form(
    mapping(
      "objectId" -> ignored(Option.empty[String]),
      ID -> optional(text),
      NAME -> nonEmptyText,
      QUERY -> nonEmptyText.verifying(isReadOnly),
      DESCRIPTION -> optional(text),
      PUBLIC -> boolean,
      CREATED -> ignored(Option.empty[ZonedDateTime]),
      UPDATED -> ignored(Option.empty[ZonedDateTime])
    )(CypherQuery.apply)(CypherQuery.unapply)
  )
}
