package frontend

import com.typesafe.sbtscalariform.ScalariformPlugin
import java.io.File
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

    playCopyAssets <<= copyAssetsWithMD5s
  )

  lazy val distSettings = compileSettings ++ assemblySettings ++ Seq(
    test in assembly := {},

    mainClass in assembly := Some("play.core.server.NettyServer"),
    dist <<= buildDeployArtifact,
    assembledMappings in assembly <<= (assembledMappings in assembly, classDirectory in Compile) map {
      filterFiles(List("logger.xml", "version.txt"))
    }
  )

  private def copyAssetsWithMD5s =
    (streams in Compile, playCopyAssets, classDirectory in Compile) map {
      (s, current, classDirectory) =>

        val assetFiles = current.unzip._2 filter { _.isUnder(classDirectory / "public") }
        val assets = Assets.fromFiles(classDirectory / "public", assetFiles)
        val assetRemappings = assets.toMD5Remap

        // Generate assetmap file
        val assetMapContents = assets.toText
        val assetMapFile = classDirectory / "assetmaps" / ("asset.%s.map" format assetMapContents.md5Hex)

        IO.delete(classDirectory / "assetmaps")
        IO.write(assetMapFile, assetMapContents)
        s.log.info("Generated assetmap file at %s:\n%s".format(assetMapFile, assetMapContents).indentContinuationLines)

        // Rename assets to include md5Hex chunk
        IO.move(assetRemappings)
        s.log.info(
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

          val publicDirectory = classDirectory / "public"
          val staticFiles = playCopyAssets.unzip._2 filter { _ isUnder publicDirectory } map { file =>
            val locationInZip = "packages/%s/static-files/%s".format(projectName, file rebase publicDirectory)
            (file, locationInZip)
          }

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

  def filterFiles(filenames: Seq[String])(original: (File => Seq[(File, String)]), classDir: File) = (base: File) => original(base).filter {
    case (file, location) if filenames contains location => {
      //we want the file in our app, not the one in the play jar file
      //the simplest way to figure this out is that our one is in the class directory
      file.getAbsolutePath.contains(classDir.getAbsolutePath)
    }
    case _ => true
  }

}