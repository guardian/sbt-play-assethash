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

  val deploymentArtifact = TaskKey[File]("deployment-artifact", "Builds a deployable zip file for magenta")
  val artifactResources = TaskKey[Seq[(File,String)]]("artifact-resources", "Files that will be collected by the deployment-artifact task")
  val artifactFile = SettingKey[String]("artifact-file", "Filename of the artifact built by deployment-artifact")

  lazy val compileSettings:Seq[Setting[_]] = ScalariformPlugin.scalariformSettings ++ Seq(
    organization := "com.gu",
    scalaVersion := "2.9.1",

    maxErrors := 20,
    javacOptions := Seq("-g", "-source", "1.6", "-target", "1.6", "-encoding", "utf8"),
    scalacOptions := Seq("-unchecked", "-optimise", "-deprecation", "-Xcheckinit", "-encoding", "utf8"),

    externalResolvers <<= resolvers map { rs =>
      Resolver.withDefaultResolvers(rs, scalaTools = false)
    },

    ivyXML :=
      <dependencies>
          <exclude org="commons-logging"/>       // conflicts with jcl-over-slf4j
          <exclude org="org.springframework"/>   // because I don't like it
      </dependencies>
  )

  lazy val distSettings:Seq[Setting[_]] = compileSettings ++ assemblySettings ++ Seq(
    test in assembly := {},

    mainClass in assembly := Some("play.core.server.NettyServer"),

    artifactResources <<= (assembly, name, baseDirectory) map { (assembly, name, baseDirectory) =>
      Seq(
        (assembly, "packages/%s/%s".format(name, assembly.getName)),
        (baseDirectory / "conf" / "deploy.json", "deploy.json")
      )
    },

    artifactFile := "artifacts.zip",

    deploymentArtifact <<= buildDeployArtifact,
    dist <<= buildDeployArtifact,

    mergeStrategy in assembly <<= (mergeStrategy in assembly) { current =>
      {
        // Previous default MergeStrategy was first

        // Take ours, i.e. MergeStrategy.last...
        case "logger.xml" => MergeStrategy.last
        case "version.txt" => MergeStrategy.last

        case "overview.html" => MergeStrategy.first
        case "NOTICE" => MergeStrategy.first
        case "LICENSE" => MergeStrategy.first
        case meta if meta.startsWith("META-INF/") => MergeStrategy.first

        case other => current(other)
      }
    },

    excludedFiles in assembly := { (bases: Seq[File]) =>
    bases flatMap { base =>
      (base / "META-INF" * "*").get collect {
        case f if f.getName.toLowerCase == "license" => f
        case f if f.getName.toLowerCase == "manifest.mf" => f
        case f if f.getName.endsWith(".SF") => f
        case f if f.getName.endsWith(".DSA") => f
        case f if f.getName.endsWith(".RSA") => f
      }
    }}
  )

  lazy val assetHashCompileSettings:Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= cssGeneratorTask,
    resourceGenerators in Compile <+= imageGeneratorTask,
    managedResources in Compile <<= managedResourcesWithMD5s
  )

  lazy val assetHashDistSettings:Seq[Setting[_]] = assetHashCompileSettings ++ Seq(
    artifactResources <++= assetMapResources
  )


  val cssGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDir, resourceManaged) => generatorTransform(sourceDir,resourceManaged,(sourceDir / "assets") ** "*.css")
  }

  val imageGeneratorTask = (sourceDirectory in Compile, resourceManaged in Compile) map {
    (sourceDirectory, resourceManaged) => generatorTransform(sourceDirectory, resourceManaged, (sourceDirectory / "assets" / "images") ** "*")
  }

  val generatorTransform = {
    (sourceDirectory:File, resourceManaged:File, assetFinder:PathFinder) =>
      val copies = assetFinder.get map { asset => (asset, resourceManaged / "public" / asset.rebase(sourceDirectory / "assets").toString) }
      IO.copy(copies)
      copies.unzip._2
  }

  private def managedResourcesWithMD5s =
    (streams in Compile, managedResources in Compile, resourceManaged in Compile) map {
      (s, current, resourceManaged) =>
        implicit val log = s.log

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

  private def assetMapResources =
    (streams, assembly, target, name) map {
      (s, assembly, target, projectName) =>
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
          throw new RuntimeException("Assetmap collisions for: " + keyCollisions.toList.sorted.mkString(", "))
        }

        val staticFiles = assetMaps flatMap { _.values } map { file =>
          (targetDist / "public" / file, "packages/%s/static-files/%s".format(projectName, file))
        }

        staticFiles
    }

  private def buildDeployArtifact =
    (streams, assembly, target, artifactResources, artifactFile) map {
      (s, assembly, target, resources, artifactFileName) =>
        {
          val distFile = target / artifactFileName
          s.log.info("Disting " + distFile)

          if (distFile exists) { distFile.delete }
          IO.zip(resources, distFile)

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