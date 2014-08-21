package backend

import scala.concurrent.{ExecutionContext, Future}
import models.{Annotation, AnnotationF}
import utils.Page

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Annotations {
  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], executionContext: ExecutionContext): Future[Page[Annotation]]

  def createAnnotation(id: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], wr: BackendWriteable[AnnotationF], executionContext: ExecutionContext): Future[Annotation]

  def createAnnotationForDependent(id: String, did: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser, rs: BackendReadable[Annotation], wr: BackendWriteable[AnnotationF], executionContext: ExecutionContext): Future[Annotation]
}
