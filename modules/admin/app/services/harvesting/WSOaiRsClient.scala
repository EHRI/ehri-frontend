package services.harvesting

import java.net.URI

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import javax.inject.Inject
import models._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Node, NodeSeq}


case class WSOaiRsClient @Inject ()(ws: WSClient)(implicit mat: Materializer) extends OaiRsClient {

  private implicit val ec: ExecutionContext = mat.executionContext

  private def parseAttr(nodes: NodeSeq, key: String): Option[String] =
    nodes.headOption.flatMap(n => parseAttr(n, key))

  private def parseAttr(node: Node, key: String): Option[String] =
    node.attribute(key).flatMap(_.headOption.map(_.text))

  private def readUrlSet(url: URI): Future[Seq[OaiRsResource]] = {
    ws.url(url.toString).get().map { r =>
      (r.xml \ "url").flatMap { urlNode =>
        val md = urlNode \ "md"
        val lastMod = urlNode \ "lastmod"
        val capability = parseAttr(md, "capability")

        (urlNode \ "loc").map { locNode =>
          val loc = URI.create(locNode.text)
          capability match {
            case Some("resourcelist") => ResourceList(loc)
            case Some("capabilitylist") => CapabilityList(loc)
            case _ => ResourceLink(
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

  override def list(config: OaiRsConfig): Future[Seq[ResourceLink]] = {
    readUrlSet(config.changeList).flatMap { list =>

      val s: Source[ResourceLink, NotUsed] = Source(list.toList)
        .mapAsync(1) { urlItem => readUrlSet(urlItem.loc) }
        .flatMapConcat(s => Source(s.toList))
        .collect { case link: ResourceLink => link }

      s.runWith(Sink.seq)
    }
  }

  override def audit(config: OaiRsConfig): Future[Boolean] = ???
}
