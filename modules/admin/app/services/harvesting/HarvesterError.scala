package services.harvesting

import play.api.i18n.Messages

trait HarvesterError extends RuntimeException {
  def errorMessage(implicit message: Messages): String
}
