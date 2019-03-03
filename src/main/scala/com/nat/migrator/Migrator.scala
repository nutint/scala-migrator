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


  /** Implement this methods to see which is the current version of database"
    *
    * @return
    */
  def currentVersion: Option[String]


  /**
    * This method is for validate migration on the following criterias
    *   - Duplicate version number
    * @return
    */
  def validateMigrations: List[String] = {
    val identicalVersions =
      migrations
        .map(_.version)
        .toSet
        .size

    if(identicalVersions == migrations.length)
      Nil
    else {
      migrations
        .groupBy(_.version)
        .collect {
          case (ves, items) if items.length > 1 => ves
        }
        .toList
    }

  }

  def migrate(targetVersion: Option[String]) = {
    targetVersion match {
      case None =>
      case Some(targetVersion) =>
    }
  }

//  def doMigration(targetVersion: String) = {
//    migrations.find(_.version == targetVersion)
//      .
//  }
}

