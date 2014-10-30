package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Annotations {
  def getAnnotationsForItem[A](id: String)(implicit apiUser: ApiUser, rs: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def createAnnotation[A,AF](id: String, ann: AF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[A], wr: BackendWriteable[AF], executionContext: ExecutionContext): Future[A]

  def createAnnotationForDependent[A,AF](id: String, did: String, ann: AF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[A], wr: BackendWriteable[AF], executionContext: ExecutionContext): Future[A]
}
