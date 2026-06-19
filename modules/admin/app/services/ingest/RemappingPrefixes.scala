package services.ingest

import com.google.inject.ImplementedBy
import models.EntityType

import javax.inject.Inject

@ImplementedBy(classOf[RemappingPrefixesProvider])
trait RemappingPrefixes {
  def prefixes(et: EntityType.Value): Seq[String]
}

case class RemappingPrefixesProvider @Inject()() extends RemappingPrefixes {
  override def prefixes(et: EntityType.Value): Seq[String] = {
    val placeholder = "TO___REMOVE"
    val public = views.Helpers.linkToOpt(et, placeholder).map(_.url.stripSuffix(placeholder)).toSeq
    val admin = views.admin.Helpers.linkToOpt(et, placeholder).map(_.url.stripSuffix(placeholder)).toSeq
    public ++ admin
  }
}
