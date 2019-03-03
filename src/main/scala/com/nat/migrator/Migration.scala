package com.nat.migrator


trait Migration {


  /**
    * This method should return version that need to be identical
    * @return
    */
  def version: String

  /**
    * This method is for initialize database schemas, for safety this method should have side effect on new collections only.
    * The logic of running init schema is depends on current version of database
    * This method will be call or not depends on the caller
    * @param version version string of current schema version, should be identical with other migration
    * @return
    */
  def initSchemas(): Option[Boolean]



}