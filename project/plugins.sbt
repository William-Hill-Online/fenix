// Comment to get more information during initialization
//
logLevel := Level.Warn

// Resolvers
//

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Softprops Maven" at "http://dl.bintray.com/content/softprops/maven"

// Dependency graph
//
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.5")

// Scalariform
//
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.5.1")

// Scalastyle
//
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

// Update plugin
//
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.9")

// Native Packager
//
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")
