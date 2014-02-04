sbt-play-assethash
==================

SBT plugin to add md5 hashes to the filenames of assets in Play 2.0 project.

This plugin automatically pulls in the related [sbt-play-artifact](https://github.com/guardian/sbt-play-artifact)
plugin.

Why use this?
-------------

If you want scale play and make deployments easier then this may be for you.

Our motivation behind introducing assets with an md5 hash in their name:
* We want the cache hit rate gain from our multi-component apps sharing a single set of assets
* We want to be able to do rolling deployments
* Therefore, multiple revisions of assets must exist to facilitate deployment and

The simplest way to provide this is to have separate hosting outside of the play apps (this avoids the need for play
to know about multiple revisions of an asset file).  This simplifies our CDN configuration as well.

How to use
----------

The most convenient way of using this plugin is to add a source dependency in a scala file under project/project:

```scala
val playAssetHashPluginVersion = "" // tag id you want to use
lazy val plugins = Project("plugins", file("."))
    .dependsOn(uri("git://github.com/guardian/sbt-play-assethash.git#" + playAssetHashPluginVersion))
```

Then add playAssetHashDistSettings or playAssetHashCompileSettings in your project (both from com.gu.PlayAssetHash._).
The dist settings include the compile settings - the only time you might want to use only the latter is when making a
common library (such as [frontend-common](https://github.com/guardian/frontend-common).

```scala
import com.gu.deploy.PlayAssetHash._

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
    .settings(playAssetHashDistSettings: _*)
```


Release
-------
To release a new version, tag it and push the tag to github.

```
git tag -a 1.XXX
git push --tags
```
