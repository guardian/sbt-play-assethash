package frontend

import com.typesafe.sbtscalariform.ScalariformPlugin
import java.util.Properties
import java.io.{ FileInputStream, File }
import sbt._
import sbtassembly.Plugin._
import AssemblyKeys._
import PlayProject._
import Keys._

object Frontend extends Plugin {

  lazy val compileSettings = ScalariformPlugin.scalariformSettings ++ Seq(
    organization := "com.gu",
    scalaVersion := "2.9.1",

    maxErrors := 20,
    javacOptions := Seq("-g", "-source", "1.6", "-target", "1.6", "-encoding", "utf8"),
    scalacOptions := Seq("-unchecked", "-optimise", "-deprecation", "-Xcheckinit", "-encoding", "utf8"),

    externalResolvers <<= resolvers map { rs =>
      Resolver.withDefaultResolvers(rs, scalaTools = false)
    },

    playCopyAssets <<= copyAssetsWithMD5s,

    ivyXML :=
      <dependencies>
          <exclude org="commons-logging"/>       // conflicts with jcl-over-slf4j
          <exclude org="org.springframework"/>   // because I don't like it
      </dependencies>
  )

  lazy val distSettings = compileSettings ++ assemblySettings ++ Seq(
    test in assembly := {},

    mainClass in assembly := Some("play.core.server.NettyServer"),
    dist <<= buildDeployArtifact,

    mergeStrategy in assembly <<= (mergeStrategy in assembly) { current =>
      {
        // Previous default MergeStrategy was first

        // Take ours, i.e. MergeStrategy.last...
        case "logger.xml" => MergeStrategy.last
        case "version.txt" => MergeStrategy.last

        case "overview.html" => MergeStrategy.first
        case meta if meta.startsWith("META-INF/") => MergeStrategy.first

        case other => current(other)
      }
    }
  )

  private def copyAssetsWithMD5s =
    (streams in Compile, playCopyAssets, classDirectory in Compile) map {
      (s, current, classDirectory) =>
        implicit val log = s.log

        val assetFiles = current.unzip._2 filter { _.isUnder(classDirectory / "public") }
        val assets = Assets.fromFiles(classDirectory / "public", assetFiles)

        // Wait for assets to appear if necessary from asynchronous write. TODO: Revisit this
        assetFiles.synchronise()

        val assetRemappings = assets.toMD5Remap

        // Generate assetmap file
        val assetMapContents = assets.toText
        val assetMapFile = classDirectory / "assetmaps" / ("asset.%s.map" format assetMapContents.md5Hex)

        IO.delete(classDirectory / "assetmaps")
        IO.write(assetMapFile, assetMapContents)
        log.info("Generated assetmap file at %s:\n%s".format(assetMapFile, assetMapContents).indentContinuationLines)

        // Rename assets to include md5Hex chunk
        IO.move(assetRemappings)
        log.info(
          ("Renamed assets to include md5Hex chunk:\n" + (assetRemappings mkString "\n").sortLines).
            indentContinuationLines.
            deleteAll(classDirectory / "public" + "/")
        )

        // Update current with new names and assetmap file
        (assetMapFile -> assetMapFile) +: (current.toMap composeWith assetRemappings).toSeq
    }

  private def buildDeployArtifact =
    (streams, assembly, baseDirectory, target, name, playCopyAssets, classDirectory in Compile) map {
      (s, assembly, baseDirectory, target, projectName, playCopyAssets, classDirectory) =>
        {
          val distFile = target / "artifacts.zip"
          s.log.info("Disting " + distFile)

          if (distFile exists) { distFile.delete }

          val targetDist = target / "dist"
          if (targetDist exists) { targetDist.delete }

          // Extract and identify assets
          IO.unzip(assembly, targetDist, new SimpleFilter(name =>
            name.startsWith("assetmaps") || name.startsWith("public"))
          )

          val assetMaps = (targetDist / "assetmaps" * "*").get map { loadAssetMap(_) }

          // You try to determine a precedence order here if you like...
          val keyCollisions = assetMaps.toList.duplicateKeys
          if (!keyCollisions.isEmpty) {
            throw new RuntimeException("Assetmap collisions for: " + keyCollisions.mkString(", "))
          }

          val staticFiles = assetMaps flatMap { _.values } map { file =>
            (targetDist / "public" / file, "packages/%s/static-files/%s".format(projectName, file))
          }

          // Build artifact
          val assemblyFiles = Seq(
            baseDirectory / "conf" / "deploy.json" -> "deploy.json",
            assembly -> "packages/%s/%s".format(projectName, assembly.getName)
          ) ++ staticFiles

          IO.zip(assemblyFiles, distFile)

          // Tells TeamCity to publish the artifact => leave this println in here
          println("##teamcity[publishArtifacts '%s => .']" format distFile)

          s.log.info("Done disting.")
          assembly
        }
    }

  private def loadAssetMap(file: File): Map[String, String] = {
    val properties = new Properties()
    using(new FileInputStream(file)) { properties.load }
    properties.toMap
  }
}