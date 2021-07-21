package actors.transformation

import actors.transformation.XmlConverterManager.{XmlConvertData, XmlConvertJob}
import akka.actor.Props
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.google.inject.name.Names
import helpers.IntegrationTestRunner
import mockdata.adminUserProfile
import models.{DataTransformation, UserProfile}
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.JsObject
import services.storage.FileStorage
import services.transformation.XmlTransformer

import java.util.UUID
import scala.concurrent.Future

class XmlConverterSpec extends IntegrationTestRunner {

  import XmlConverter._

  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)

  private def damStorage(implicit app: Application) = app.injector.instanceOf(
    BindingKey(classOf[FileStorage], Some(QualifierInstance(Names.named("dam")))))

  private val noOpTransformer: XmlTransformer =
    (_: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]) =>
      Flow[ByteString].map(identity)

  private val errorTransformer: XmlTransformer =
    (_: Seq[(DataTransformation.TransformationType.Value, String, JsObject)]) =>
      Flow[ByteString].mapAsync(1)(_ => Future.failed(XmlConvertError("Error during transformation")))


  "XML converter" should {
    "send the right messages when there's nothing to do" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))
        val converter = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
        converter ! Initial
        expectMsg(Starting)
        expectMsg(Counting)
        expectMsg(Counted(0))
        expectMsgClass(classOf[Completed])
    }

    "transcode input charset to UTF-8" in new ITestAppWithAkka {
      val ds = UUID.randomUUID().toString
      val job = XmlConvertJob("r1", ds, ds,
        XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

      val inBytes = ByteString("<xml>Über</xml>".getBytes("iso-8859-1"))
      val outBytes = ByteString("<xml>Über</xml>".getBytes("UTF-8"))
      // Sanity check
      inBytes must_!= outBytes

      // Upload the file with the specific non-UTF-8 charset here
      await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(inBytes), contentType = Some("text/xml; charset=iso-8859-1")))
      val converter = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
      converter ! Initial
      expectMsg(Starting)
      expectMsg(Counting)
      expectMsg(Counted(1))
      expectMsg(DoneFile("+ file.xml"))
      expectMsgClass(classOf[Completed])
      await(damStorage.get(job.data.outPrefix + "file.xml")).map(_._2) must beSome.which {(s: Source[ByteString, _]) =>
        await(s.runFold(ByteString.empty)(_ ++ _)) must_== outBytes
      }
    }

    "copy files when transformation is a no-op" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

        await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
        val converter = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
        converter ! Initial
        expectMsg(Starting)
        expectMsg(Counting)
        expectMsg(Counted(1))
        expectMsg(DoneFile("+ file.xml"))
        expectMsgClass(classOf[Completed])
    }

    "handle conversion errors without halting" in new ITestAppWithAkka {
      val ds = UUID.randomUUID().toString
      val job = XmlConvertJob("r1", ds, ds,
        XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

      await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
      val converter = system.actorOf(Props(XmlConverter(job, errorTransformer, damStorage)))
      converter ! Initial
      expectMsg(Starting)
      expectMsg(Counting)
      expectMsg(Counted(1))
      expectMsg(Error("file.xml", XmlConvertError("Error during transformation")))
      expectMsgClass(classOf[Completed])
    }


    "handle storage errors by halting" in new ITestAppWithAkka {
      val noKey = "nope.xml"
      val ds = UUID.randomUUID().toString
      val job = XmlConvertJob("r1", ds, ds,
        XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/",
          only = Some(noKey))) //  NB: the file's not in storage

      await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
      val converter = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
      converter ! Initial
      expectMsgAllOf()
      expectMsg(Starting)
      expectMsg(Counting)
      expectMsg(Counted(1))
      expectMsg(Error(noKey, XmlConvertException(s"Missing key: $noKey")))
      expectNoMessage()
    }

    "not re-copy when a prior identical transformation has been run" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

        await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
        val converter = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
        converter ! Initial
        val completed: Completed = fishForSpecificMessage() { case c: Completed => c }
        completed.done must_== 1
        completed.fresh must_== 1

        val converter2 = system.actorOf(Props(XmlConverter(job, noOpTransformer, damStorage)))
        converter2 ! Initial
        val completed2: Completed = fishForSpecificMessage() { case c: Completed => c }
        completed2.done must_== 1
        completed2.fresh must_== 0
    }
  }
}
