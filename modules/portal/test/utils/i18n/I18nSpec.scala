package utils.i18n

import play.api.{Configuration, Environment}
import play.api.i18n.{Lang, DefaultLangs, DefaultMessagesApi}
import play.api.test.PlaySpecification

class I18nSpec extends PlaySpecification {

  val conf = Configuration.reference ++ Configuration.from(Map("play.i18n.langs" -> Seq("en", "fr")))
  val messagesApi = new DefaultMessagesApi(Environment.simple(), conf, new DefaultLangs(conf))

  "i18n" should {
    "support the right langs" in {
      messagesApi.translate("welcome", Seq.empty)(Lang("en")) must beSome.which { t =>
        t must equalTo("Welcome to EHRI")
      }
      messagesApi.translate("welcome", Seq.empty)(Lang("fr")) must beSome.which { t =>
        t must equalTo("Bienvenue sur l'EHRI")
      }
    }
  }
}
