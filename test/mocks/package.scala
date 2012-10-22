package object mocks {

  val MOCK_EMAIL = "example@example.com"
  val MOCK_PROFILE_ID = "mike"
  val MOCK_USER = new models.sql.OpenIDUser(
      id=1L,
      email=MOCK_EMAIL,
      profile_id=MOCK_PROFILE_ID)
}