package com.gu.deploy

import java.io.{ FileInputStream, File }
import java.util.Properties
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import sbt._
import scala.collection.JavaConversions._
import com.google.common.io.Files
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex

object `package` {

  def using[S <: { def close() }, T](closable: S)(block: S => T): T = {
    try {
      block(closable)
    } finally {
      closable.close()
    }
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
    def md5Hex = new String(Hex.encodeHex(Files.getDigest(file, MessageDigest.getInstance("MD5"))))
  }

  implicit def string2Md5Hex(s: String) = new {
    def md5Hex = DigestUtils.md5Hex(s)
  }

  implicit def seq2UpdateWith[V](seq: Seq[V]) = new {
    def updateWith(vv: Map[V, V]): Seq[V] = seq map { v => vv.getOrElse(v, v) }
  }

  implicit def listOfMaps2DuplicateKeys[K, V](maps: List[Map[K, V]]) = new {
    def duplicateKeys: Set[K] = {
      val keys = (maps flatMap { _.keySet })
      val keyInstances = keys groupBy { k => k }
      (keyInstances filter { case (key, instances) => instances.length > 1 }).keySet
    }
  }

  def loadProperties(file: File): Map[String, String] = {
    val properties = new Properties()
    using(new FileInputStream(file)) { properties load _ }
    properties.toMap
  }
}