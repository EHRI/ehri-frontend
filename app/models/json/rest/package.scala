package models.json

/**
 * User: michaelb
 * 
 * Model formats for reading/writing JSON to/from the
 * REST server.
 */
package object rest {

  implicit val accessPointFormat = models.json.AccessPointFormat.restFormat
  implicit val addressFormat = models.json.AddressFormat.restFormat
  implicit val annotationFormat = models.json.AnnotationFormat.restFormat
  implicit val authoritativeSetFormat = models.json.AuthoritativeSetFormat.restFormat
  implicit val conceptFormat = models.json.ConceptFormat.restFormat
  implicit val conceptDescriptionFormat = models.json.ConceptDescriptionFormat.restFormat
  implicit val countryFormat = models.json.CountryFormat.restFormat
  implicit val datePeriodFormat = models.json.DatePeriodFormat.restFormat
  implicit val documentaryUnitFormat = models.json.DocumentaryUnitFormat.restFormat
  implicit val groupFormat = models.json.GroupFormat.restFormat
  implicit val historicalAgentFormat = models.json.HistoricalAgentFormat.restFormat
  implicit val isaarFormat = models.json.IsaarFormat.restFormat
  implicit val isadGFormat = models.json.IsadGFormat.restFormat
  implicit val isdiahFormat = models.json.IsdiahFormat.restFormat
  implicit val linkFormat = models.json.LinkFormat.restFormat
  implicit val repositoryFormat = models.json.RepositoryFormat.restFormat
  implicit val userProfileFormat = models.json.UserProfileFormat.restFormat
  implicit val vocabularyFormat = models.json.VocabularyFormat.restFormat
  implicit val permissionGrantReads = models.json.PermissionGrantFormat.permissionGrantReads
}
