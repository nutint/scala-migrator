package com.nat.migrator

/**
  * This trait is responsible for control process of all
  */

trait Migrator {

  /**
    * This should contains newest version at the head of the list, and oldest version should be the tail of the list
    * @return
    */
  def migrations: List[Migration]

  def validateMigrations: Option[String] = {
    migrations
      .map(_.version)
      .toSet
      .size match {
      case migrations.length => None
      case _ => Some("There are some duplicated version in migrations, please make sure the version are identical")
    }
  }

  def migrate(targetVersion: Option[String]) = {
    targetVersion match {
      case None =>
      case Some(targetVersion) =>
    }
  }

  def doMigration(targetVersion: String) = {
    migrations.find()
  }
}

