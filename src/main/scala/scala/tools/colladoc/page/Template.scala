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
import lib.util.PathUtils._
import lib.js.JqJsCmds._
import lib.js.JqUI._
import lib.widgets.Editor
import model.Model
import model.Model.factory._
import model.mapper.{Comment, User}

import net.liftweb.common._
import net.liftweb.http.SHtml
import net.liftweb.http.js._
import net.liftweb.http.js.jquery.JqJE._
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsCmds._
import net.liftweb.mapper._
import net.liftweb.util.Helpers._

import tools.nsc.doc.model._
import xml.{NodeSeq, Text}
import lib.DependencyFactory._

/**
 * Page containing template entity documentation and user controls.
 * @author Petr Hosek
 */
class Template(tpl: DocTemplateEntity) extends tools.nsc.doc.html.page.Template(tpl) {
  /**
   * Create unique identifier for given entity and position.
   * @param mbr member entity
   * @param pos identifier position
   * @return unique positional identifier
   */
  private def id(mbr: MemberEntity, pos: String) =
    idAttrEncode(hash(mbr.identifier + System.identityHashCode(mbr) + pos))

  override def memberToHtml(mbr: MemberEntity): NodeSeq =
    super.memberToHtml(mbr) \% Map("data-istype" -> (mbr.isAbstractType || mbr.isAliasType).toString)

  override def memberToShortCommentHtml(mbr: MemberEntity, isSelf: Boolean): NodeSeq =
    super.memberToShortCommentHtml(mbr, isSelf) \% Map("id" -> id(mbr, "short"))

  override def memberToInlineCommentHtml(mbr: MemberEntity, isSelf: Boolean) =
    <xml:group>
      { memberToShortCommentHtml(mbr, isSelf) }
      <div class="fullcomment">{ memberToUseCaseCommentHtml(mbr, isSelf) }{ memberToCommentBodyHtml(mbr, isSelf) }</div>
    </xml:group>

  override def memberToCommentBodyHtml(mbr: MemberEntity, isSelf: Boolean, isReduced: Boolean = false) =
    <div id={ id(mbr, "full") }>
      { content(mbr, isSelf, isReduced) }
      { controls(mbr, isSelf) }
    </div>

  /** Render member entity content with controls. */
  private def content(mbr: MemberEntity, isSelf: Boolean, isReduced: Boolean) =
    <div id={ id(mbr, "content") }>
      { super.memberToCommentBodyHtml(mbr, isSelf, isReduced) }
    </div>

  /** Render member entity control. */
  private def controls(mbr: MemberEntity, isSelf: Boolean) =
    <div class="controls">
      { select(mbr, isSelf) }
      { if (User.loggedIn_?)
          edit(mbr, isSelf)
      }
      { if (User.superUser_?) delete(mbr, isSelf) }
      { if (User.superUser_?) selectDefault(mbr, isSelf) }
      { if (User.loggedIn_?) propagateToPredecessors(mbr, isSelf) }
      { export(mbr, isSelf) }
    </div>

  /** Propagate comment from member through the hierarchy of predecessors. */
  private def propagateToPredecessors(mbr: MemberEntity, isSelf: Boolean) = currentComment(mbr) match {
      case Some(comment) =>
        def move(name: String) = {
          if (name != ".push") { // TODO: remove '.push' tag
            val newQualifiedName = mbr.qualifiedName.replace(mbr.inTemplate.qualifiedName, name)
            val usr = User.currentUser.open_!

            comment.qualifiedName(newQualifiedName).user(usr).dateTime(now).changeSet(now).save
            index.vend.reindexEntityComment(mbr)
          }
          Replace(id(mbr, "full"), memberToCommentBodyHtml(mbr, isSelf)) & Run("reinit('#" + id(mbr, "full") + "')") &
          (if (!isSelf) SetHtml(id(mbr, "short"), inlineToHtml(mbr.comment.get.short)) else JsCmds.Noop)
          // TODO: update mbr(qualifiedName)
        }
        val defs = (".push", "Push to predecessor") ::
                mbr.inDefinitionTemplates.filter(x => x != mbr.inTemplate).map(x => (x.qualifiedName, x.qualifiedName))
        if (defs.length > 1)
          SHtml.ajaxSelect(defs, Empty, ColladocConfirm("Confirm propagate"), move _, ("class", "select"))
      case _ =>
    }

  /**
   * Current comment for member.
   * If tag is empty try to load data from database.
   */
  private def currentComment(mbr: MemberEntity): Option[Comment] = mbr.tag match {
    case comment: Comment => Some(comment)
    case _ => Comment.default(mbr.uniqueName) match {
      case None => Comment.latest(mbr.uniqueName)
      case c => c
    }
  }

