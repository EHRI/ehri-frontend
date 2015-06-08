package modules

import com.google.inject.{Provider, AbstractModule}
import play.api.i18n.MessagesApi

import scala.concurrent.ExecutionContext

class PlayModule extends AbstractModule {
  def configure() = {
    bind(classOf[ExecutionContext]).toProvider(new Provider[ExecutionContext] {
      def get() = play.api.libs.concurrent.Execution.defaultContext
    })
  }
}