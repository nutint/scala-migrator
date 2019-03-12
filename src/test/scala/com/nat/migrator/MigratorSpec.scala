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