  /** Default value for select with changesets. */
  private def defaultComment(mbr: MemberEntity) = mbr.tag match {
    case cmt: Comment => Full(cmt.id.is.toString)
    case id: String => Full(id)
    case _ => Comment.default(mbr.uniqueName) match {
      case Some(c) => Full(c.id.is.toString)
      case None => Empty
    }
  }

  /** Render revision selection for member entity. */
  private def select(mbr: MemberEntity, isSelf: Boolean) = {
    def replace(cid: String) = {
      val (cmt, c) = Comment.find(cid) match {
        case Full(c) => (Model.factory.parse(mbr, c.comment.is), c)
        case _ => (mbr.comment.get.original.get, "source")
      }
      val m = Model.factory.copyMember(mbr, cmt)(c)
      
      Replace(id(mbr, "full"), memberToCommentBodyHtml(m, isSelf)) & Run("reinit('#" + id(m, "full") + "')") &
      (if (!isSelf) JqId(Str(id(mbr, "short"))) ~> JqHtml(inlineToHtml(cmt.short)) ~> JqAttr("id", id(m, "short")) else JsCmds.Noop)
    }
    val revs = Comment.revisions(mbr.uniqueName) ::: ("source", "Source Comment") :: Nil
    SHtml.ajaxSelect(revs, defaultComment(mbr), replace _, ("class", "select"))
  }

    /** Render revision selection for member entity. */
  private def selectDefault(mbr: MemberEntity, isSelf: Boolean) = {
    def replace(cid: String) = {
      Comment.find(cid) match {
        case Full(c) => activate(mbr, c)
        case _ =>
      }

      Replace(id(mbr, "full"), memberToCommentBodyHtml(mbr, isSelf)) & Run("reinit('#" + id(mbr, "full") + "')") &
      (if (!isSelf) SetHtml(id(mbr, "short"), inlineToHtml(mbr.comment.get.short)) else JsCmds.Noop)
    }
    val revs = ("source", "Select default") :: Comment.revisions(mbr.uniqueName)
    SHtml.ajaxSelect(revs, Empty, ColladocConfirm("Set as default value?"), replace _, ("class", "select"))
  }

  /** Render edit button for member entity. */
  private def edit(mbr: MemberEntity, isSelf: Boolean) = {
    SHtml.a(doEdit(mbr, isSelf) _, Text("Edit"), ("class", "button"))
  }

  /** Provide edit button logic replacing part of the page with comment editor. */
  private def doEdit(mbr: MemberEntity, isSelf: Boolean)(): JsCmd = {
    def getSource(mbr: MemberEntity) = mbr.comment match {
        case Some(c) => c.source.getOrElse("")
        case None => ""
      }
    Editor.editorObj(getSource(mbr), parse(mbr, isSelf) _, text => update(mbr, text)) match {
      case (n, j) =>
        Replace(id(mbr, "full"),
          <form id={ id(mbr, "form") } class="edit" method="GET">
            <div class="editor">
              { n }
              <div class="buttons">
                { SHtml.ajaxButton(Text("Save"), () => SHtml.submitAjaxForm(id(mbr, "form"), () => save(mbr, isSelf))) }
                { SHtml.a(Text("Cancel"), cancel(mbr, isSelf)) }
              </div>
            </div>
          </form>) & j & Jq(Str("button")) ~> Button()
      case _ => JsCmds.Noop
    }
  }

   /** Render delete button for member entity. */
  private def delete(mbr: MemberEntity, isSelf: Boolean) = {
    if (Comment.revisions(mbr.uniqueName).length > 0)
      SHtml.a(ColladocConfirm("Confirm delete"), doDelete(mbr, isSelf) _, Text("Delete"), ("class", "button"))
  }

  /** Provide delete button logic. */
  private def doDelete(mbr: MemberEntity, isSelf: Boolean)(): JsCmd = {
    def replace(mbr: MemberEntity, isSelf: Boolean) = {
      val (cmt, c) = previousOrSource(mbr)
      val m = Model.factory.copyMember(mbr, cmt)(c)
      Replace(id(mbr, "full"), memberToCommentBodyHtml(m, isSelf)) & Run("reinit('#" + id(m, "full") + "')") &
      (if (!isSelf) JqId(Str(id(mbr, "short"))) ~> JqHtml(inlineToHtml(cmt.short)) ~> JqAttr("id", id(m, "short")) else JsCmds.Noop)
    }

    mbr.tag match {
      case cmt: Comment => cmt.valid(false).save; replace(mbr, isSelf)
      case _ => Noop
    }
  }

