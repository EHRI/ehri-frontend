package models

import defines.EventType
import models.base.{AnyModel, Accessor}
import org.joda.time.DateTime

/**
 * Summarise a set of events. Typically the events will be
 * aggregated by either a) being sequential and having the
 * same user, or b) being repeated actions on the same item
 * by the same user.
 */
case class EventSummary(events: Seq[SystemEvent]) {
  lazy val user: Option[Accessor] = events.headOption.flatMap(_.actioner)
  lazy val timestamp: Option[DateTime] = events.headOption.map(_.model.timestamp)
  lazy val eventTypes: Set[EventType.Value] = events.flatMap(_.effectiveType).toSet
  lazy val firstSubjects: Set[AnyModel] = events.flatMap(_.effectiveSubject).toSet

  def from: Option[DateTime] = events.headOption.map(_.model.timestamp)
  def to: Option[DateTime] = events.lastOption.map(_.model.timestamp)

  def sameSubject: Boolean = firstSubjects.size == 1
  def sameType: Boolean = eventTypes.size == 1

  def bySubject: Map[String, Seq[SystemEvent]] = events
      .foldLeft(Map.empty[String, Seq[SystemEvent]]) { (m, e) =>
    val subjectId: String = e.effectiveSubject.map(_.id).getOrElse("")
    m.updated(subjectId, m.getOrElse(subjectId, Seq.empty) :+ e )
  }

  def byType: Map[EventType.Value, Seq[SystemEvent]] = events
      .foldLeft(Map.empty[EventType.Value, Seq[SystemEvent]]) { (m, e) =>
    e.effectiveType.map { et =>
      m.updated(et, m.getOrElse(et, Seq.empty) :+ e)
    }.getOrElse(m)
  }

  def byTypeAndFirstSubject: Map[EventType.Value, Set[AnyModel]] = events
    .foldLeft(Map.empty[EventType.Value, Set[AnyModel]]) { (m, e) =>
    val et = e.effectiveType.getOrElse(EventType.modification)
    e.effectiveSubject.map { s =>
      m.updated(et, m.getOrElse(et, Set.empty) + s)
    }.getOrElse(m)
  }
}
