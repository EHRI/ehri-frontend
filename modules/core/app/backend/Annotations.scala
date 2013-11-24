package backend

import scala.concurrent.Future
import models.{Annotation,AnnotationF}

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Annotations {
  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Seq[Annotation]]
  def createAnnotation(id: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser): Future[Annotation]
  def createAnnotationForDependent(id: String, did: String, ann: AnnotationF, accessors: Seq[String] = Nil)(implicit apiUser: ApiUser): Future[Annotation]
}
