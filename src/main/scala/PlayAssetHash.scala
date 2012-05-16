package com.gu.deploy

import com.gu.deploy.PlayArtifact._
import java.io.File
import sbt._
import sbt.Keys._
import sbtassembly.Plugin.AssemblyKeys._

object PlayAssetHash extends Plugin {

  lazy val playAssetHashCompileSettings: Seq[Setting[_]] = playArtifactCompileSettings ++ compileSettings
  lazy val playAssetHashDistSettings: Seq[Setting[_]] = playArtifactDistSettings ++ distSettings

  lazy val compileSettings: Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= cssGeneratorTask,
    resourceGenerators in Compile <+= imageGeneratorTask,
    managedResources in Compile <<= managedResourcesWithMD5s
  )

  lazy val distSettings: Seq[Setting[_]] = compileSettings ++ Seq(
    playArtifactResources <++= assetMapResources
  )

  val cssGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDirectory, resourceManaged) => generatorTransform(sourceDirectory, resourceManaged, (sourceDirectory / "assets") ** "*.css")
  }

  val imageGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDirectory, resourceManaged) => generatorTransform(sourceDirectory, resourceManaged, (sourceDirectory / "assets" / "images") ** "*")
  }

  def generatorTransform(sourceDirectory: File, resourceManaged: File, assetFinder: PathFinder) = {
      val copies = assetFinder.get map {
        asset => (asset, resourceManaged / "public" / asset.rebase(sourceDirectory / "assets").toString)
      }
      IO.copy(copies)
      copies.unzip._2
  }

  private def managedResourcesWithMD5s = (streams in Compile, managedResources in Compile, resourceManaged in Compile) map {
    (streams, current, resourceManaged) =>
      implicit val log = streams.log

      val assetFiles = current filter { _.isUnder(resourceManaged / "public") }
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

  private def assetMapResources = (assembly, target, name) map {
    (assembly, target, projectName) =>
      val targetDist = target / "dist"
      if (targetDist exists) {
        targetDist.delete
      }

      // Extract and identify assets
      IO.unzip(assembly, targetDist, new SimpleFilter(name =>
        name.startsWith("assetmaps") || name.startsWith("public"))
      )

      val assetMaps = (targetDist / "assetmaps" * "*").get map { loadProperties(_) }

      // You determine a precedence order here if you like...
      val keyCollisions = assetMaps.toList.duplicateKeys
      if (!keyCollisions.isEmpty) {
        throw new RuntimeException("Assetmap collisions for: " + keyCollisions.toList.sorted.mkString(", "))
      }

      val staticFiles = assetMaps flatMap { _.values } map { file =>
        (targetDist / "public" / file, "packages/%s/static-files/%s".format(projectName, file))
      }

      staticFiles
  }
}