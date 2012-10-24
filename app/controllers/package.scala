package object controllers {

  private val editViewMappings = Map(
    models.EntityTypes.DocumentaryUnit -> views.html.documentaryUnit.edit)

  def editViewFor(e: models.EntityTypes.Type) = editViewMappings.getOrElse(e,
    sys.error("No edit view found entity: " + e.toString()))

}