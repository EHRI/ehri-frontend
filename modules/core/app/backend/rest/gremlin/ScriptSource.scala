package backend.rest.gremlin

import org.codehaus.groovy.antlr.{GroovySourceAST,SourceBuffer,UnicodeEscapingReader}
import org.codehaus.groovy.antlr.parser.{GroovyLexer,GroovyRecognizer}
import org.codehaus.groovy.antlr.parser.GroovyTokenTypes._

object ScriptSource {}

// 
// This class handles loading and storing Groovy scripts.
// Scripts for Gremlin are written as standard functions.
// The ScriptSource uses Groovy's lexer to obtain the AST
// tree of each source file and extract from it each 
// function name, params, and body.
//
class ScriptSource() {

  private val methods: collection.mutable.Map[String,String] = collection.mutable.Map[String,String]()

  def get(name: String): String = methods.get(name).getOrElse(
        throw new IllegalArgumentException("Unknown script: %s".format(name)))

  private def getAST(code: String): GroovySourceAST = { 
    var buff = new SourceBuffer()
    var read = new UnicodeEscapingReader(new java.io.StringReader(code), buff)
    var lexer = new GroovyLexer(read)
    read.setLexer(lexer)
    var parser = GroovyRecognizer.make(lexer)
    parser.setSourceBuffer(buff)
    parser.compilationUnit()
    parser.getAST().asInstanceOf[GroovySourceAST]
  }

  def loadScript(path: String) = {
    val code = io.Source.fromFile(path).mkString
    var ast = getAST(code)
    while (ast != null) {
      var methodName = ast.childrenOfType(IDENT).get(0)
      var body = ast.childrenOfType(SLIST).get(0)
      var text = code.split("\n").slice(body.getLine, body.getLineLast - 1).mkString("\n").stripMargin
      methods += ((methodName.getText, text))
      ast = ast.getNextSibling().asInstanceOf[GroovySourceAST]
    }
  }
}
