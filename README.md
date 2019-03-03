# scala-migrator
Zero downtime database migrator, this project is for Scala programmer to be able to implement migration easier

# Supported database
- MongoDB

# Practices
- Collection name must be configurable
- New service version should be configurable on operate on both collection on only new collection (Multistep deployment)

# Strategy
- For safety, this migration will create new collection, and new service version should work on new collection.
- New version of service must operate on previous version's schema, and must be able to make side effect on both collections (This make previous version of service able to operate with old schema in the same time)
- This migration script can run during new version of service is operating

# How it works

1. Step 0: Service A operate on schemas a
2. Step 1: Init new schema on collection b
3. Step 2: Running Service version B (Which can run on both collection configuration) on initialized schema
4. Step 3: Run migration script
5. Step 4: Run Service version B (Which run only new migrated collection)
6. Step 5: [Optional] Back up collection a
