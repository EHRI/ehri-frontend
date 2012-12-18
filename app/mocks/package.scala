import play.api.Play.current

package object mocks {

  val MOCK_EMAIL = "example@example.com"
    
  // Profile ID must be passed in configuration
  def MOCK_USER = new models.sql.OpenIDUser(
      id=1L,
      email=MOCK_EMAIL,
      profile_id="mike")
}