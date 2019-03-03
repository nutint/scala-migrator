import AssemblyKeys._
import Keys._

name := "scala-migrator"

version := "1.0"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
)


assemblySettings

mergeStrategy in assembly := {
  case x if Assembly.isConfigFile(x) => MergeStrategy.concat
  case PathList(ps@_*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) => MergeStrategy.rename
  case PathList("META-INF", xs@_*) =>
    xs map {
      _.toLowerCase
    } match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) => MergeStrategy.discard
      case ps@(x :: _) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") => MergeStrategy.discard
      case "plexus" :: _ => MergeStrategy.discard
      case "service" :: _ => MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) => MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.first
    }
  case PathList(_*) => MergeStrategy.first
}


mainClass in assembly := Some("com.nat.migrator.Main")