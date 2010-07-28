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
package scala.tools.colladoc
package lib

import net.liftweb.http.rest.RestHelper
import reflect.NameTransformer
import tools.colladoc.model.Model
import tools.colladoc.model.Model.factory._
import tools.nsc.doc.model._
import xml.{Node, NodeSeq, Null}
import net.liftweb.http.{S, Req, GetRequest}
import java.net.URLDecoder
import util.matching.Regex
import collection.mutable.HashSet

object WebService extends RestHelper {

  serve {
    case Req(path, "xml", GetRequest) => changeSet(path)
  }

  def changeSet(path: List[String]) =
    <scaladoc>
      { new ChangeSetFactory construct(Model.model.rootPackage, path) }
    </scaladoc>

  class ChangeSetFactory {

    protected val visited = HashSet.empty[MemberEntity]

    def construct(pack: Package, path: List[String]): NodeSeq =
      construct(pathToEntity(pack, path))

    def construct(mbr: MemberEntity): NodeSeq =
      <xml:group>
        { if (!visited.contains(mbr)) {
            visited += mbr
            mbr match {
              case tpl: DocTemplateEntity => processTemplate(tpl)
              case _ => processMember(mbr)
            }
          }
        }
      </xml:group>

    protected def processTemplate(tpl: DocTemplateEntity): NodeSeq =
      <xml:group>
        { if (tpl.isUpdated) {
            <item>
              <type>{ tpl match {
                case _ if tpl.isPackage => "package"
                case _ if tpl.isTrait => "trait"
                case _ if tpl.isClass => "class"
                case _ if tpl.isObject => "object"
              }}</type>
              <filename>{ entityToFileName(tpl) }</filename>
              <identifier>{ tpl.qualifiedIdentifier }</identifier>
              <newcomment>{ tpl.comment.get.source.get }</newcomment>
            </item>
          }
        }
        { (tpl.values ++ tpl.abstractTypes ++ tpl.methods) map { processMember(_) } }
        { tpl.members collect { case t: DocTemplateEntity => t } map { construct(_) } }
      </xml:group>

    protected def processMember(mbr: MemberEntity): Node =
      <xml:group>
        { if ((mbr.inheritedFrom.isEmpty || mbr.inheritedFrom.contains(mbr.inTemplate)) && mbr.isUpdated) {
            <item>
              <type>{ mbr match {
                case _ if mbr.isDef || mbr.isVal || mbr.isVar => "value"
                case _ if mbr.isAbstractType || mbr.isAliasType => "type"
              }}</type>
              <filename>{ entityToFileName(mbr) }</filename>
              <identifier>{ mbr.qualifiedIdentifier }</identifier>
              <newcomment>{ mbr.comment.get.source.get }</newcomment>
            </item>
          }
        }
      </xml:group>

    protected def entityToFileName(mbr: MemberEntity) = {
      mbr.symbol match {
        case Some(sym) if sym.sourceFile != null =>
          val path = sym.sourceFile.path.stripPrefix(Model.settings.sourcepath.value)
          if (path.startsWith("/")) path.stripPrefix("/")
          else path
        case _ => ""
      }
    }

    private def pathToEntity(rootPack: Package, path: List[String]): MemberEntity = {
      def doName(mbr: MemberEntity): String = mbr match {
          case tpl: DocTemplateEntity => NameTransformer.encode(tpl.name) + (if (tpl.isObject) "$" else "")
          case mbr: MemberEntity => URLDecoder.decode(mbr.identifier, "UTF-8")
        }
      def downPacks(pack: Package, path: List[String]): (Package, List[String]) = {
        pack.packages.find{ _.name == path.head } match {
          case Some(p) => downPacks(p, path.tail)
          case None => (pack, path)
        }
      }
      def downInner(tpl: DocTemplateEntity, path: List[String]): MemberEntity = path match {
        case p :: r if p.isEmpty => downInner(tpl, r)
        case p :: r =>
          tpl.members.sortBy{ t => -1 * doName(t).length }.find{ t => p.startsWith(doName(t)) } match {
            case Some(t: DocTemplateEntity) => downInner(t, p.stripPrefix(doName(t)).stripPrefix("$") :: r)
            case Some(m: MemberEntity) => m
            case None => tpl
          }
        case Nil => tpl
      }
      downPacks(rootPack, path) match {
        case (pack, "package" :: Nil) => pack
        case (pack, path) => downInner(pack, path)
      }
    }

  }
}