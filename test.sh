#!/bin/sh

export _JAVA_OPTIONS="-Xms64m -Xmx1024m -Xss2m -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -Dconfig.file=conf/test.conf"

play "test-only test.DocUnitViewsSpec" && \
play "test-only test.DocUnitPermissionsSpec" && \
play "test-only test.DocUnitLinkAnnotateSpec" && \
play "test-only test.RepositoryViewsSpec"  && \
play "test-only test.EntityViewsSpec"  && \
play "test-only test.DAOSpec"          && \
play "test-only test.APISpec"          && \
play "test-only test.json.JsonFormatSpec" && \
play "test-only test.CountryScopeIntegrationSpec" && \
play "test-only test.SupervisorWorkerIntegrationSpec"
