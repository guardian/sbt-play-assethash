import sbt._
import Keys._
import com.typesafe.sbtscalariform.ScalariformPlugin

object PluginBuild extends Build {
  
  // Plugins included here are exported to projects that use this plugin because
  // they are dependencies of the plugin itself and not associated with the 
  // build definition as plugins usually are.
  
  lazy val main = Project("sbt-play-assethash", file("."))
    .settings(
      name := "sbt-play-assethash",
      organization := "com.gu",
      sbtPlugin := true,
      resolvers ++= Seq(
        "sbt-idea-repo" at "http://mpeltonen.github.com/maven/",
        "mvn repository" at "http://mvnrepository.com/artifact/"
      ),

      externalResolvers <<= resolvers map { rs =>
        Resolver.withDefaultResolvers(rs, scalaTools = false)
      }
  )
    .dependsOn(
      uri("git://github.com/guardian/sbt-play-artifact.git#1.1")
    )

}
