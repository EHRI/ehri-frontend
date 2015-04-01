package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Annotations {
  def getAnnotationsForItem[A](id: String)(implicit rs: Readable[A]): Future[Page[A]]

  def createAnnotation[A <: WithId, AF](id: String, ann: AF, accessors: Seq[String] = Nil)(implicit rs: Readable[A], wr: BackendWriteable[AF]): Future[A]

  def createAnnotationForDependent[A <: WithId, AF](id: String, did: String, ann: AF, accessors: Seq[String] = Nil)(implicit rs: Readable[A], wr: BackendWriteable[AF]): Future[A]
}
