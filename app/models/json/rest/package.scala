package models.json

/**
 * User: michaelb
 * 
 * Model formats for reading/writing JSON to/from the
 * REST server.
 */
package object rest {

  implicit val accessPointFormat = models.json.AccessPointFormat.accessPointFormat
  implicit val addressFormat = models.json.AddressFormat.addressFormat
  implicit val annotationFormat = models.json.AnnotationFormat.annotationFormat
  implicit val authoritativeSetFormat = models.json.AuthoritativeSetFormat.authoritativeSetFormat
  implicit val conceptFormat = models.json.ConceptFormat.conceptFormat
  implicit val conceptDescriptionFormat = models.json.ConceptDescriptionFormat.conceptDescriptionFormat
  implicit val countryFormat = models.json.CountryFormat.countryFormat
  implicit val datePeriodFormat = models.json.DatePeriodFormat.datePeriodFormat
  implicit val documentaryUnitFormat = models.json.DocumentaryUnitFormat.documentaryUnitFormat
  implicit val groupFormat = models.json.GroupFormat.groupFormat
  implicit val historicalAgentFormat = models.json.HistoricalAgentFormat.historicalAgentFormat
  implicit val isaarFormat = models.json.IsaarFormat.isaarFormat
  implicit val isadGFormat = models.json.IsadGFormat.isadGFormat
  implicit val isdiahFormat = models.json.IsdiahFormat.isdiahFormat
  implicit val linkFormat = models.json.LinkFormat.linkFormat
  implicit val repositoryFormat = models.json.RepositoryFormat.repositoryFormat
  implicit val userProfileFormat = models.json.UserProfileFormat.userProfileFormat
  implicit val vocabularyFormat = models.json.VocabularyFormat.vocabularyFormat  
}
