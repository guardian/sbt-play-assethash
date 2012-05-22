import com.typesafe.sbtscalariform.ScalariformPlugin
import sbt._
import sbt.Keys._

object PluginBuild extends Build {
  
  // Plugins included here are exported to projects that use this plugin because
  // they are dependencies of the plugin itself and not associated with the 
  // build definition as plugins usually are.

  val playArtifactPluginVersion = "1.5"

  lazy val playSnapshots = Resolver.url("Play 2.1-SNAPSHOT",
    url("http://guardian.github.com/ivy/repo-snapshots"))(Resolver.ivyStylePatterns)

  lazy val main = Project("sbt-play-assethash", file("."))
    // Fixed in SBT 0.12: https://github.com/harrah/xsbt/issues/329
    //.settings(ScalariformPlugin.scalariformSettings: _*)
    .settings(
      name := "sbt-play-assethash",
      organization := "com.gu",
      sbtPlugin := true,

      resolvers += playSnapshots
    )
    .dependsOn(uri("git://github.com/guardian/sbt-play-artifact.git#" + playArtifactPluginVersion))
}
