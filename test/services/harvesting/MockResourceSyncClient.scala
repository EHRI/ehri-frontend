package services.harvesting
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import javax.inject.Inject
import models.{FileLink, ResourceSyncConfig}

import scala.concurrent.{ExecutionContext, Future}

case class MockResourceSyncClient @Inject()()(implicit ec: ExecutionContext) extends ResourceSyncClient {
  override def list(config: ResourceSyncConfig): Future[Seq[FileLink]] = Future {
    Thread.sleep(100)
    Seq(
      FileLink("http://www.example.com/test1.xml"),
      FileLink("http://www.example.com/test2.xml"),
      FileLink("http://www.example.com/test3.xml")
    )
  }

  override def get(config: ResourceSyncConfig, link: FileLink): Source[ByteString, _] =
    Source.single(ByteString.fromString("""<ead></ead>"""))
}
