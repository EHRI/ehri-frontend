package services.storage

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import helpers.TestConfiguration
import play.api.test.PlaySpecification

class MockFileStorageSpec extends PlaySpecification with TestConfiguration {

  private val injector = appBuilder.injector
  private implicit val actorSystem: ActorSystem = injector.instanceOf[ActorSystem]
  private implicit val mat: Materializer = injector.instanceOf[Materializer]
  private implicit val ec = mat.executionContext

  private val bytes = Source.single(ByteString("Hello, world"))
  private val paths = Seq("bar", "baz", "spam", "eggs")
  private val bucket = "bucket1"

  def putTestItems: (MockFileStorage, Seq[String]) = {
    val storage = MockFileStorage(collection.mutable.Map.empty)
    val urls = paths.map { path =>
      await(storage.putBytes(bucket, path, bytes, public = true)).toString
    }
    storage -> urls
  }

  "storage service" should {
    "put items correctly" in {
      putTestItems._2 must_== paths.map(p => s"https://$bucket.mystorage.com/$p")
    }

    "get item bytes" in {
      val storage = putTestItems._1
      val bytes = await(storage.get(bucket, "baz")
        .collect { case Some((_, src)) => src }
        .map(_.runFold(ByteString.empty)(_ ++ _))
        .flatten
        .map(_.utf8String))
      bytes must_== "Hello, world"
    }

    "list items correctly" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(bucket))
      items.files.map(_.key) must_== paths
    }

    "list items correctly with prefix" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(bucket, Some("b")))
      items.files.map(_.key) must_== paths.filter(_.startsWith("b"))
    }

    "list items correctly with paging" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(bucket, max = 2))
      items.files.map(_.key) must_== paths.take(2)
      items.truncated must_== true

      val items2 = await(storage.listFiles(bucket, after = Some("baz"), max = 2))
      items2.files.map(_.key) must_== paths.drop(2)
      items2.truncated must_== false
    }
  }
}
