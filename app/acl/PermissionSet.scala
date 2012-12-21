package acl


trait PermissionSet {
  /**
   * HACK! Until we can get bitmasks working (Scala enums apparently
   * can't contain additional fields) hack the expansion of the owner
   * permission to CREATE/UPDATE/DELETE/ANNOTATE (don't worry, the
   * server will enforce things properly.)
   */
  import defines.PermissionType._
  
  def expandOwnerPerms(perms: List[Value]) = {
    val ownerExpands = List(Create, Update, Delete, Annotate)
    if (perms.contains(Owner))
      (perms.toSet ++ ownerExpands).toList
    else
      perms
  }
}