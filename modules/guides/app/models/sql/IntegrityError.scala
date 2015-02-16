package models.sql

import java.sql.SQLException

case class IntegrityError(e: SQLException) extends SQLException(e)
