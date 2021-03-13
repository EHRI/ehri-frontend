package services.storage

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import helpers.TestConfiguration
import play.api.Configuration
import play.api.test.PlaySpecification

import scala.concurrent.ExecutionContext

class S3CompatibleFileStorageSpec extends PlaySpecification with TestConfiguration {

  private val injector = appBuilder.injector()
  private implicit val actorSystem: ActorSystem = injector.instanceOf[ActorSystem]
  private implicit val mat: Materializer = injector.instanceOf[Materializer]
  private implicit val ec: ExecutionContext = mat.executionContext
  private val config = injector.instanceOf[Configuration]

  private val bytes = Source.single(ByteString("Hello, world"))
  private val paths = Seq("bar", "baz", "spam", "eggs")
  private val bucket = "test"
  private val endpoint = config.get[String]("storage.test.config.endpoint-url")

  def putTestItems: (FileStorage, Seq[String]) = {
    val storage = S3CompatibleFileStorage(config.get[Config]("storage.test"))
    await(storage.setVersioned(enabled = true))
    val urls = paths.map { path =>
      await(storage.putBytes(path, bytes, public = true)).toString
    }
    storage -> urls
  }

  "storage service" should {
    "put items correctly" in {
      putTestItems._2.sorted must_== paths.sorted.map(p => s"$endpoint/$bucket/$p")
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
      items.files.map(_.key) must_== paths.filter(_.startsWith("b"))
    }

    "list items correctly with paging" in {
      val storage = putTestItems._1
      val items = await(storage.listFiles(max = 2))
      items.files.map(_.key).sorted must_== paths.take(2).sorted
      items.truncated must_== true

      val items2 = await(storage.listFiles(after = Some("baz"), max = 2))
      items2.files.map(_.key).sorted must_== paths.drop(2).sorted
      items2.truncated must_== false
    }

    "delete files with a given prefix" in {
      val storage = putTestItems._1
      val deleted = await(storage.deleteFilesWithPrefix("b"))
      deleted.sorted must_== Seq("bar", "baz")
      val items = await(storage.listFiles())
      items.files.size must_== 2
    }

    "do nothing on delete when no files match prefix" in {
      val storage = putTestItems._1
      val deleted = await(storage.deleteFilesWithPrefix("NOPE"))
      deleted.size must_== 0
    }

    "get item info with version ID" in {
      val storage = putTestItems._1
      val info: Option[(FileMeta, Map[String, String])] = await(storage.info("baz"))
      info.map(_._1) must beSome.which {fm: FileMeta => fm.versionId must beSome}

      await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))
      val info2: Option[(FileMeta, Map[String, String])] = await(storage.info("baz"))
      info2 must beSome.which { case (fm: FileMeta, _) =>
        fm.versionId must beSome
        val info3: Option[(FileMeta, Map[String, String])] = await(storage.info("baz", versionId = fm.versionId))
        info3.map(_._1) must beSome.which { fm2: FileMeta => fm2.versionId must_== fm.versionId }
      }
    }

    "get item data with version ID" in {
      val storage = putTestItems._1
      val info: Option[(FileMeta, Map[String, String])] = await(storage.info("baz"))
      info.map(_._1) must beSome.which { (fm: FileMeta) =>

        await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))

        val data1 = await(storage.get("baz", versionId = fm.versionId))
        data1.map(_._2) must beSome.which { src: Source[ByteString, _] =>
          val s = await(src.runFold(ByteString.empty)(_ ++ _))
          s.utf8String must_== "Hello, world"
        }
      }
    }

    "list versions" in {
      val storage = putTestItems._1
      await(storage.putBytes("baz", Source.single(ByteString("Bye, world"))))

      val versions = await(storage.listVersions("baz"))
      versions.files.size must beGreaterThan(2)
    }
  }
}
