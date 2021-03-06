/*
 * Copyright (c) 2010, Petr Hosek. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and
 *     the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *     and the following disclaimer in the documentation and/or other materials provided with the
 *     distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COLLABORATIVE SCALADOC PROJECT ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COLLABORATIVE SCALADOC
 * PROJECT OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package scala.tools.colladoc {
package lib {
package util {

import xml._

/**
 * Provides utility functions for working with xml.
 * @author Petr Hosek
 */
trait XmlHelpers {

  /** Extends element with additional operators for adding attributes and child nodes. */
  implicit def addNode(elem: Elem) = new {
    def %(attrs: Map[String, String]) = {
      val seq = for((n, v) <- attrs) yield new UnprefixedAttribute(n, v, Null)
      (elem /: seq) { _ % _ }
    }

    def \+(newChild: Node) = elem match {
      case Elem(prefix, labels, attrs, scope, child @ _*) =>
        Elem(prefix, labels, attrs, scope, child ++ newChild : _*)
    }
  }

  /** Extends node sequence with additional operators for adding attributes and child nodes. */
  implicit def addNodeSeq(seq: NodeSeq) = new {
    def \%(attrs: Map[String, String]) = seq theSeq match {
      case Seq(elem: Elem, rest @ _*) =>
        elem % attrs ++ rest
      case elem => elem
    }

    def \\+(newChild: Node) = seq theSeq match {
      case Seq(elem: Elem, rest @ _*) =>
        elem \+ newChild ++ rest
      case elem => elem
    }
  }

}

}
}
}