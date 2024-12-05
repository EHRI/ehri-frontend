package services.harvesting

import org.apache.pekko.stream.connectors.xml.{Characters, EndDocument, ParseEvent, StartElement}
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.{Future, Promise}

object OaiPmhTokenParser {
  val parser: Flow[ParseEvent, ParseEvent, Future[TokenState]] = Flow.fromGraph(OaiPmhTokenParser())
}

case class OaiPmhTokenParser() extends GraphStageWithMaterializedValue[FlowShape[ParseEvent, ParseEvent], Future[TokenState]] {
  val in: Inlet[ParseEvent] = Inlet("ParseEvent.in")
  val out: Outlet[ParseEvent] = Outlet("ParseEvent.out")
  override def shape: FlowShape[ParseEvent, ParseEvent] = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[TokenState]) = {
    var inRT = false
    var rt: TokenState = Final
    val promise = Promise[TokenState]()

    val logic = new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)

          elem match {
            case StartElement(tag, _, _, _, _) if !inRT && tag == "resumptionToken" =>
              inRT = true
            case Characters(text) if inRT =>
              rt = Resume(text)
              inRT = false
            case EndDocument => promise.success(rt)
            case _ =>
          }

          push(out, elem)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }

    (logic, promise.future)
  }
}
