package services.harvesting

import akka.stream.alpakka.xml.{Characters, EndElement, ParseEvent, StartElement}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.concurrent.{Future, Promise}


object OaiPmhRecordParser {
  val parser: Flow[ParseEvent, ParseEvent, Future[String]] = Flow.fromGraph(OaiPmhRecordParser())
}

case class OaiPmhRecordParser() extends GraphStageWithMaterializedValue[FlowShape[ParseEvent, ParseEvent], Future[String]] {
  val in: Inlet[ParseEvent] = Inlet("ParseEvent.in")
  val out: Outlet[ParseEvent] = Outlet("ParseEvent.out")
  override def shape: FlowShape[ParseEvent, ParseEvent] = FlowShape.of(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[String]) = {
    var inMeta = false
    var inIdent = false
    val promise = Promise[String]()

    val logic = new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)

          elem match {
            case StartElement(tag, _, _, _, _) if !promise.isCompleted
                && !inIdent
                && tag == "identifier" =>
              inIdent = true
              pull(in)
            case StartElement(tag, _, _, _, _) if !inMeta && tag == "metadata" =>
              inMeta = true
              pull(in)
            case Characters(text) if inIdent =>
              promise.success(text)
              inIdent = false
              pull(in)
            case EndElement("metadata") if inMeta =>
              inMeta = false
              complete(out)
            case e if inMeta =>
              push(out, e)
            case _ => pull(in)
          }
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
