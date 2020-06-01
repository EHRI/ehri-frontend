package services.ingest

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.xml.ParseEvent
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.util.ByteString
import javax.inject.Inject
import models.admin.{OaiPmhConfig, OaiPmhIdentity}
import org.w3c.dom.Element
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

sealed trait TokenState {
  def asOption: Option[String] = this match {
    case Resume(token) => Some(token)
    case _ => None
  }
}
case object Initial extends TokenState
case class Resume(token: String) extends TokenState
case object Final extends TokenState


case class OaiPmhClientService @Inject()(ws: WSClient)(implicit ec: ExecutionContext, mat: Materializer) extends OaiPmhClient {

  private def stream[T](endpoint: OaiPmhConfig, params: Seq[(String, String)], transform: Flow[ParseEvent, T, NotUsed]): Source[T, _] = {

    def stage(tokenF: Future[TokenState]): Future[(Future[TokenState], Source[T, NotUsed])] = {
      tokenF.flatMap { token =>
        val allParams = params ++ Seq("metadataPrefix" -> endpoint.format) ++ token.asOption.map(t => "resumptionToken" -> t)
        ws.url(endpoint.url)
          .withQueryStringParameters(allParams: _*)
          .stream().map { response =>

          response.bodyAsSource
            .via(XmlParsing.parser)
            .viaMat(OaiPmhTokenParser.parser)(Keep.right)
            .viaMat(transform)(Keep.left)
            .preMaterialize()
        }
      }
    }

    Source.unfoldAsync[Future[TokenState], Source[T, _]](Future.successful(Initial)) { ft =>
      ft.flatMap {
        case Final =>  Future.successful(None)
        case _ => stage(ft).map(next => Some(next))
      }
    }.flatMapConcat(f => f)
  }

  override def identify(endpoint: OaiPmhConfig): Future[OaiPmhIdentity] = {
    ws.url(endpoint.url)
      .withQueryStringParameters("verb" -> "Identify")
      .get()
      .map { _.xml \ "Identify" }
      .map { nodes =>
        OaiPmhIdentity(
          (nodes \ "repositoryName").text,
          (nodes \ "baseURL").text,
          (nodes \ "protocolVersion").text
        )
      }
  }

  override def listSets(endpoint: OaiPmhConfig): Source[(String, String), _] = {
    val t: Flow[ParseEvent, (String, String), NotUsed] = XmlParsing
      .subtree(collection.immutable.Seq("OAI-PMH", "ListSets", "set"))
      .map { elem =>
        val spec = elem.getElementsByTagName("setSpec").item(0)
        val name = elem.getElementsByTagName("setName").item(0)
        Option(spec) -> Option(name)
      }.collect {
      case (Some(specElem), Some(nameElem)) =>
        specElem.getTextContent -> nameElem.getTextContent
    }
    stream(endpoint, Seq("verb" -> "ListSets"), t)
  }

  override def listIdentifiers(endpoint: OaiPmhConfig): Source[String, _] = {
    val t: Flow[ParseEvent, String, NotUsed] = XmlParsing
      .subtree(collection.immutable.Seq("OAI-PMH", "ListIdentifiers", "header"))
      .map { elem =>
        elem.getElementsByTagName("identifier").item(0).getTextContent
      }
    stream(endpoint, Seq("verb" -> "ListIdentifiers"), t)
  }

  override def listRecords(endpoint: OaiPmhConfig): Source[Element, _] = {
    val t: Flow[ParseEvent, Element, NotUsed] = XmlParsing
      .subtree(collection.immutable.Seq("OAI-PMH", "ListRecords", "record"))
    stream(endpoint, Seq("verb" -> "ListRecords"), t)
  }

  override def getRecord(endpoint: OaiPmhConfig, id: String): Source[ByteString, _] = {
    val t: Flow[ParseEvent, ByteString, NotUsed] = XmlParsing
      .subslice(collection.immutable.Seq("OAI-PMH", "GetRecord", "record", "metadata"))
        .via(XmlWriting.writer)
    stream(endpoint, Seq("verb" -> "GetRecord", "identifier" -> id), t)
  }

  override def streamRecords(endpoint: OaiPmhConfig): Source[ParseEvent, _] = {
    stream(endpoint, Seq("verb" -> "ListRecords"), Flow[ParseEvent].map(f => f))
  }
}
