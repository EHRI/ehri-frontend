package com.jmcejuela.scala.xml

/**
Copyright 2015 Juan Miguel Cejuela
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
  */

import java.io._

import scala.xml.dtd.DocType
import scala.xml.{Atom, Group, NamespaceBinding, Node, SpecialNode, Text}

/**
 * # XMLPrettyPrinter
 *
 * Advantages over scala.xml.PrettyPrinter:
 *
 * 1. You can both pretty-format a String or pretty-write directly to a file.
 * 2. pre-formatted elements: specify which nodes you want to be considered as pre-formatted.
 * These are written exactly as they are read, with all the white spaces and new lines.
 * All the other elements are completely stripped of leading & trailing whitespaces.
 *
 * Consider for instance [Polyglot Markup documents](http://dev.w3.org/html5/html-polyglot/html-polyglot.html).
 * You can write in XML with the same behavior as with the HTML <pre> tag.
 * You could also have inlined <span>'s within a <p> without creating spurious break lines.
 *
 * 3. Thread safe: you can have the same (global) object used by different clients in parallel.
 * 4. Not tested, but presumably more efficient in both speed and space
 *
 *
 * ## How to use
 *
 * XMLPrettyPrinter is self-contained and has no dependencies.
 * Copy the [code](https://github.com/jmcejuela/Scala-XML-Pretty-Printer/blob/master/src/main/scala/com/jmcejuela/scala/xml/XMLPrettyPrinter.scala) and drop in to your project.
 *
 *
 * @author Juan Miguel Cejuela
 * @version 1.0.0
 *
 * @param indent: indentation space for a node's subnodes
 * @param pre: elements to be considered as pre-formatted
 */
class XMLPrettyPrinter(indent: Int, pre: String*) {
  require(indent >= 0, "the indent must be greater than or equal to zero")
  require(pre.forall(p => !(p == null || p.isEmpty)), "all pre elements must be non-empty")

  private val preSet = pre.toSet

  /**
   * Pretty-format the node as a String.
   */
  def format(node: Node): String = {
    val out = stringWriter()
    print(node)(out)
    out.toString
  }

  /**
   * Pretty-write the node to given file.
   *
   * @param node to write to file
   * @param docType (optional, defaults to null) DocType to include (like <!DOCTYPE ...)
   * @param addXmlDeclaration true/false (optional, defaults to true). If true, the added declaration is: <?xml version="1.0" encoding="UTF-8"?>
   *
   */
  def write(node: Node, docType: DocType = null, addXmlDeclaration: Boolean = true)(out: Writer): Unit = {
    if (addXmlDeclaration) {
      out write s"""<?xml version="1.0" encoding="${scala.io.Codec.UTF8}"?>"""
      out write ↵
    }
    if (null != docType) {
      out write docType.toString
      out write ↵
    }

    print(node)(out)
    out.close()
  }

  /*---------------------------------------------------------------------------*/

  private val < = '<'
  private val > = '>'
  private val </ = "</"
  private val /> = "/>"
  private val ↵ = "\n" //System.getProperty("line.separator")

  /**
   * Returns the appended text of the node's leaves (children) iff all children are text nodes
   * (or text-like nodes such as Atom[String]). Otherwise return None.
   */
  private def leavesText(node: Node): Option[String] = {
    val sb = new StringBuilder
    def $(children: Seq[Node]): Option[String] = {
      if (children.isEmpty) Some(sb.toString())
      else {
        children.head match {
          case s: Text =>
            sb append s.toString; $(children.tail)
          case a: Atom[_] if a.data.isInstanceOf[String] =>
            sb append a.toString; $(children.tail)
          case _ => None
        }
      }
    }
    $(node.child)
  }

  private def print(node: Node, pscope: NamespaceBinding = null, curIndent: Int = 0, inPre: Boolean = false)(implicit out: Writer): Unit = {
    def whitespaceTrim(x: String) = x.trim
    val preformatted = inPre || node.isInstanceOf[Group] || preSet.contains(node.label) //note, group.label fails
    def ::(x: String): Unit = out write x
    def :::(x: Char): Unit = out write x
    def __() : Unit = (0 until curIndent).foreach(_ => :::(' '))
    def printNodes(nodes: Seq[Node], newScope: NamespaceBinding, newIndent: Int): Unit = {
      nodes.foreach(n => print(n, newScope, newIndent, preformatted))
    }

    node match {
      case _: SpecialNode =>
        if (preformatted) ::(node.toString())
        else ((x: String) => if (!x.isEmpty) {
          __(); ::(x); ::(↵)
        })(whitespaceTrim(node.toString()))
      case Group(group) => printNodes(group, pscope, curIndent)
      case _ =>
        if (!inPre) __()

        if (node.child.isEmpty) {
          ::(leafTag(node))
        }
        else {
          ::(startTag(node, pscope))
          if (preformatted) {
            printNodes(node.child, node.scope, curIndent + indent)
          }
          else {
            val leavesTxt = leavesText(node).map(s => whitespaceTrim(s))
            if (leavesTxt.isDefined) {
              ::(leavesTxt.get)
            }
            else {
              ::(↵)
              printNodes(node.child, node.scope, curIndent + indent)
              __()
            }
          }
          ::(endTag(node))
        }

        if (!inPre) ::(↵)
    }
  }

  /*---------------------------------------------------------------------------*/

  /** These functions were copied outright from [[scala.xml. {Utility, PrettyPrinter}]] */

  private def sbToString(f: (StringBuilder) => Unit): String = {
    val sb = new StringBuilder
    f(sb)
    sb.toString()
  }

  private def leafTag(n: Node): String = {
    def mkLeaf(sb: StringBuilder) {
      sb append <
      n nameToString sb
      n.attributes buildString sb
      sb append />
    }
    sbToString(mkLeaf)
  }

  private def startTag(n: Node, pScope: NamespaceBinding): String = {
    def mkStart(sb: StringBuilder) {
      sb append <
      n nameToString sb
      n.attributes buildString sb
      n.scope.buildString(sb, pScope)
      sb append >
    }
    sbToString(mkStart)
  }

  private def endTag(n: Node): String = {
    def mkEnd(sb: StringBuilder) {
      sb append </
      n nameToString sb
      sb append >
    }
    sbToString(mkEnd)
  }

  /*---------------------------------------------------------------------------*/

  private def fileWriter(file: File): Writer =
    new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), scala.io.Codec.UTF8.toString()))

  private def stringWriter(): Writer = new StringBuilderWriter

  /**
   * Copy from [[org.apache.commons.io.output.StringBuilderWriter]]
   * and translated to Scala without the unnecessary constructors.
   */
  private class StringBuilderWriter extends Writer with Serializable {
    val builder = new StringBuilder()

    override def append(value: Char): Writer = {
      builder.append(value)
      this
    }

    override def append(value: CharSequence): Writer = {
      builder.append(value)
      this
    }

    override def append(value: CharSequence, start: Int, end: Int): Writer = {
      builder.appendAll(value.toString.toCharArray, start, end)
      this
    }

    def close() {}

    def flush() {}

    override def write(value: String) {
      if (value != null) {
        builder.append(value)
      }
    }

    def write(value: Array[Char], offset: Int, length: Int) {
      if (value != null) {
        builder.appendAll(value, offset, length)
      }
    }

    def getBuilder: StringBuilder = builder

    override def toString = builder.toString()
  }

}