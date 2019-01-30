import sbt.Resolver

import scala.sys.process.Process
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)

val scalaV = "2.12.8"

val dl4jV = "1.0.0-beta2"
val ffmpegV = "3.2.1-1.3"

lazy val server = (project in file("server")).settings(
  promptTheme := com.scalapenos.sbt.prompt.PromptThemes.ScalapenosTheme,
  scalaVersion := scalaV,
  scalaJSProjects := Seq(client),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  compile in Compile := (compile in Compile).dependsOn(scalaJSPipeline, copyJsArtifacts).value,
  resolvers ++= Seq(
    Resolver.typesafeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")),

  assemblyMergeStrategy in assembly := {
    case PathList("org", "bytedeco", xs @ _*) => MergeStrategy.last
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },

  libraryDependencies ++= Seq(
    "com.vmunier"          %%   "scalajs-scripts"      % "1.1.0",
    "com.lihaoyi"          %%   "scalatags"            % "0.6.7",
    "org.webjars"          %    "bootstrap"            % "3.3.6",
    "io.netty"             %    "netty-all"            % "4.1.32.Final" withSources() withJavadoc(),
    //"com.typesafe"         %   "config"                % "1.3.3",
    "com.twelvemonkeys.imageio" % "imageio-core"       % "3.1.1",

    "org.nd4j" % "nd4j-native-platform" % dl4jV,
    "org.deeplearning4j" % "deeplearning4j-core" % dl4jV,
    "org.deeplearning4j" % "deeplearning4j-zoo" % dl4jV,
    "org.deeplearning4j" % "deeplearning4j-modelimport" % dl4jV,
    "org.datavec" % "datavec-data-image"  % dl4jV,

    "org.bytedeco.javacpp-presets" % "ffmpeg" % ffmpegV,
    "org.bytedeco.javacpp-presets" % "ffmpeg-platform" % ffmpegV,
    "org.bytedeco" % "javacv"      % "1.4.1"
  ),
  version := "0.1",
  WebKeys.packagePrefix in Assets := "public/",
  managedClasspath in Runtime += (packageBin in Assets).value,
  mainClass in assembly := Some("facenet.WebServer"),
  assemblyJarName in assembly := s"facenet-${version.value}.jar"
).enablePlugins(SbtWeb).
  dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(
  scalaVersion := scalaV,
  resolvers ++= Seq(Resolver.jcenterRepo),
  scalaJSUseMainModuleInitializer := false,

  jsDependencies +=
     "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",

  libraryDependencies ++= Seq(
    "org.scala-js"        %%%  "scalajs-dom" % "0.9.3",
    "org.querki"          %%%  "jquery-facade" % "1.2",
    "com.lihaoyi"         %%%  "scalatags" % "0.6.7"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).
  dependsOn(sharedJs)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    scalaVersion := scalaV,
    libraryDependencies ++= Seq("com.lihaoyi" %%% "upickle" % "0.6.6")
  )

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// loads the server project at sbt startup
onLoad in Global := (onLoad in Global).value andThen {s: State => "project server" :: s}

def copyJsArtifacts() = (baseDirectory) map { dir =>
  def execute() = {
    Process(s"cp ${dir}/src/main/resources/jquery.js     ${dir}/target/web/web-modules/main/webjars/lib").! &
    Process(s"cp ${dir}/src/main/resources/webcam.min.js ${dir}/target/web/web-modules/main/webjars/lib").! &
    Process(s"cp ${dir}/src/main/resources/camera.js     ${dir}/target/web/web-modules/main/webjars/lib").! &
    Process(s"cp ${dir}/src/main/resources/cam.css ${dir}/target/web/web-modules/main/webjars/lib/bootstrap/css").!
  }

  println("Coping js resources ...")
  haltOnCmdResultError(execute())
}

def haltOnCmdResultError(result: Int) {
  if (result != 0) throw new Exception("Build failed")
}