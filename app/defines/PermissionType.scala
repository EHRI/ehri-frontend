package defines

object PermissionType extends Enumeration {
	type Type = Value
	val Create = Value("create")
	val Update = Value("update")
	val Delete = Value("delete")
	val Owner = Value("owner")
	val Grant = Value("grant")
	val Annotate = Value("annotate")
}