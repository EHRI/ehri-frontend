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
import services.storage.FileStorage
import services.transformation.XmlTransformer

import java.util.UUID

class XmlConverterSpec extends IntegrationTestRunner {

  import XmlConverter._

  private implicit val userOpt: Option[UserProfile] = Some(adminUserProfile)
  private def damStorage(implicit app: Application) = app.injector.instanceOf(
    BindingKey(classOf[FileStorage], Some(QualifierInstance(Names.named("dam")))))
  private val transformer: XmlTransformer = new XmlTransformer {
    override def transform(mapType: DataTransformation.TransformationType.Value, map: String) =
      Flow[ByteString].map(identity)
    override def transform(mappings: Seq[(DataTransformation.TransformationType.Value, String)]) =
      Flow[ByteString].map(identity)
  }


  "XML converter" should {
    "send the right messages when there's nothing to do" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))
        val converter = system.actorOf(Props(XmlConverter(job, transformer, damStorage)))
        converter ! Initial
        expectMsg(Starting)
        expectMsg(Counting)
        expectMsg(Counted(0))
        expectMsgClass(classOf[Completed])
    }

    "copy files when transformation is a no-op" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

        await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
        val converter = system.actorOf(Props(XmlConverter(job, transformer, damStorage)))
        converter ! Initial
        expectMsg(Starting)
        expectMsg(Counting)
        expectMsg(Counted(1))
        expectMsg(DoneFile("+ file.xml"))
        expectMsgClass(classOf[Completed])
    }

    "not re-copy when a prior identical transformation has been run" in new ITestAppWithAkka {
        val ds = UUID.randomUUID().toString
        val job = XmlConvertJob("r1", ds, ds,
          XmlConvertData(Seq.empty, s"ingest-data/r1/$ds/input/", s"ingest-data/r1/$ds/output/"))

        await(damStorage.putBytes(job.data.inPrefix + "file.xml", Source.single(ByteString("<xml>test</xml>"))))
        val converter = system.actorOf(Props(XmlConverter(job, transformer, damStorage)))
        converter ! Initial
        val completed: Completed = fishForSpecificMessage() { case c: Completed => c }
        completed.done must_== 1
        completed.fresh must_== 1

        val converter2 = system.actorOf(Props(XmlConverter(job, transformer, damStorage)))
        converter2 ! Initial
        val completed2: Completed = fishForSpecificMessage() { case c: Completed => c }
        completed2.done must_== 1
        completed2.fresh must_== 0
    }
  }
}
