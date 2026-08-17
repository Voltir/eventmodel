ThisBuild / scalaVersion := "3.8.4"
ThisBuild / organization := "dev.eventmodel"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all"
)

// The model itself. Deliberately dependency-free -- these types are the point
// of the project, and nothing about them should know that HTML exists.
lazy val core = (project in file("core"))
  .settings(
    name := "eventmodel-core"
  )

// What every output format shares: the Renderer contract, and moving files to
// disk. Kept out of core so core stays a description of the method with no
// opinion about output at all.
lazy val render = (project in file("render"))
  .dependsOn(core)
  .settings(
    name := "eventmodel-render"
  )

// The static site.
lazy val html = (project in file("html"))
  .dependsOn(render)
  .settings(
    name := "eventmodel-html",
    libraryDependencies += "com.lihaoyi" %% "scalatags" % "0.13.1"
  )

// Plain text, for pasting a model into an LLM prompt.
lazy val markdown = (project in file("markdown"))
  .dependsOn(render)
  .settings(
    name := "eventmodel-markdown"
  )

// Your models. One file per board, each with its own `@main`, so a new model
// is a new file rather than an edit to a shared entry point. Living in this
// build rather than a separate project means library changes are picked up
// without a publishLocal step.
lazy val models = (project in file("models"))
  .dependsOn(html, markdown)
  .settings(
    name           := "eventmodel-models",
    publish / skip := true,
    // Write out/ at the repo root rather than under models/.
    // baseDirectory only takes effect on a forked run.
    run / fork                    := true,
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value
  )

lazy val root = (project in file("."))
  .aggregate(core, render, html, markdown, models)
  .settings(
    name           := "eventmodel",
    publish / skip := true
  )

// Convenience for the example. For your own boards use the main directly:
//   sbt "models/runMain renderMyBoard"   or   sbt "~models/runMain renderMyBoard"
addCommandAlias("render", "models/runMain renderShoppingCart")

// Serves out/ so the pages can poll their build stamp and reload themselves.
// Python comes from the devcontainer feature; nothing is installed for this.
lazy val serve = taskKey[Unit]("Serve out/ over HTTP on port 8000")

ThisBuild / serve := {
  val log = streams.value.log
  val dir = (ThisBuild / baseDirectory).value / "out"
  IO.createDirectory(dir)
  log.info(s"serving $dir at http://localhost:8000 — Ctrl-C to stop")
  scala.sys.process.Process(
    Seq("python3", "-m", "http.server", "8000", "--bind", "0.0.0.0"),
    dir
  ).!
}
