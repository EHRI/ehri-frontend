package defines

object EventType extends Enumeration() {
  type Type = Value
  val
    creation,
    modification,
    deletion,
    link,
    annotation,
    setGlobalPermissions,
    setItemPermissions,
    setVisibility,
    addGroup,
    removeGroup,
    ingest = Value
}
