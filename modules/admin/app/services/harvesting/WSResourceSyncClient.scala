package services.harvesting

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import javax.inject.Inject
import models._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Node, NodeSeq}


case class WSResourceSyncClient @Inject ()(ws: WSClient)(implicit mat: Materializer) extends ResourceSyncClient {

  private implicit val ec: ExecutionContext = mat.executionContext

  private def parseAttr(nodes: NodeSeq, key: String): Option[String] =
    nodes.headOption.flatMap(n => parseAttr(n, key))

  private def parseAttr(node: Node, key: String): Option[String] =
    node.attribute(key).flatMap(_.headOption.map(_.text))

  private def readUrlSet(url: String): Future[Seq[ResourceSyncItem]] = {
    ws.url(url).get().map { r =>
      (r.xml \ "url").flatMap { urlNode =>
        val md = urlNode \ "md"
        val lastMod = urlNode \ "lastmod"
        val capability = parseAttr(md, "capability")

        (urlNode \ "loc").map { locNode =>
          val loc = locNode.text
          capability match {
            case Some("resourcelist") => ResourceList(loc)
            case Some("capabilitylist") => CapabilityList(loc)
            case _ => FileLink(
              loc,
              contentType = parseAttr(md, "type"),
              hash = parseAttr(md, "hash"),
              length = parseAttr(md, "length").map(_.toLong),
              updated = lastMod.headOption.map(n => java.time.Instant.parse(n.text))
            )
          }
        }
      }
    }
  }

  override def list(config: ResourceSyncConfig): Future[Seq[FileLink]] = {
    readUrlSet(config.url).flatMap { list =>

      val s: Source[FileLink, NotUsed] = Source(list.toList)
        .mapAsync(1) { urlItem => readUrlSet(urlItem.loc) }
        .flatMapConcat(s => Source(s.toList))
        .collect { case link: FileLink => link }

      s.runWith(Sink.seq)
    }
  }
}
