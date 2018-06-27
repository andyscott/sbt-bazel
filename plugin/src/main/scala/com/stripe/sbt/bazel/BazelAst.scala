package com.stripe.sbt.bazel

import org.typelevel.paiges.Doc

object BazelAst {

  sealed trait PyExpr

  final case class PyCall(
    funName: String,
    args: List[(String, PyExpr)]
  ) extends PyExpr

  final case class PyStr(str: String) extends PyExpr

  final case class PyArr(arr: List[PyExpr]) extends PyExpr

  final case class PyBinOp(name: String, lhs: PyExpr, rhs: PyExpr) extends PyExpr

  final case class PyAssign(varName: String, pyExpr: PyExpr) extends PyExpr

  final case class PyVar(name: String) extends PyExpr

  final case class PyLoad(label: PyExpr, symbols: List[PyExpr]) extends PyExpr

  object Helpers {
    def scalaBinary(
      name: String,
      projectDeps: List[String],
      mainClass: String
    ): PyExpr = {
      PyCall(
        "scala_binary",
        List(
          "name" -> PyStr(name),
          "deps" -> PyArr(projectDeps.map(PyStr)),
          "main_class" -> PyStr(mainClass)
        )
      )
    }

    def scalaLibrary(
      name: String,
      projectDeps: List[String],
      visibility: String,
      sources: List[String]
    ): PyExpr = {
      PyCall(
        "scala_library",
        List(
          "name" -> PyStr(name),
          "deps" -> PyArr(projectDeps.map(PyStr)),
          "visibility" -> PyArr(List(PyStr(visibility))),
          "srcs" -> PyArr(sources.map(PyStr))
        )
      )
    }

    def bind(name: String, actual: String):PyExpr = {
      PyCall("bind",
        List(
          "name" -> PyStr(s"$name"),
          "actual" -> PyStr(actual)
        )
      )
    }

    def mavenJar(jarName: String, mvnCoords: String, repo: String): PyExpr = {
      PyCall("maven_jar",
        List(
          "name" -> PyStr(s"$jarName"),
          "artifact" -> PyStr(s"$mvnCoords"),
          "repository" -> PyStr(repo)
        )
      )
    }

    def workspacePrelude(scalaRulesVersion: String) = List(
      PyAssign("rules_scala_version", PyStr(scalaRulesVersion)),
      PyCall("http_archive",
        List(
          "name" -> PyStr("io_bazel_rules_scala"),
          "url" -> PyBinOp("%",
            PyStr("https://github.com/bazelbuild/rules_scala/archive/%s.zip"),
            PyVar("rules_scala_version")
          ),
          "type" -> PyStr("zip"),
          "strip_prefix" -> PyBinOp("%", PyStr("rules_scala-%s"), PyVar("rules_scala_version"))
        )
      ),
      PyLoad(PyStr("@io_bazel_rules_scala//scala:scala.bzl"),
        List(
          PyStr("scala_repositories")
        )
      ),
      PyCall("scala_repositories", List()),
      PyLoad(PyStr("@io_bazel_rules_scala//scala:toolchains.bzl"),
        List(
          PyStr("scala_register_toolchains")
        )
      ),
      PyCall("scala_register_toolchains", List())
    )

    def buildPrelude: List[PyExpr] = List(
      PyLoad(PyStr("@io_bazel_rules_scala//scala:scala.bzl"),
        List(
          PyStr("scala_binary"),
          PyStr("scala_library"),
          PyStr("scala_test")
        )
      )
    )
  }

  object Render {
    def renderPyExpr(expr: PyExpr): Doc = {
      expr match {
        case PyStr(s) => str(s)
        case PyArr(ar) => arr(ar.map(renderPyExpr))
        case PyBinOp(name, lhs, rhs) => renderPyExpr(lhs) & Doc.text(name) & renderPyExpr(rhs)
        case PyAssign(varName, rhs) => Doc.text(varName) & Doc.char('=') & renderPyExpr(rhs)
        case PyVar(name) => Doc.text(name)
        case PyLoad(label, symbols) =>
          val args = (label +: symbols).map(renderPyExpr)
          Doc.intercalate(Doc.char(',') + Doc.lineOrEmpty, args)
            .tightBracketBy(Doc.text("load("), Doc.char(')'))
        case PyCall(name, args) =>
          val docArgs = args.map { case (k, v) =>
            Doc.text(k) -> renderPyExpr(v)
          }
          pyCall(Doc.text(name), docArgs)
      }
    }

    def renderPyExprs(exprs: List[PyExpr]): Doc = {
      Doc.intercalate(Doc.lineBreak, exprs.map(renderPyExpr))
    }

    def pyArg(name: Doc, value: Doc): Doc = {
      name + Doc.space + Doc.char('=') + Doc.space + value
    }

    def pyArgs(args: List[(Doc, Doc)]): Doc = {
      Doc.intercalate(Doc.char(',') + Doc.lineOrSpace, args.map { case (name, value) => pyArg(name, value) })
    }

    def pyCall(name: Doc, args: List[(Doc, Doc)]): Doc = {
      if (args.length >= 2) {
        pyArgs(args).tightBracketBy(name + Doc.char('('), Doc.char(')'))
      } else if (args.length == 1) {
        Doc.text("name(") + pyArgs(args) + Doc.char(')')
      } else {
        name + Doc.text("()")
      }
    }

    def join(docs: List[Doc]): Doc =
      Doc.intercalate(Doc.line, docs.map(_ + Doc.char(',')))

    def str(value: String): Doc = {
      val escaped = value.replaceAll("'", "\\'")
      Doc.text(s"'$escaped'")
    }

    def arr(entries: List[Doc]): Doc =
      if (entries.isEmpty) Doc.text("[]")
      else join(entries).tightBracketBy(Doc.char('['), Doc.char(']'))
  }
}
