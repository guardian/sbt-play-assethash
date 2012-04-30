package frontend

import java.io.{ FileInputStream, File }
import java.util.Properties
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import sbt._
import scala.collection.JavaConversions._

object `package` {

  def using[S <: { def close() }, T](closable: S)(block: S => T): T = {
    try {
      block(closable)
    } finally {
      closable.close()
    }
  }

  implicit def string2Md5Hex(s: String) = new {
    lazy val md5Hex: String = DigestUtils md5Hex s
  }

  implicit def string2IndentContinuationLines(s: String) = new {
    lazy val indentContinuationLines: String = s.replaceAll("\n", "\n\t\t")
  }

  implicit def string2SortLines(s: String) = new {
    lazy val sortLines: String = s.split("\n").toList.sorted mkString "\n"
  }

  implicit def string2DeleteAll(s: String) = new {
    def deleteAll(regex: String): String = s.replaceAll(regex, "")
  }

  implicit def file2Rebase(file: File) = new {
    def rebase(directory: File): File = file.relativeTo(directory).get
    def isRebaseableTo(directory: File): Boolean = file.relativeTo(directory).isDefined
    def isUnder(directory: File): Boolean = isRebaseableTo(directory)
  }

  implicit def file2Md5Hex(file: File) = new {
    def contents: String = using(new FileInputStream(file)) { IOUtils toString _ }
    def md5Hex = contents.md5Hex
  }

  implicit def map2ComposeWith[K, V](kv: Map[K, V]) = new {
    def composeWith(vv: Map[V, V]): Map[K, V] = kv mapValues { v => vv.getOrElse(v, v) }
  }

  implicit def properties2ToMap(properties: Properties) = new {
    def toMap: Map[String, String] = properties.entrySet map { entry =>
      (entry.getKey.toString, entry.getValue.toString)
    } toMap
  }

  implicit def listOfMaps2DuplicateKeys[K, V](maps: List[Map[K, V]]) = new {
    def duplicateKeys: Set[K] = {
      val keys = (maps flatMap { _.keySet })
      val keyInstances = keys groupBy { k => k }
      (keyInstances filter { case (key, instances) => instances.length > 1 }).keySet
    }
  }

  implicit def seqOfFiles2RichSeqOfFiles(files: Seq[File]) = new RichTraversableOfFiles(files)

  class RichTraversableOfFiles(files: Seq[File]) {
    def synchronise(attempt: Int = 1)(implicit log: { def info(msg: => String) }) {
      val missing = files filter { !_.exists() }

      if (!missing.isEmpty) {
        log.info("Waiting on: " + missing.mkString(", "))
        if (attempt < 20) {
          log.info("Retrying synchronisation after 100ms (retry %d)".format(attempt))
          Thread.sleep(100)
          missing.synchronise(attempt + 1)
        } else {
          log.warn("Aborting wait after 20 retries")
        }
      }
    }
  }
}