package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.Page

trait Annotations {
  def getAnnotationsForItem[A: Readable](id: String): Future[Page[A]]

  def createAnnotation[A <: WithId : Readable, AF: Writable](id: String, ann: AF, accessors: Seq[String] = Nil): Future[A]

  def createAnnotationForDependent[A <: WithId : Readable, AF: Writable](id: String, did: String, ann: AF, accessors: Seq[String] = Nil): Future[A]
}
