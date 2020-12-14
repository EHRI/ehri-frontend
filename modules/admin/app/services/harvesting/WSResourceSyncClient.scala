package services.harvesting

import java.util.regex.Pattern

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

  private def readUrlSet(url: String, filter: String => Boolean): Future[Seq[ResourceSyncItem]] = {
    ws.url(url).get().map { r =>
      (r.xml \ "url").flatMap { urlNode =>
        val md = urlNode \ "md"
        val lastMod = urlNode \ "lastmod"
        val capability = parseAttr(md, "capability")

        (urlNode \ "loc").flatMap { locNode =>
          val loc = locNode.text
          capability match {
            case Some("resourcelist") => Some(ResourceList(loc))
            case Some("capabilitylist") => Some(CapabilityList(loc))
            case None if filter(loc) => Some(FileLink(
              loc,
              contentType = parseAttr(md, "type"),
              hash = parseAttr(md, "hash"),
              length = parseAttr(md, "length").map(_.toLong),
              updated = lastMod.headOption.map(n => java.time.Instant.parse(n.text))
            ))
            case _ => None
          }
        }
      }
    }
  }

  override def list(config: ResourceSyncConfig): Future[Seq[FileLink]] = {

    val test: String => Boolean = config.filter.fold(ifEmpty = (_:String) => true)(
      s => t => Pattern.compile(s).matcher(t).find())

    readUrlSet(config.url, test).flatMap { list =>

      val s: Source[FileLink, NotUsed] = Source(list.toList)
        .collect { case rl: ResourceList => rl }
        .mapAsync(1) { urlItem => readUrlSet(urlItem.loc, test) }
        .flatMapConcat(s => Source(s.toList))
        .collect { case link: FileLink => link }

      s.runWith(Sink.seq)
    }
  }
}
