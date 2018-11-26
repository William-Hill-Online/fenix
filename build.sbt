import sbt.Def._
import sbt.Keys._

// sbt-dependecy-graph
import net.virtualvoid.sbt.graph.Plugin._

// sbt-scalariform
import scalariform.formatter.preferences._

enablePlugins(JavaAppPackaging)

// Resolvers
resolvers ++= Seq(
  "william hill nexus" at "https://nexus.dtc.prod.williamhill.plc:8443/repository/maven-public/"
)

val scalaVers = "2.11.8"
val scalaBinaryVers = "2.11"
val akkaVersion = "2.4.4"
//val akkaStreamingVers = "2.4.2"
val playVers = "2.4.6"

mainClass in Compile := Some("com.williamhill.fenix.server.FenixMain")

// Dependencies
val rootDependencies = Seq(
  "de.heikoseeberger" %% "constructr-akka" % "0.12.0",
  "de.heikoseeberger" %% "constructr-coordination-etcd" % "0.12.0",
  "joda-time" % "joda-time" % "2.8.2",
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.websudos" %% "phantom-dsl" % "1.11.0",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "org.scala-lang" % "scala-reflect" % scalaVers,
  "com.github.krasserm" %% "akka-persistence-cassandra" % "0.6",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.0",
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-metrics" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  //  "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamingVers,
  //  "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamingVers,
  //  "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamingVers,
  //  "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaStreamingVers,
  "com.typesafe.play" %% "play-json" % playVers,
  "io.reactivex" %% "rxscala" % "0.26.0",
  "org.apache.kafka" % "kafka-clients" % "0.9.0.1"
)

val testDependencies = Seq(
  "org.specs2" %% "specs2-core" % "3.6.5" % "test",
  "org.specs2" %% "specs2-scalacheck" % "3.6.5" % "test",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
)

val dependencies = rootDependencies ++ testDependencies

// Settings

val buildSettings = Seq(
  name := "fenix",
  organization := "com.williamhill",
  version := "1.0.0-SNAPSHOT",
  scalaVersion := scalaVers,
  scalaBinaryVersion := scalaBinaryVers
)

val compileSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-target:jvm-1.7",
    "-feature",
    "-language:_",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    //"-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"
  )
)

val forkedJvmOption = Seq(
  "-server",
  "-Dfile.encoding=UTF8",
  "-Duser.timezone=GMT"
)

val formatSettings = scalariformPreferences.apply(
  _
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, false)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
    .setPreference(CompactControlReadability, false)
    .setPreference(CompactStringConcatenation, false)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(FormatXml, true)
    .setPreference(IndentLocalDefs, false)
    .setPreference(IndentPackageBlocks, true)
    .setPreference(IndentSpaces, 2)
    .setPreference(IndentWithTabs, false)
    .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
    .setPreference(PreserveSpaceBeforeArguments, false)
    .setPreference(PreserveDanglingCloseParenthesis, true)
    .setPreference(RewriteArrowSymbols, false)
    .setPreference(SpaceBeforeColon, false)
    .setPreference(SpaceInsideBrackets, false)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(SpacesWithinPatternBinders, true)
    .setPreference(SpacesAroundMultiImports, true)
)

val pluginsSettings =
  buildSettings ++
    graphSettings

val publishSettings = isSnapshot.apply[Option[sbt.Resolver]] { value =>
  val nexus = "https://nexus.dtc.prod.williamhill.plc:8443/repository"
  if (value) Some("snapshots" at nexus + "/snapshots")
  else Some("releases" at nexus + "/releases")
}

val settings = Seq(
  libraryDependencies ++= dependencies,
  fork in run := true,
  fork in Test := true,
  fork in testOnly := true,
  connectInput in run := true,
  javaOptions in run ++= forkedJvmOption,
  javaOptions in Test ++= forkedJvmOption,
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  // formatting
  scalariformPreferences <<= formatSettings,
  // publishing
  publishTo <<= publishSettings,
  // classpath
  unmanagedClasspath in Runtime += baseDirectory.value / "src/universal/conf/",
  scriptClasspath += "../conf/"
)

val dockerSettings = Seq(
  dockerRepository := Some("docker-registry.prod.williamhill.plc/martin.hatas"),
  packageName in Docker := packageName.value,
  version in Docker := version.value,
  defaultLinuxInstallLocation in Docker := "/opt/fenix"
)

lazy val main =
  project
    .in(file("."))
    .settings(
      compileSettings ++ pluginsSettings ++ dockerSettings ++ settings: _*
    )
