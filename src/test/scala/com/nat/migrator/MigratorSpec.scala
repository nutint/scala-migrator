package com.nat.migrator

import com.nat.migrator.model._
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatest.mockito.MockitoSugar

class MigratorSpec extends FreeSpec
  with BeforeAndAfter
  with MockitoSugar
{

  "Validating migration data" - {

    "should return all duplicated version when any migration contains duplicate version" in {
      val mgs =
        List("1.0.0", "1.0.0", "1.0.1", "1.2.0", "1.2.0")
          .map { version =>
            val mg = mock[Migration]
            when(mg.version).thenReturn(version)
            mg
          }

      val migrator = new Migrator {
        override def migrations: List[Migration] = mgs
        override def loadCurrentVersionNumber = None
      }

      migrator.validateMigrations match {
        case "1.0.0" :: "1.2.0" :: Nil => assert(true)
        case _ => assert(false)
      }

    }

    "should pass when no no duplicate version" in {
      val mgs =
        List("1.0.0", "1.0.1")
          .map { version =>
            val mg = mock[Migration]
            when(mg.version).thenReturn(version)
            mg
          }

      val migrator = new Migrator {
        override def migrations: List[Migration] = mgs
        override def loadCurrentVersionNumber = None
      }

      migrator.validateMigrations match {
        case Nil => assert(true)
        case _ => assert(false)
      }

    }
  }

  "Find migration direction" - {

    val mockedMigrations: List[Migration] = (1 to 5).map(digit => s"1.0.$digit")
      .map { versionNumber => {
        val mockedMigration = mock[Migration]
        when(mockedMigration.version).thenReturn(versionNumber)
        mockedMigration
      }}
      .reverse
      .toList

    object successMigrator extends Migrator {
      override def migrations: List[Migration] = ???
      override def loadCurrentVersionNumber: Option[String] = ???
      override def validateMigrations(validatingMigration: List[Migration]) = Nil
    }

    object failMigrator extends Migrator {
      override def migrations: List[Migration] = ???
      override def loadCurrentVersionNumber: Option[String] = ???
      override def validateMigrations(validatingMigration: List[Migration]) = "anyError" :: Nil
    }

    "should failed when validation error regarding correct version or not" in {
      // 1 incorrect version
      assert(failMigrator.findMigrationDirection("1.0.1", "1.0.0", mockedMigrations) == Left("anyError" :: Nil))
      // 0 incorrect version
      assert(failMigrator.findMigrationDirection("1.0.1", "1.0.2", mockedMigrations) == Left("anyError" :: Nil))
      // 2 incorrect versions
      assert(failMigrator.findMigrationDirection("2.0.1", "2.0.0", mockedMigrations) == Left("anyError" :: Nil))
    }

    "should failed when validation success, and any version is not exists" in {
      val res = Left(List("Unable to find target version, or current version in migration list"))
      // 1 incorrect version
      assert(successMigrator.findMigrationDirection("1.0.1", "1.0.0", mockedMigrations) == res)
      // 2 incorrect versions
      assert(successMigrator.findMigrationDirection("2.0.1", "2.0.0", mockedMigrations) == res)
    }

    "should return correct migration direction when there is no other error happen" - {
      "migration direction up" in {
        assert(successMigrator.findMigrationDirection("1.0.5", "1.0.1", mockedMigrations) == Right(MigrationDirectionUp))
        assert(successMigrator.findMigrationDirection("1.0.4", "1.0.3", mockedMigrations) == Right(MigrationDirectionUp))
      }

      "migration direction same" in {
        assert(successMigrator.findMigrationDirection("1.0.4", "1.0.4", mockedMigrations) == Right(MigrationDirectionSame))
        assert(successMigrator.findMigrationDirection("1.0.3", "1.0.3", mockedMigrations) == Right(MigrationDirectionSame))
      }

      "migration direction down" in {
        assert(successMigrator.findMigrationDirection("1.0.1", "1.0.4", mockedMigrations) == Right(MigrationDirectionDown))
        assert(successMigrator.findMigrationDirection("1.0.2", "1.0.3", mockedMigrations) == Right(MigrationDirectionDown))
      }


    }

  }

  "Find interested migration" - {

    val mockedMigrations: List[Migration] = (1 to 5).map(digit => s"1.0.$digit")
      .map { versionNumber => {
        val mockedMigration = mock[Migration]
        when(mockedMigration.version).thenReturn(versionNumber)
        mockedMigration
      }}
      .toList

    val migrator = new Migrator {
      override def migrations: List[Migration] = ???
      override def loadCurrentVersionNumber: Option[String] = ???
    }

    "should error when there is some version in the migration is not exists" in {
      assert(migrator.findInterestedMigrations(mockedMigrations, "2.0.0", "3.0.0") == Left(s"Both 2.0.0, and 3.0.0 are not exists"))
      assert(migrator.findInterestedMigrations(mockedMigrations, "2.0.0", "1.0.1") == Left(s"2.0.0 is not exists"))
      assert(migrator.findInterestedMigrations(mockedMigrations, "1.0.1", "2.0.0") == Left(s"2.0.0 is not exists"))
    }

    "should slice correctly" in {
      assert(migrator.findInterestedMigrations(mockedMigrations, "1.0.1", "1.0.3") == Right(mockedMigrations.slice(0, 3)))
      assert(migrator.findInterestedMigrations(mockedMigrations, "1.0.3", "1.0.1") == Right(mockedMigrations.slice(0, 3)))
      assert(migrator.findInterestedMigrations(mockedMigrations, "1.0.1", "1.0.1") == Right(mockedMigrations.slice(0, 1)))
    }

  }

  "migrate" - {

    type SideEffectFunction = () => Unit

    val createMigration = (vs: String) => (onMigrationUp: SideEffectFunction) => new Migration {
      override def version: String = vs
      override def initSchemas(): Either[String, Unit] = Right(Unit)
      override def deInitSchemas(): Either[String, Unit] = Right(Unit)
      override def migrationUp(): Either[String, Unit] = {
        onMigrationUp(); Right(Unit)
      }
      override def migrationDown(): Either[String, Unit] = {
        println("do migration down")
        Right(Unit)
      }
    }

    val mockMigrator = (
                         migrateEffect: String => SideEffectFunction,
                         versions: List[String],
                         currentVersion: Option[String]) =>
      new Migrator {
        override def migrations: List[Migration] =
          versions
            .map(vs => createMigration(vs)(migrateEffect(vs)))
        override def loadCurrentVersionNumber: Option[String] = currentVersion
      }

    "should migrate to the latest version if the not provide target version" in {

      // Migrator1
      var migrator1Log: List[String] = Nil

      val migrator1Effects: String => SideEffectFunction = (version: String) => () => {
        migrator1Log = migrator1Log :+ s"version $version migrated"
      }

      val migrator1 = mockMigrator(
        migrator1Effects,
        (1 to 5).reverse.map(_.toString).map(v=>s"1.0.$v").toList,
        Some("1.0.1")
      )

      // Migrator2
      var migrator2Log: List[String] = Nil

      val migrator2Effects: String => SideEffectFunction = (version: String) => () => {
        migrator2Log = migrator2Log :+ s"version $version migrated"
      }

      val migrator2 = mockMigrator(
        migrator2Effects,
        (1 to 5).reverse.map(_.toString).map(v=>s"1.0.$v").toList,
        Some("1.0.1")
      )

      // Tests
      migrator1.migrate(Some("1.0.5"))
      migrator2.migrate(None)
      assert(migrator1Log == migrator2Log)
    }

    "should fail when empty migration" in {
      val migrator = mockMigrator(
        (_: String) => ()=>Unit,
        Nil,
        None
      )

      assert(migrator.migrate(None) == MigrationResultFailed("Empty migration"))
      assert(migrator.migrate(Some("do not cared")) == MigrationResultFailed("Empty migration"))
    }

    "should fail when target version, and current version is the same" in {
      val migrator = mockMigrator(
        (_: String) => ()=>Unit,
        (1 to 5).reverse.map(_.toString).map(v=>s"1.0.$v").toList,
        Some("1.0.5")
      )

      assert(
        migrator.migrate(Some("1.0.5")) ==
        MigrationResultFailed("target version, and current version is the same (currentVersion = 1.0.5)")
      )
    }

    "should also fail when the validation is not passed" in {
      val migrator = new Migrator {
        override def migrations: List[Migration] = List(mock[Migration])
        override def loadCurrentVersionNumber: Option[String] = Some("xx")
        override def findMigrationDirection(targetVersion: String, currentVersion: String, migrations: List[Migration]): Either[List[String], MigrationDirection] = Left(List("an error"))
      }
      assert(migrator.migrate(Some("xx")) == MigrationResultFailed(s"Migration collection does not pass validation List(an error)"))
    }

    "should fail when load current version returns None" in {

      // Migrator1
      var migrator1Log: List[String] = Nil

      val migrator1Effects: String => SideEffectFunction = (version: String) => () => {
        migrator1Log = migrator1Log :+ s"version $version migrated"
      }

      val migrator1 = mockMigrator(
        migrator1Effects,
        (1 to 5).reverse.map(_.toString).map(v=>s"1.0.$v").toList,
        None // return None when get current version
      )

      assert(migrator1.migrate(Some("1.0.5")) == MigrationResultFailed("Unable to get current version"))
    }

    "should fail when unable to find interested version" in {
      val migrator = new Migrator {
        override def migrations: List[Migration] = List(mock[Migration])
        override def loadCurrentVersionNumber: Option[String] = Some("1.0.0")
        override def findInterestedMigrations(allMigrations: List[Migration], targetVersion: String, currentVersion: String): Either[String, List[Migration]] = Left("something wrong")
        override def findMigrationDirection(targetVersion: String, currentVersion: String, migrations: List[Migration]): Either[List[String], MigrationDirection] = Right(MigrationDirectionSame)
      }

      assert(migrator.migrate(Some("1.0.0")) == MigrationResultFailed("something wrong"))
    }

    "should run migration down when target version's index is lower than current version" in {
      var downWasRun = false
      val sideEff: SideEffectFunction = () => Unit
      val mockedMigrations: List[Migration] = (1 to 5).reverse.map(v => s"1.0.$v").map(v => createMigration(v)(sideEff)).toList
      val migrator = new Migrator {
        override def migrations: List[Migration] = mockedMigrations
        override def loadCurrentVersionNumber: Option[String] = Some("1.0.5")
        override def findInterestedMigrations(allMigrations: List[Migration], targetVersion: String, currentVersion: String): Either[String, List[Migration]] = Right(mockedMigrations)
        override def findMigrationDirection(targetVersion: String, currentVersion: String, migrations: List[Migration]): Either[List[String], MigrationDirection] = Right(MigrationDirectionDown)
        override def runMigrationDown(migration: Migration): MigrationResult = {
          downWasRun = true
          MigrationResultSuccess
        }
      }

      migrator.migrate(Some("1.0.1"))
      assert(downWasRun)
    }
  }

  "Run sorted migration" - {
    "should success when there is no migration left in the list" in {
      val migrator = new Migrator {
        override def migrations: List[Migration] = ???
        override def loadCurrentVersionNumber: Option[String] = ???
      }

      assert(migrator.runSortedMigration(Nil, MigrationDirectionUp) == MigrationResultSuccess)
      assert(migrator.runSortedMigration(Nil, MigrationDirectionDown) == MigrationResultSuccess)
    }

    "should fail if migration up or down failed" in {
      val migrator = new Migrator {
        override def migrations: List[Migration] = ???
        override def loadCurrentVersionNumber: Option[String] = ???
        override def runMigrationUp(migration: Migration) = MigrationResultFailed("up failed")
        override def runMigrationDown(migration: Migration) = MigrationResultFailed("down failed")
      }

      assert(migrator.runSortedMigration(mock[Migration] :: Nil, MigrationDirectionUp) == MigrationResultFailed("up failed"))
      assert(migrator.runSortedMigration(mock[Migration] :: Nil, MigrationDirectionDown) == MigrationResultFailed("down failed"))
    }

    "should success if migration up or down success" in {
      val migrator = new Migrator {
        override def migrations: List[Migration] = ???
        override def loadCurrentVersionNumber: Option[String] = ???
        override def runMigrationUp(migration: Migration) = MigrationResultSuccess
        override def runMigrationDown(migration: Migration) = MigrationResultSuccess
      }

      assert(migrator.runSortedMigration(mock[Migration] :: Nil, MigrationDirectionUp) == MigrationResultSuccess)
      assert(migrator.runSortedMigration(mock[Migration] :: Nil, MigrationDirectionDown) == MigrationResultSuccess)
    }
  }

  "Run migration" - {
    "should run migration up when provide up" in {
      var migrationUpCalled = false
      val migrator = new Migrator {
        override def migrations: List[Migration] = ???
        override def loadCurrentVersionNumber: Option[String] = ???
        override def runMigrationUp(migration: Migration): MigrationResult = {
          migrationUpCalled = true
          MigrationResultSuccess
        }
      }

      migrator.runMigration(mock[Migration], MigrationDirectionUp)
      assert(migrationUpCalled == true)
    }
    "should run migration down when provide down" in {
      var migrationDownCalled = false
      val migrator = new Migrator {
        override def migrations: List[Migration] = ???
        override def loadCurrentVersionNumber: Option[String] = ???
        override def runMigrationDown(migration: Migration): MigrationResult = {
          migrationDownCalled = true
          MigrationResultSuccess
        }
      }

      migrator.runMigration(mock[Migration], MigrationDirectionDown)
      assert(migrationDownCalled)
    }
  }

  "Running migration up or down" - {

    "should return reason when init schema failed" in {
      val migration = mock[Migration]
      when(migration.initSchemas()).thenReturn(Left("Something went wrong"))
      when(migration.deInitSchemas()).thenReturn(Left("Something went wrong"))

      val migrator = new Migrator {
        override def migrations: List[Migration] = migration :: Nil
        override def loadCurrentVersionNumber: Option[String] = ???
      }

      assert(migrator.runMigrationUp(migration) == MigrationResultFailed("Something went wrong"))
      assert(migrator.runMigrationDown(migration) == MigrationResultFailed("Something went wrong"))
    }

    "should return reason when running migration failed" in {
      val migration = mock[Migration]
      when(migration.initSchemas()).thenReturn(Right())
      when(migration.deInitSchemas()).thenReturn(Right())
      when(migration.migrationUp()).thenReturn(Left("migration up failed"))
      when(migration.migrationDown()).thenReturn(Left("migration down failed"))

      val migrator = new Migrator {
        override def migrations: List[Migration] = migration :: Nil
        override def loadCurrentVersionNumber: Option[String] = ???
      }

      assert(migrator.runMigrationUp(migration) == MigrationResultFailed("migration up failed"))
      assert(migrator.runMigrationDown(migration) == MigrationResultFailed("migration down failed"))
    }

    "should return success when everything success" in {
      val migration = mock[Migration]
      when(migration.initSchemas()).thenReturn(Right())
      when(migration.deInitSchemas()).thenReturn(Right())
      when(migration.migrationUp()).thenReturn(Right())
      when(migration.migrationDown()).thenReturn(Right())

      val migrator = new Migrator {
        override def migrations: List[Migration] = migration :: Nil
        override def loadCurrentVersionNumber: Option[String] = ???
      }

      assert(migrator.runMigrationUp(migration) == MigrationResultSuccess)
      assert(migrator.runMigrationDown(migration) == MigrationResultSuccess)
    }

  }

}