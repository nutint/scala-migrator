package com.nat.migrator

import com.nat.migrator.model._

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
  def loadCurrentVersionNumber: Option[String]

  /**
    * Validate instance's migration object
    * @return
    */
  def validateMigrations: List[String] = validateMigrations(migrations)


  /**
    * This method is for validate migration on the following criterias
    *   - Duplicate version number
    * @return
    */
  def validateMigrations(validatingMigration: List[Migration]): List[String] = {
    val identicalVersions =
      validatingMigration
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

  /**
    * Automatically detect migration direction,
    *   - migration up if target version index is below current version
    *   - migration down if target version index is current target version
    *   - migration none if target version is the same as current version
    * @param targetVersion
    * @param currentVersion
    * @return
    */
  def findMigrationDirection(targetVersion: String, currentVersion: String, migrations: List[Migration]): Either[List[String],MigrationDirection] = {
    validateMigrations(migrations) match {
      case Nil => {
        val migrationVersions = migrations.map(_.version)
        val targetVersionIndex = migrationVersions.indexOf(targetVersion)
        val currentVersionIndex = migrationVersions.indexOf(currentVersion)
        (targetVersionIndex, currentVersionIndex) match {
          case (-1, -1) | (-1, _) | (_, -1) => Left(List("Unable to find target version, or current version in migration list"))
          case (tgv, crv) if tgv < crv => Right(MigrationDirectionUp)
          case (tgv, crv) if tgv > crv => Right(MigrationDirectionDown)
          case _ => Right(MigrationDirectionSame)
        }
      }
      case errors => Left(errors)
    }
  }

  /**
    * Find portion of migration list only interested version
    * @param allMigrations
    * @param targetVersion
    * @param currentVersion
    * @return
    */
  def findInterestedMigrations(allMigrations: List[Migration], targetVersion: String, currentVersion: String): Either[String, List[Migration]] = {
    val onlyVersions = allMigrations.map(_.version)
    (onlyVersions.indexOf(targetVersion), onlyVersions.indexOf(currentVersion)) match {
      case (-1, -1) => Left(s"Both $targetVersion, and $currentVersion are not exists")
      case (-1, _) => Left(s"$targetVersion is not exists")
      case (_, -1) => Left(s"$currentVersion is not exists")
      case (tgi, cri) if tgi > cri => Right(allMigrations.slice(cri, tgi + 1))
      case (tgi, cri) => Right(allMigrations.slice(tgi, cri + 1))
    }
  }

  /**
    * Migrate with the following logic
    * - If no version provide => detect current version, and migrate to the latest version
    * - If version is provide
    *   - the current version, and target version is the same => return fail with no change
    *   - the current version is lower than target version => do migration up
    *   - the current version is higher than target version => do migration down
    * - If unable to load current version => do upgrade to current version
    * @param targetVersion
    * @return
    */
  def migrate(targetVersion: Option[String]): MigrationResult = {
    (migrations, targetVersion) match {
      case (Nil, _ ) => MigrationResultFailed("Empty migration")
      case (head :: _, None) => migrate(Some(head.version))
      case (_, Some(tgv)) =>
        loadCurrentVersionNumber
          .map { currentVer =>
            (currentVer, findMigrationDirection(tgv, currentVer, migrations))
          }
          .map {
            case (_, Left(errors)) => MigrationResultFailed(s"Migration collection does not pass validation $errors")
            case (currentVer, Right(direction)) =>
              findInterestedMigrations(migrations, tgv, currentVer)
                .map { interestedMigration =>
                  direction match {
                    case MigrationDirectionSame => MigrationResultFailed(s"target version, and current version is the same (currentVersion = $currentVer)")
                    case MigrationDirectionUp => runSortedMigration(interestedMigration.reverse.tail, MigrationDirectionUp)
                    case MigrationDirectionDown => runSortedMigration(interestedMigration.tail, MigrationDirectionDown)
                  }
                }
                .fold(
                  err => MigrationResultFailed(err),
                  identity
                )
          }
          .getOrElse(MigrationResultFailed("Unable to get current version"))
    }
  }

  /**
    * Running sorted migration from one by one, terminate when any failed happen
    * @Preconditions Migration must be sorted and ready to execute from head to tail
    * @param pendingSortedMigration
    * @param migrationDirection
    * @return
    */
  def runSortedMigration(pendingSortedMigration: List[Migration], migrationDirection: MigrationDirectionDifferent): MigrationResult = {
    pendingSortedMigration match {
      case Nil => MigrationResultSuccess
      case head :: remaining => {
        val migrationFn = migrationDirection match {
          case MigrationDirectionUp => runMigrationUp _
          case MigrationDirectionDown => runMigrationDown _
        }
        migrationFn(head) match {
          case MigrationResultSuccess => runSortedMigration(remaining, migrationDirection)
          case failed => failed
        }
      }
    }
  }

  /**
    * Run a migration
    * @param pendingMigration
    * @param migrationDirection
    * @return
    */
  def runMigration(pendingMigration: Migration, migrationDirection: MigrationDirectionDifferent): MigrationResult = {
    migrationDirection match {
      case MigrationDirectionUp => runMigrationUp(pendingMigration)
      case MigrationDirectionDown => runMigrationDown(pendingMigration)
    }
  }

  /**
    * Run a migration up
    * @param migration
    * @return
    */
  def runMigrationUp(migration: Migration): MigrationResult = {
    migration
      .initSchemas()
      .flatMap(_ => migration.migrationUp())
      .fold[MigrationResult](
        rs => MigrationResultFailed(rs),
        _ => MigrationResultSuccess
      )
  }

  /**
    * Run a migration down
    * @param migration
    * @return
    */
  def runMigrationDown(migration: Migration): MigrationResult = {
    migration
      .deInitSchemas()
      .flatMap(_ => migration.migrationDown())
      .fold[MigrationResult](
        rs => MigrationResultFailed(rs),
        _ => MigrationResultSuccess
      )
  }
}

