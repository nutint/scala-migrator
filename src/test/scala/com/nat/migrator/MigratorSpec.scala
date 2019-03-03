package com.nat.migrator

import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfter, FreeSpec}
import org.scalatest.mockito.MockitoSugar

class MigratorSpec extends FreeSpec
  with BeforeAndAfter
  with MockitoSugar
{

  "Validating migration data" - {

    "should error when version contains duplicate version number" in {
      val mgs =
        List("1.0.0", "1.0.0")
          .map { version =>
            val mg = mock[Migration]
            when(mg.version).thenReturn(version)
            mg
          }

      val migrator = new Migrator {
        override def migrations: List[Migration] = mgs
      }

      migrator.validateMigrations match {
        case "1.0.0" :: Nil => assert(true)
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
      }

      migrator.validateMigrations match {
        case Nil => assert(true)
        case _ => assert(false)
      }

    }

  }
}