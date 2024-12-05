package services.storage

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import helpers.TestConfiguration
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext

class MockFileStorageSpec extends PlaySpecification with TestConfiguration {

  private val injector = appBuilder.injector()
  private implicit val actorSystem: ActorSystem = injector.instanceOf[ActorSystem]
  private implicit val mat: Materializer = injector.instanceOf[Materializer]
  private implicit val ec: ExecutionContext = mat.executionContext

  private val bytes = Source.single(ByteString("Hello, world"))
  private val paths = Seq("bar", "baz", "spam", "eggs")
  private val bucket = "bucket1"

  def putTestItems: (MockFileStorage, Seq[String]) = {
    val storage = MockFileStorage(bucket, collection.mutable.Map.empty)
    val urls = paths.map { path =>
      await(storage.putBytes(path, bytes, public = true)).toString
    }
    storage -> urls
  }

  "storage service" should {
    "put items correctly" in {
      putTestItems._2 must_== paths.map(p => s"https://$bucket.mystorage.com/$p")
    }

    "get item bytes" in {
      val storage = putTestItems._1
      val bytes = await(storage.get("baz")
        .collect { case Some((_, src)) => src }
        .map(_.runFold(ByteString.empty)(_ ++ _))
        .flatten
        .map(_.utf8String))
      bytes must_== "Hello, world"
    }

    "list items correctly" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles())
      items.files.map(_.key).sorted must_== paths.sorted
    }

    "list items correctly with prefix" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(Some("b")))
      items.files.map(_.key).sorted must_== paths.filter(_.startsWith("b")).sorted
    }

    "list items correctly with paging" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(max = 2))
      items.files.map(_.key) must_== paths.take(2)
      items.truncated must_== true

      val items2 = await(storage.listFiles(after = Some("baz"), max = 2))
      items2.files.map(_.key).sorted must_== paths.sorted.drop(2)
      items2.truncated must_== false
    }

    "delete files with a given prefix" in {
      val storage = putTestItems._1
      val deleted = await(storage.deleteFilesWithPrefix("b"))
      deleted.sorted must_== Seq("bar", "baz")
      val items = await(storage.listFiles())
      items.files.size must_== 2
    }

    "get item info with version ID" in {
      val storage = putTestItems._1
      val info: Option[(FileMeta, Map[String, String])] = await(storage.info("baz"))
      info must beSome.which(_._1.versionId must_== Some("1"))

      await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))
      val info2: Option[(FileMeta, Map[String, String])] = await(storage.info("baz"))
      info2 must beSome.which(_._1.versionId must_== Some("2"))

      val info3: Option[(FileMeta, Map[String, String])] = await(storage.info("baz", versionId = Some("1")))
      info3 must beSome.which(_._1.versionId must_== Some("1"))
    }

    "get item data with version ID" in {
      val storage = putTestItems._1
      await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))

      val data1 = await(storage.get("baz", versionId = Some("1")))
      data1 must beSome.which { case (_, src) =>
        val s = await(src.runFold(ByteString.empty)(_ ++ _))
        s.utf8String must_== "Hello, world"
      }
    }

    "list versions" in {
      val storage = putTestItems._1
      await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))

      val versions = await(storage.listVersions("baz"))
      versions.files.size must_== 2
      versions.files.head.versionId must beSome("2")
      versions.files.last.versionId must beSome("1")
    }
  }
}
