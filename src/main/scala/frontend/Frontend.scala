package frontend

import sbt._
import Keys._
import java.security.MessageDigest
import com.google.common.io._
import sbtassembly.Plugin._
import AssemblyKeys._
import PlayProject._
import com.typesafe.sbtscalariform.ScalariformPlugin

object Frontend extends Plugin {
  val LessFile = """(.*)\.less$""".r
  val CoffeeFile = """(.*)\.coffee$""".r
  val JavaScriptFile = """(.*)\.js$""".r

  lazy val commonCompileSettings = ScalariformPlugin.settings ++ Seq(
    organization := "com.gu",
    scalaVersion := "2.9.1",
    maxErrors := 20,
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "utf8"),
    scalacOptions ++= Seq("-unchecked", "-optimise", "-deprecation", "-Xcheckinit", "-encoding", "utf8")
  )

  lazy val frontendSettings = commonCompileSettings ++ assemblySettings ++ Seq(
    sourceGenerators in Compile <+= staticFileRoutes,
    mainClass in assembly := Some("play.core.server.NettyServer"),
    test in assembly := {},
    dist <<= buildDeployArtifact
  )

  private def digestFor(file: File): String = Hash.toHex(Files.getDigest(file, MessageDigest.getInstance("MD5")))

  private def staticFileRoutes = (baseDirectory, streams, sourceManaged).map { (base, s, sourceDir) =>
    {
      val staticMap = hashFiles(base).map {
        case (raw, cached) => """ "%s" -> "%s" """ format (raw, cached)
      } mkString (",")

      val template = """
            package controllers

            object Static {
              lazy val staticMappings = Map[String,  String](
                %s
              )
              lazy val reverseMappings = staticMappings.map{ _.swap }

              def at(path: String, file: String) = Assets.at(path, reverseMappings(file))
              def at(path: String) = "/assets/" + staticMappings(path)
            }
          """ format (staticMap)

      val file = sourceDir / "controllers" / "Static.scala"

      IO.write(file, template)

      Seq(file)
    }
  }

  def hashFiles(base: File): Seq[(String, String)] = {
    val assetsDir = (base / "app" / "assets")
    val resourceFiles = (assetsDir ** "*").get.filter(!_.isDirectory)
    val hashedResourceFiles = resourceFiles.flatMap(f => f.relativeTo(assetsDir).map((digestFor(f), _)))

    val generatedPaths = hashedResourceFiles flatMap {
      case (hash, file) =>
        file.getPath match {
          case LessFile(name) => List((name + ".css", name + "." + hash + ".css"),
            (name + ".min.css", name + "." + hash + ".min.css"))
          case CoffeeFile(name) => List((name + ".js", name + "." + hash + ".js"),
            (name + ".min.js", name + "." + hash + ".min.js"))
          case JavaScriptFile(name) => List((name + ".js", name + "." + hash + ".js"),
            (name + ".min.js", name + "." + hash + ".min.js"))
          case _ => sys.error("Do not understand resource file: " + name)
        }
    }

    val publicDir = (base / "public")

    val publicPaths = (publicDir ** ("**")).get.filter(!_.isDirectory).flatMap {
      file: File =>
        val hash = digestFor(file)

        file.relativeTo(publicDir).map(_.getPath).toList.map {
          path =>
            val pathParts = path.split("""\.""")
            (path, (pathParts.dropRight(1) ++ List(hash) ++ pathParts.takeRight(1)).mkString("."))
        }
    }
    (generatedPaths ++ publicPaths)
  }

  def buildDeployArtifact =
    (assembly, streams, baseDirectory, target, resourceManaged in Compile, name) map {
      (jar, s, baseDir, outDir, resourcesDir, projectName) =>
        {
          val log = s.log

          val distFile = outDir / "artifacts.zip"
          log.info("Disting %s ..." format distFile)

          if (distFile exists) { distFile delete () }

          val cacheBustedResources = hashFiles(baseDir)

          val resourceAssetsDir = resourcesDir / "public"
          val resourceAssets = cacheBustedResources map {
            case (key, cachedKey) =>
              (resourceAssetsDir / key, cachedKey)
          } filter { fileExists }

          val publicAssetsDir = baseDir / "public"
          val publicAssets = cacheBustedResources map {
            case (key, cachedKey) =>
              (publicAssetsDir / key, cachedKey)
          } filter { fileExists }

          val staticFiles = (resourceAssets ++ publicAssets) map {
            case (file, cachedKey) =>
              val locationInZip = "packages/%s/static-files/%s".format(projectName, cachedKey)
              log.verbose("Static file %s -> %s" format (file, locationInZip))
              (file, locationInZip)
          }

          val filesToZip = Seq(
            baseDir / "conf" / "deploy.json" -> "deploy.json",
            jar -> "packages/%s/%s".format(projectName, jar.getName)
          ) ++ staticFiles

          IO.zip(filesToZip, distFile)

          // Tells TeamCity to publish the artifact => leave this println in here
          println("##teamcity[publishArtifacts '%s => .']" format distFile)

          log.info("Done disting.")
          jar
        }
    }

  private def fileExists(f: (File, String)) = f._1.exists()
}