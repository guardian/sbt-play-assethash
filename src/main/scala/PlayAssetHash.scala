package com.gu

import com.typesafe.sbtscalariform.ScalariformPlugin
import java.util.Properties
import java.io.{FileInputStream, File}
import sbt._
import sbtassembly.Plugin._
import AssemblyKeys._
import PlayProject._
import Keys._
import PlayArtifact._

object PlayAssetHash extends Plugin {

  lazy val playAssetHashCompileSettings: Seq[Setting[_]] = playArtifactCompileSettings ++ Seq(
    resourceGenerators in Compile <+= cssGeneratorTask,
    resourceGenerators in Compile <+= imageGeneratorTask,
    managedResources in Compile <<= managedResourcesWithMD5s
  )

  lazy val playAssetHashDistSettings: Seq[Setting[_]] = playAssetHashCompileSettings ++ Seq(
    playArtifactResources <++= assetMapResources
  )

  val cssGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDir, resourceManaged) => generatorTransform(sourceDir, resourceManaged, (sourceDir / "assets") ** "*.css")
  }

  val imageGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDirectory, resourceManaged) => generatorTransform(sourceDirectory, resourceManaged, (sourceDirectory / "assets" / "images") ** "*")
  }

  val generatorTransform = {
    (sourceDirectory: File, resourceManaged: File, assetFinder: PathFinder) =>
      val copies = assetFinder.get map {
        asset => (asset, resourceManaged / "public" / asset.rebase(sourceDirectory / "assets").toString)
      }
      IO.copy(copies)
      copies.unzip._2
  }

  private def managedResourcesWithMD5s =
    (streams in Compile, managedResources in Compile, resourceManaged in Compile) map {
      (s, current, resourceManaged) =>
        implicit val log = s.log

        val assetFiles = current filter {
          _.isUnder(resourceManaged / "public")
        }
        val assets = Assets.fromFiles(resourceManaged / "public", assetFiles)

        val assetRemappings = assets.toMD5Remap

        // Generate assetmap file
        val assetMapContents = assets.toText
        val assetMapFile = resourceManaged / "assetmaps" / ("asset.%s.map" format assetMapContents.md5Hex)

        IO.delete(resourceManaged / "assetmaps")
        IO.write(assetMapFile, assetMapContents)
        log.info("Generated assetmap file at %s:\n%s".format(assetMapFile, assetMapContents).indentContinuationLines)

        // Copy assets to include md5Hex chunk. Moving would break subsequent calls.
        IO.copy(assetRemappings)
        log.info(
          ("Renamed assets to include md5Hex chunk:\n" + (assetRemappings mkString "\n").sortLines).
            indentContinuationLines.
            deleteAll(resourceManaged / "public" + "/")
        )

        // Update current with new names and assetmap file
        assetMapFile +: (current updateWith assetRemappings).toSeq
    }

  private def assetMapResources =
    (streams, assembly, target, name) map {
      (s, assembly, target, projectName) =>
        val targetDist = target / "dist"
        if (targetDist exists) {
          targetDist.delete
        }

        // Extract and identify assets
        IO.unzip(assembly, targetDist, new SimpleFilter(name =>
          name.startsWith("assetmaps") || name.startsWith("public"))
        )

        val assetMaps = (targetDist / "assetmaps" * "*").get map {
          loadAssetMap(_)
        }

        // You try to determine a precedence order here if you like...
        val keyCollisions = assetMaps.toList.duplicateKeys
        if (!keyCollisions.isEmpty) {
          throw new RuntimeException("Assetmap collisions for: " + keyCollisions.toList.sorted.mkString(", "))
        }

        val staticFiles = assetMaps flatMap {
          _.values
        } map {
          file =>
            (targetDist / "public" / file, "packages/%s/static-files/%s".format(projectName, file))
        }

        staticFiles
    }

  private def loadAssetMap(file: File): Map[String, String] = {
    val properties = new Properties()
    using(new FileInputStream(file)) {
      properties.load
    }
    properties.toMap
  }
}