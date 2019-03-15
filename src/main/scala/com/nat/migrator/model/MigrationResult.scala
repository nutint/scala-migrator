package com.nat.migrator.model

sealed trait MigrationResult
case object MigrationResultSuccess extends MigrationResult
case class MigrationResultFailed(reason: String) extends MigrationResult
