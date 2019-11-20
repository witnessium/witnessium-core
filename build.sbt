lazy val finchVersion = "0.29.0"
lazy val circeVersion = "0.11.1"
lazy val monixVersion = "3.1.0"
lazy val twitterVersion = "19.6.0"
lazy val bouncycastleVersion = "1.62"
lazy val scodecBitsVersion = "1.1.12"
lazy val scalatagsVersion = "0.6.8"
lazy val refinedVersion = "0.9.9"
lazy val iterateeVersion = "0.19.0-M4"
lazy val swaydbVersion = "0.10.9"
lazy val pureconfigVersion = "0.11.1"
lazy val vueVersion = "2.6.10"
lazy val scalajsJavaTimeVersion = "0.2.5"

lazy val utestVersion = "0.7.1"
lazy val scalacheckVersion = "1.14.0"
lazy val scribeVersion = "2.7.6"
lazy val acyclicVersion = "0.2.0"
lazy val silencerVersion = "1.4.1"
lazy val splainVersion = "0.4.1"
lazy val kindProjectorVersion = "0.10.3"
lazy val betterMonadicForVersion = "0.3.0"
lazy val scalaTypedHoleVersion = "0.1.0"

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val sharedSettings = Seq(
  organization := "org.witnessium",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.8",

  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % acyclicVersion),
  addCompilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion),
  addCompilerPlugin("io.tryp" %% "splain" % splainVersion cross CrossVersion.patch),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForVersion),
  addCompilerPlugin("com.github.cb372" % "scala-typed-holes" % scalaTypedHoleVersion cross CrossVersion.full),

  resolvers += Resolver.sonatypeRepo("releases"),

  cancelable in Global := true,

  // 2.13 preview
  scalacOptions += "-Xsource:2.13",

  // scala-typed-holes set log-level info
  scalacOptions += "-P:typed-holes:log-level:info",

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

  // wartremover
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.Any, Wart.Nothing),

  testFrameworks += new TestFramework("utest.runner.Framework"),

  libraryDependencies ++= Seq(
    "org.scodec" %%% "scodec-bits" % scodecBitsVersion,
    "io.circe" %%% "circe-generic" % circeVersion,
    "io.circe" %%% "circe-refined" % circeVersion,
    "io.circe" %%% "circe-parser" % circeVersion,
    "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
    "com.outr" %%% "scribe" % scribeVersion,
    "eu.timepit" %%% "refined" % refinedVersion,
    "eu.timepit" %% "refined-cats" % refinedVersion,
    "eu.timepit" %%% "refined-scodec" % refinedVersion,
    "io.monix" %% "monix-tail" % monixVersion,
    "com.lihaoyi" %%% "utest" % utestVersion % Test,
    "org.scalacheck" %%% "scalacheck" % scalacheckVersion % Test,
    "eu.timepit" %%% "refined-scalacheck" % refinedVersion % Test,
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
      "com.twitter" %% "twitter-server" % twitterVersion,
      "com.twitter" %% "finagle-stats"  % twitterVersion,
      "io.catbird" %% "catbird-effect" % twitterVersion,
      "io.catbird" %% "catbird-util" % twitterVersion,
      "org.bouncycastle" % "bcprov-jdk15on" % bouncycastleVersion,
      "io.swaydb" %% "swaydb" % swaydbVersion,
      "io.swaydb" %% "monix" % swaydbVersion,
      "com.outr" %% "scribe-slf4j18" % scribeVersion,
      "com.github.pureconfig" %% "pureconfig" % pureconfigVersion,
      "eu.timepit" %% "refined-pureconfig" % refinedVersion,
    ),
    (resources in Compile) ++= {
      val f = (fastOptJS in (js, Compile)).value.data
      val fmap = f.getParentFile / (f.getName + ".map")
      val dep = (packageMinifiedJSDependencies in (js, Compile)).value
      Seq(f, fmap, dep)
    },
  )
  .dependsOn(common.jvm % "test->test;compile->compile")

lazy val consoleClient = (project in file("console"))
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-console-client",
  )
  .dependsOn(node, common.jvm)

lazy val js = (project in file("js"))
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-js",
    skip in packageJSDependencies := false,
    jsDependencies ++= Seq(
      "org.webjars" % "vue" % vueVersion / "vue.js",
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(common.js)

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(sharedSettings)
  .settings(
    name := "witnessium-core-common",
  )
  .jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-java-time" % scalajsJavaTimeVersion,
  )
  .jvmSettings(/* ... */)
