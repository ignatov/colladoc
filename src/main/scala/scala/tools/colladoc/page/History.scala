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
package page {

import lib.util.Helpers._
import lib.util.NameUtils._
import model.Model
import model.Model.factory._
import model.mapper.Comment
import lib.DependencyFactory.model

import net.liftweb.util.Helpers._

import collection.mutable.HashMap
import tools.nsc.doc.model._
import xml.NodeSeq

/**
 * Page with history of all comment changes displayed in the form of timeline.
 * @author Petr Hosek
 */
class History(rootPack: Package) extends Template(rootPack) {

  /** Page title. */
  override val title = "History"

  /** Page body. */
  override val body =
    <body class="history">
      <div id="definition">
        <img src="images/history_big.png" />
        <h1>History</h1>
      </div>
      <div id="template">

        <div id="mbrsel">
          <div id='textfilter'><span class='pre'/><span class='input'><input type='text' accesskey='/'/></span><span class='post'/></div>
          { <div id="order">
              <span class="filtertype">Ordering</span>
              <ol><li class="date in">Date</li><li class="alpha out">Alphabetic</li></ol>
            </div>
          }
          { <div id="date">
              <span class="filtertype">Date</span>
              <ol>
                <li class="from">From</li>
                <li><history:from /></li>
              </ol>
              <ol>
                <li class="to">To</li>
                <li><history:to /></li>
              </ol>
            </div>
          }
          { <div id="user">
              <span class="filtertype">User</span>
              <ol><li class="filter"><history:user /></li></ol>
            </div>
          }
        </div>
        <h3>Changed Members</h3>
        <history:history />
      </div>
    </body>

  /**
   * Renders list of comments to its xhtml representation.
   * @param cmts list of comments
   * @return xhtml comments representation
   */
  def commentsToHtml(cmts: List[Comment]): NodeSeq = {
    def aggregateComments(mbrs: Iterable[MemberEntity]) = {
      val changeset = new HashMap[DocTemplateEntity, List[MemberEntity]] {
        override def default(key: DocTemplateEntity) = Nil
      }
      val timeline = new HashMap[DocTemplateEntity, List[List[MemberEntity]]] {
        override def default(key: DocTemplateEntity) = Nil
      }

      for (mbr <- mbrs) {
        val tpl = mbr match {
          case tpl: DocTemplateEntity => tpl
          case _ => mbr.inTemplate
        }
        if (!changeset(tpl).contains(mbr)) {
          changeset += tpl -> (mbr :: changeset(tpl))
        } else {
          timeline += tpl -> (changeset(tpl) :: timeline(tpl))
          changeset += tpl -> (mbr :: Nil)
        }
      }
      for ((tpl, mbr) <- changeset)
        timeline += tpl -> (mbr :: timeline(tpl))

      timeline
    }

    val mbrs = Comment.changeSets(cmts).map(commentToMember _).collect{ case Some(m) => m }
    <xml:group>
      { aggregateComments(mbrs) flatMap { case (grp, csets) => csets flatMap { mbrs =>
          val tpl = mbrs.filter(_ == grp) match {
            case List(t: DocTemplateEntity, _*) => t
            case Nil => grp
          }
          val dte = tpl.tag match {
            case c: Comment if mbrs.contains(tpl) => c.dateTime.is.getTime
            case _ if (mbrs nonEmpty) => mbrs map{ _.tag } collect{ case c: Comment => c.dateTime.is.getTime } max
          }
          <div class={ "changeset" + (if (tpl.isTrait || tpl.isClass) " type" else " value") } name={ tpl.name } date={ timestamp(dte).toString }>
            <h4 class="definition">
              <a href={ relativeLinkTo(tpl) }><img src={ relativeLinkTo{List(kindToString(tpl) + ".png", "lib")} }/></a>
              <span>{ if (tpl.isRootPackage) "root package" else tpl.qualifiedName }</span>
            </h4>
            <div>
              { if (mbrs.contains(tpl))
                  <xml:group>
                    { signature(tpl, isSelf = true) }
                    { memberToShortCommentHtml(tpl, false) }
                    <div class="fullcomment">{ memberToCommentBodyHtml(tpl, true) }</div>
                  </xml:group>
              }
              { membersToHtml(mbrs filterNot (_ == tpl)) }
            </div>
          </div>
          }
        }
      }
    </xml:group>
  }

  /**
   * Transform comment into corresponding member entity.
   * @param cmt comment to transform
   * @return member entity if found, none otherwise
   */
  def commentToMember(cmt: Comment) = {
    nameToMember(model.vend.rootPackage, cmt.qualifiedName.is) match {
      case Some(m) =>
        val c = Model.factory.parse(m, cmt.comment.is)
        Some(Model.factory.copyMember(m, c)(cmt))
      case None => None
    }
  }

  /**
   *  Renders sequence of member entities to its xhtml representation.
   * @param mbrs sequence of member entities
   * @return xhtml comments representation
   */
  protected def membersToHtml(mbrs: Iterable[MemberEntity]): NodeSeq = {
    val valueMembers = mbrs collect {
      case (tpl: TemplateEntity) if tpl.isObject || tpl.isPackage => tpl
      case (mbr: MemberEntity) if mbr.isDef || mbr.isVal || mbr.isVar => mbr
    }
    val typeMembers = mbrs collect {
      case (tpl: TemplateEntity) if tpl.isTrait || tpl.isClass => tpl
      case (mbr: MemberEntity) if mbr.isAbstractType || mbr.isAliasType => mbr
    }
    val constructors = mbrs collect { case (mbr: MemberEntity) if mbr.isConstructor => mbr }
    <xml:group>
      { if (constructors.isEmpty) NodeSeq.Empty else
          <div id="constructors" class="members">
            <ol>{ constructors map { memberToHtml(_) } }</ol>
          </div>
      }
      { if (typeMembers.isEmpty) NodeSeq.Empty else
          <div id="types" class="types members">
            <ol>{ typeMembers map { memberToHtml(_) } }</ol>
          </div>
      }
      { if (valueMembers.isEmpty) NodeSeq.Empty else
          <div id="values" class="values members">
            <ol>{ valueMembers map { memberToHtml(_) } }</ol>
          </div>
      }
    </xml:group>
  }

  override def memberToHtml(mbr: MemberEntity) = mbr.tag match {
    case cmt: Comment => super.memberToHtml(mbr) \% Map("date" -> timestamp(cmt.dateTime.is).toString)
    case _ => super.memberToHtml(mbr)
  }

  override def memberToCommentBodyHtml(mbr: MemberEntity, isSelf: Boolean, isReduced: Boolean = false) =
    if (isSelf)
      super.memberToCommentBodyHtml(mbr, isSelf, isReduced = true)
    else
      super.memberToCommentBodyHtml(mbr, isSelf, isReduced)

}

}
}