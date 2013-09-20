package defines

object EventType extends Enumeration() {
  type Type = Value
  val
    creation,
    createDependent,
    modification,
    modifyDependent,
    deletion,
    deleteDependent,
    link,
    annotation,
    setGlobalPermissions,
    setItemPermissions,
    setVisibility,
    addGroup,
    removeGroup,
    ingest = Value

  implicit val format = defines.EnumUtils.enumFormat(this)
}
