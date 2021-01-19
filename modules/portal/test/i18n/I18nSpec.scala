package i18n

import play.api.http.HttpConfiguration
import play.api.{Configuration, Environment}
import play.api.i18n.{DefaultLangs, DefaultMessagesApiProvider, Lang}
import play.api.test.PlaySpecification

class I18nSpec extends PlaySpecification {

  private val conf = Configuration.reference
  val messages = new DefaultMessagesApiProvider(Environment.simple(), conf, new DefaultLangs(Seq(Lang("en"), Lang("fr"))), HttpConfiguration())

  "i18n" should {
    "support the right langs" in {
      messages.get.translate("welcome", Seq.empty)(Lang("en")) must beSome.which { t =>
        t must equalTo("Welcome to the EHRI Portal")
      }
      messages.get.translate("welcome", Seq.empty)(Lang("fr")) must beSome.which { t =>
        t must equalTo("Bienvenue sur le portail de l'EHRI")
      }
    }
  }
}
