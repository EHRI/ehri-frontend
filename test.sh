#!/bin/sh

play "test-only test.DocUnitViewsSpec" && \
play "test-only test.EntityViewsSpec"  && \
play "test-only test.DAOSpec"          && \
play "test-only test.APISpec"          && \
play "test-only test.json.JsonFormatSpec" && \
play "test-only test.PermissionsIntegrationSpec"
