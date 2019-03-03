package com.nat.migrator

import org.scalatest.{BeforeAndAfter, FreeSpec, FunSuite}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._


class MigrationSpec
  extends FreeSpec
  with BeforeAndAfter
  with MockitoSugar
{

  "Validating migration data" - {

    "should error when version contains duplicate version number" in {

      val migrationV1 = mock[Migration]

      when(migrationV1.version).thenReturn("1.0.0")

    }

  }
}
