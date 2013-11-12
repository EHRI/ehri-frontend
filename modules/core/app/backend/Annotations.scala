package backend

import scala.concurrent.Future
import models.{Annotation,AnnotationF}

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Annotations {
  def getAnnotationsForItem(id: String)(implicit apiUser: ApiUser): Future[Map[String,List[Annotation]]]
  def createAnnotation(id: String, ann: AnnotationF)(implicit apiUser: ApiUser): Future[Annotation]
}
