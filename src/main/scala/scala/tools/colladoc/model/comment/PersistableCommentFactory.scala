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
package model
package comment

import tools.nsc.doc.model.comment.{Comment, CommentFactory}
import tools.nsc.doc.model.{MemberEntity, ModelFactory}

import scala.tools.colladoc.model.{Comment => CComment}
import net.liftweb.common.{Full, Empty}
import net.liftweb.mapper._
import java.util.Date

trait PersistableCommentFactory extends UpdatableCommentFactory { thisFactory: ModelFactory with CommentFactory =>

  override def comment(sym: global.Symbol, inTpl: => DocTemplateImpl): Option[Comment] = {
    updatedComment(sym, inTpl) match {
      case Some(com) => global.docComment(sym, com.comment.is)
      case None =>
    }
    super.comment(sym, inTpl)
  }
  
  override def update(mbr: MemberEntity, docStr: String) = {
    val comment = CComment.create
      .qualifiedName(mbr.qualifiedName)
      .comment(docStr)
      .dateTime(new Date)
      .user(User.currentUser.open_!)
    comment.save
    super.update(mbr, docStr)
  }
  
  private def updatedComment(sym: global.Symbol, inTpl: => DocTemplateImpl) = {
    CComment.findAll(By(CComment.qualifiedName, qualifiedName(sym, inTpl)),
      OrderBy(CComment.dateTime, Descending),
      MaxRows(1)) match {
      case List(com: CComment, _*) if com.dateTime.is.getTime > sym.sourceFile.lastModified => Some(com)
      case _ => None
    }
  }

  private def qualifiedName(sym: global.Symbol, inTpl: => DocTemplateImpl) = sym match {
    case s if s.isMethod => inTpl.qualifiedName + "#" + sym.nameString
    case _ => if (inTpl.isRootPackage) sym.nameString else inTpl.qualifiedName + "." + sym.nameString
  }

  implicit def isUpdated(mbr: MemberEntity) = new {
      def isUpdated() = {
        val com: UpdatableComment = mbr.comment.get.asInstanceOf[UpdatableComment]
        updatedComment(com.sym, com.inTpl) match {
          case Some(c) => true
          case None => false
        }
    }
  }
  
}