  /** Get previous comment or comment from source. */
  private def previousOrSource(mbr: MemberEntity) = {
    Comment.findAll(By(Comment.qualifiedName, mbr.qualifiedName), By(Comment.valid, true),
      OrderBy(Comment.dateTime, Descending), MaxRows(1)) match {
      case List(c: Comment, _*) => (Model.factory.parse(mbr, c.comment.is), c)
      case _ => (mbr.comment.get.original.get, "source")
    }
  }

  /** Parse documentation string input to show comment preview. */
  private def parse(mbr: MemberEntity, isSelf: Boolean)(docStr: String) = {
    val cmt = Model.factory.parse(mbr, docStr)
    <html>
      <head>
        <link href="/lib/template.css" media="screen" type="text/css" rel="stylesheet" />
        <link href="/copreview.css" media="screen" type="text/css" rel="stylesheet" />
      </head>
      <body>
        <div id="comment" class="fullcomment">
          { super.memberToCommentBodyHtml(Model.factory.copyMember(mbr, cmt)(), isSelf) }
        </div>
      </body>
    </html>
  }

  /** Save modified member entity comment. */
  private def save(mbr: MemberEntity, isSelf: Boolean): JsCmd =
    if (Model.reporter.hasWarnings)
      JqId(Str(id(mbr, "text"))) ~> AddClass("ui-state-error")
    else
      Replace(id(mbr, "form"), memberToCommentBodyHtml(mbr, isSelf)) & Run("reinit('#" + id(mbr, "full") + "')") &
      (if (!isSelf) SetHtml(id(mbr, "short"), inlineToHtml(mbr.comment.get.short)) else JsCmds.Noop)

  /** Cancel member entity comment modifications. */
  private def cancel(mbr: MemberEntity, isSelf: Boolean): JsCmd =
    Replace(id(mbr, "form"), memberToCommentBodyHtml(mbr, isSelf)) & Run("reinit('#" + id(mbr, "full") + "')")

  /** Update member entity after comment has been changed. */
  private def update(mbr: MemberEntity, docStr: String) = Model.synchronized {
    Model.reporter.reset
    def doSave() = {
      val usr = User.currentUser.open_!
      Comment.deactivateAll(mbr.uniqueName)
      val cmt = Comment.create.qualifiedName(mbr.uniqueName).comment(docStr).dateTime(now).user(usr).active(true)
      Comment.findAll(By(Comment.qualifiedName, mbr.uniqueName), By(Comment.user, usr), By(Comment.valid, true),
          OrderBy(Comment.dateTime, Descending), MaxRows(1)) match {
        case List(c: Comment, _*) if c.dateTime.is - cmt.dateTime.is < minutes(30) =>
          cmt.changeSet(c.changeSet.is)
        case _ =>
          cmt.changeSet(now)
      }
      cmt.save
      index.vend.reindexEntityComment(mbr)
    }
    mbr.comment.get.update(docStr)
    if (!Model.reporter.hasWarnings) doSave
  }

  /** Activate comment for member entity. */
  private def activate(mbr: MemberEntity, cmt: Comment) = Model.synchronized {
    Model.reporter.reset
    def doSave() = {
      Comment.deactivateAll(mbr.uniqueName)
      cmt.active(true).save
      index.vend.reindexEntityComment(mbr)
    }
    mbr.comment.get.update("" + cmt.comment.is)
    if (!Model.reporter.hasWarnings) doSave
  }

  /** Render export link for member entity. */
  private def export(mbr: MemberEntity, isSelf: Boolean) = mbr match {
    case tpl: DocTemplateEntity =>
      <xml:group>
        <a class="control menu">Export</a>
        <ul style="display: none;">
          <li>{ SHtml.a(doExport(mbr, isSelf, false) _, Text("Symbol Only")) }</li>
          <li>{ SHtml.a(doExport(mbr, isSelf, true) _, Text("All Subsymbols")) }</li>
        </ul>
      </xml:group>
    case _ =>
      SHtml.a(doExport(mbr, isSelf, false) _, Text("Export"), ("class", "control"))
  }

  /** Provide export link logic. */
  private def doExport(mbr: MemberEntity, isSelf: Boolean, rec: Boolean)(): JsCmd = {
    var pars = if (rec) "rec=true" :: Nil else Nil
    mbr.tag match {
      case cmt: Comment => pars ::= "rev=%s" format(cmt.dateTime.is.getTime)
      case _ =>
    }
    val path = memberToPath(mbr, isSelf) + ".xml" + pars.mkString("?", "&", "")
    JsRaw("window.open('%s', 'Export')" format (path))
  }
}
}
}
