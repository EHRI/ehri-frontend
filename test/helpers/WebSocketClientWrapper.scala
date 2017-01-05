package helpers

import java.net.URI

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake

import scala.collection.mutable.ListBuffer

case class WebSocketClientWrapper(uri: String, headers: Map[String, String] = Map.empty) {

  val messages: ListBuffer[String] = ListBuffer[String]()

  import scala.collection.JavaConversions._
  val client = new WebSocketClient(URI.create(uri), new Draft_17(), headers, 0) {
    def onError(p1: Exception) = throw p1

    def onMessage(message: String): Unit = messages += message

    def onClose(code: Int, reason: String, remote: Boolean) {}

    def onOpen(handshakedata: ServerHandshake) {}
  }
}
