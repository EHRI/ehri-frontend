package object controllers {

  private val editViewMappings = Map(
    defines.EntityType.DocumentaryUnit -> views.html.documentaryUnit.edit)

  def editViewFor(e: defines.EntityType.Type) = editViewMappings.getOrElse(e,
    sys.error("No edit view found entity: " + e.toString()))

}