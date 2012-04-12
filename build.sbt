name := "sbt-frontend-build"

organization := "com.gu"

version := "1.3-SNAPSHOT"

sbtPlugin := true

resolvers += Resolver.url("sbt-plugin-releases",
  new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

resolvers ++= Seq(
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

seq(scalariformSettings: _*)


//adding plugin here as we want it to propagate to projects that use this plugin
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.7.3")

addSbtPlugin("play" % "sbt-plugin" % "2.0")

addSbtPlugin("com.typesafe.sbtscalariform" % "sbtscalariform" % "0.3.1")
