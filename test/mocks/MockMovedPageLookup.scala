package mocks

import utils.MovedPageLookup

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}

case class MockMovedPageLookup() extends MovedPageLookup {
  override def hasMovedTo(path: String): Future[Option[String]] =
    immediate(mocks.movedPages.find(_._1 == path).map(_._2))

  override def addMoved(moved: Seq[(String, String)]): Future[Int] =
    immediate {
      mocks.movedPages.appendAll(moved)
      moved.size
    }
}