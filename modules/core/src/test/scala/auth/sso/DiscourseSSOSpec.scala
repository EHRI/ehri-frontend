package auth.sso

import play.api.test.PlaySpecification

/**
  * Tests and literal values taken from Discourse SSO example:
  *
  * https://meta.discourse.org/t/discourseconnect-official-single-sign-on-for-discourse-sso/13045
  */
class DiscourseSSOSpec extends PlaySpecification {
  val SSO = DiscourseSSO("d836444a9e4084d5b224a60c208dce14")
  val NONCE = "cb68251eefb5211e58c00ff1395f0c0b"
  val PAYLOAD = "bm9uY2U9Y2I2ODI1MWVlZmI1MjExZTU4YzAwZmYxMzk1ZjBjMGI=\n"
  val SIG = "2828aa29899722b35a2f191d34ef9b3ce695e0e6eeec47deb46d588d70c7cb56"

  "Discourse SSO" should {
    "encode data correctly" in {
      val (payload, sig) = SSO.encode(Seq("nonce" -> NONCE))
      payload must_== PAYLOAD
      sig must_== SIG
    }

    "decode data correctly" in {
      val data = SSO.decode(PAYLOAD, SIG)
      data must_== Seq("nonce" -> NONCE)
    }
  }
}
