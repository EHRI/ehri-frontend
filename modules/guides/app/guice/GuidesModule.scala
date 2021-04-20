package guice

import com.google.inject.AbstractModule
import models.{GuideService, SqlGuideService}

class GuidesModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[GuideService]).to(classOf[SqlGuideService])
  }
}
