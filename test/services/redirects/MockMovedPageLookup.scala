package services.redirects

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

case class MockMovedPageLookup(movedPages: mutable.Buffer[(String, String)]) extends MovedPageLookup {
  override def hasMovedTo(path: String): Future[Option[String]] =
    immediate(movedPages.find(_._1 == path).map(_._2))

  override def addMoved(moved: Seq[(String, String)]): Future[Int] =
    immediate {
      movedPages.appendAll(moved)
      moved.size
    }
}