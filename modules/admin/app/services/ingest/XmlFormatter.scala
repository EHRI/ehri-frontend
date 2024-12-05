package services.ingest

import java.util

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.connectors.xml.{Characters, EndElement, ParseEvent, StartElement}
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import org.apache.pekko.stream.{Attributes, FlowShape, Inlet, Outlet}


object XmlFormatter {
  def format(indent: Int = 4): Flow[ParseEvent, ParseEvent, NotUsed] = Flow.fromGraph(XmlFormatter(indent))

  val format: Flow[ParseEvent, ParseEvent, NotUsed] = format()
}

private sealed trait SeenState
private case object SeenNone extends SeenState
private case object SeenData extends SeenState
private case object SeenElem extends SeenState

protected[ingest] case class XmlFormatter(indent: Int) extends GraphStage[FlowShape[ParseEvent, ParseEvent]] {
  val in: Inlet[ParseEvent] = Inlet("XMLFormatter.in")
  val out: Outlet[ParseEvent] = Outlet("XMLFormatter.out")
  override def shape: FlowShape[ParseEvent, ParseEvent] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    var state: SeenState = SeenNone
    val stateStack = new util.Stack[SeenState]()
    var depth = 0
    var init = false

    new GraphStageLogic(shape) {
      @inline private def doIndent(pre: String = "", post: String = ""): Characters =
        Characters(pre + (" " * indent * depth) + post)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)

          elem match {
            // First element in document...
            case e: StartElement if !init =>
              init = true
              emitMultiple(out, List(Characters("\n"), e))
              stateStack.push(SeenElem)
              depth += 1

            // Start element
            case e: StartElement =>
              stateStack.push(SeenElem)
              state = SeenNone
              if (depth > 0) emitMultiple(out, List(doIndent("\n"), e))
              else push(out, e)
              depth += 1

            // Empty elements
            case e: EndElement if state == SeenNone =>
              depth -= 1
              state = SeenElem
              push(out, e)

            // End element
            case e: EndElement =>
              depth -= 1
              if (state == SeenElem) {
                if (depth > 0) emitMultiple(out, List(doIndent("\n"), e))
                else emitMultiple(out, List(Characters("\n"), e))
              } else push(out, e)
              state = stateStack.pop()

            // Ignore whitespace only
            case Characters(t) if t.forall(_.isWhitespace) =>
              pull(in)

            // Output characters
            case e: Characters =>
              state = SeenData
              push(out, e)

            case e =>
              push(out, e)
          }
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
  }
}
