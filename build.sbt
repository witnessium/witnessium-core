lazy val finchVersion = "0.29.0"
lazy val circeVersion = "0.11.1"
lazy val twitterServerVersion = "19.5.1"
lazy val scalatagsVersion = "0.6.8"
lazy val refinedVersion = "0.9.8"
lazy val pureconfigVersion = "0.11.1"
lazy val vueVersion = "2.6.10"
lazy val utestVersion = "0.6.9"

lazy val scribeVersion = "2.7.6"
lazy val acyclicVersion = "0.2.0"
lazy val silencerVersion = "1.4.1"

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val sharedSettings = Seq(
  organization := "org.witnessium",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.8",

  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % acyclicVersion),
  addCompilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),

  cancelable in Global := true,

  // 2.13 preview
  scalacOptions += "-Xsource:2.13",

  // Linter
  scalacOptions ++= Seq(
    "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
    "-encoding", "utf-8",                // Specify character encoding used by source files.
    "-explaintypes",                     // Explain type errors in more detail.
    "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
    "-language:higherKinds",             // Allow higher-kinded types
    "-language:implicitConversions",     // Allow definition of implicit functions called views
    "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
    "-P:acyclic:force",                  // Enforce acyclic plugin across all files.
    "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
    "-Xfuture",                          // Turn on future language features.
    "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
    "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
    "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
    "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",            // Option.apply used implicit view.
    "-Xlint:package-object-classes",     // Class or object defined in package object.
    "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
    "-Xlint:unsound-match",              // Pattern match may not be typesafe.
    "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification",             // Enable partial unification in type constructor inference
    "-Ywarn-dead-code",                  // Warn when dead code is identified.
    "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
    "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",              // Warn when numerics are widened.
    "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",              // Warn if a local definition is unused.
    "-Ywarn-unused:params",              // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",            // Warn if a private member is unused.
    "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  ),

  scalacOptions in (Compile, console) ~= (_.filterNot(Set(
    "-Ywarn-unused:imports",
    "-Xfatal-warnings"
  ))),

  // assembly plugin related
  assemblyMergeStrategy in assembly := {
    case "BUILD" => MergeStrategy.discard
    case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },

  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.outr" %% "scribe" % scribeVersion,
    "com.outr" %% "scribe-slf4j18" % scribeVersion,
    "eu.timepit" %% "refined" % refinedVersion,
    "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided,
    "com.lihaoyi" %%% "utest" % utestVersion % Test,
    compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
    "com.github.ghik" %% "silencer-lib" % silencerVersion % Provided
  )
)

lazy val root = (project in file("."))
  .aggregate(node, js, common.js, common.jvm)

lazy val node = (project in file("node"))
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-node",

    libraryDependencies ++= Seq(
      "com.github.finagle" %% "finchx-core"  % finchVersion,
      "com.github.finagle" %% "finchx-circe"  % finchVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "twitter-server" % twitterServerVersion,
      "com.github.pureconfig" %% "pureconfig" % pureconfigVersion,
      "eu.timepit" %% "refined-pureconfig" % refinedVersion,
    ),
    (resources in Compile) ++= {
      val f = (fastOptJS in (js, Compile)).value.data
      val fmap = f.getParentFile / (f.getName + ".map")
      val dep = (packageMinifiedJSDependencies in (js, Compile)).value
      Seq(f, fmap, dep)
    },
    testFrameworks += new TestFramework("utest.runner.Framework"),
  )
  .dependsOn(common.jvm)

lazy val js = (project in file("js"))
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-js",
    skip in packageJSDependencies := false,
    jsDependencies += "org.webjars" % "vue" % vueVersion / "vue.js",
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(common.js)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-common",
  )
  .jsSettings(/* ... */)
  .jvmSettings(/* ... */)
