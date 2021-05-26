package auth.sso

import com.google.common.hash.Hashing

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

case class DiscourseSSOError(msg: String) extends Exception(msg)

case class DiscourseSSO(secret: String) {
  private val utf8 = StandardCharsets.UTF_8

  // NB: we use a 60-char line length encoder here to match the behaviour of the
  // example Ruby version, but it should not affect behaviour otherwise.
  private val base64encoder = Base64.getMimeEncoder(60, Array('\n'))
  private val base64decoder = Base64.getMimeDecoder

  /**
    * Encode SSO response data and return an URL-encoded payload
    * string and a signature.
    *
    * @param data a set of query string pairs, typically including a nonce
    * @return a non-URL-encoded tuple consisting of an encoded payload string and a signed
    *         signature value
    */
  def encode(data: Seq[(String, String)]): (String, String) = {
    val payload = utils.http.joinQueryString(data)
    // FIXME: Ruby Base64 adds a trailing line break.
    val base64Payload = base64encoder.encodeToString(payload.getBytes(utf8)) + "\n"
    val newSig = Hashing.hmacSha256(secret.getBytes(utf8)).hashString(base64Payload, utf8).toString
    base64Payload -> newSig
  }

  /**
    * Decode an SSO payload and check it against the supplied signature.
    *
    * @param payload an non-URL-encoded payload string containing the SSO nonce
    * @param sig a signed signature value
    * @return a sequence of key/value pairs.
    */
  @throws[DiscourseSSOError]
  def decode(payload: String, sig: String): Seq[(String, String)] = {
    val check = Hashing.hmacSha256(secret.getBytes(utf8)).hashString(payload, utf8).toString
    if (check != sig) {
      throw DiscourseSSOError("Mismatched signature")
    }

    val urlDecoded = URLDecoder.decode(payload, utf8.name())
    val decoded: String = new String(base64decoder.decode(urlDecoded.getBytes(utf8)))
    utils.http.parseQueryString(decoded)
  }
}
