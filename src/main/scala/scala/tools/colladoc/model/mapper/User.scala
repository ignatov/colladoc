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
package model {
package mapper {

import net.liftweb.mapper._
import net.liftweb.common.{Full, Empty, Box}
import net.liftweb.http._
import js.JE.Str
import js.jquery.JqJE.Jq
import js.JsCmd
import js.JsCmds._
import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml.ElemAttr._
import xml.{NodeSeq, Text}
import lib.js.JqUI._
import net.liftweb.widgets.gravatar.Gravatar

/**
 * Mapper for user table storing registered users.
 * @author Petr Hosek
 */
class User extends ProtoUser[User] with OneToMany[Long, User]  {
  def getSingleton = User

  /** Username. */
  object userName extends MappedString(this, 32) {
    override def dbIndexed_? = true
    override def validations = valUnique(S.??("unique.user.name")) _ :: super.validations
    override def displayName = fieldOwner.userNameDisplayName
    override val fieldId = Some(Text("txtFirstName"))
  }

  object openId extends MappedString(this, 100) {
    override def dbIndexed_? = true
  }

  object deleted extends MappedBoolean(this) {
    override def defaultValue = false
  }

  object banned extends MappedBoolean(this) {
    override def defaultValue = false
  }

  object site extends MappedString(this, 255) {
    override def defaultValue = ""
  }

  object location extends MappedString(this, 255) {
    override def defaultValue = ""
  }

  object company extends MappedString(this, 255) {
    override def defaultValue = ""
  }

  def userNameDisplayName = S.??("user.name")

  def deleted_? = deleted.is == true

  /** User comment changes. */
  object comments extends MappedOneToMany(Comment, Comment.user)

  /** Url to user's profile. */
  def profileUrl = "/profile/" + urlEncode(userName)

  /** Hyperlink to user's profile. */
  def profileHyperlink = <a target="template" href={profileUrl}>{userName}</a>

  /** Non absolute url to user's profile. */
  private def profileUrlLocal = "profile/" + urlEncode(userName)

  /** Non absolute hyperlink to user's profile. */
  private def profileHyperlinkLocal = <a target="template" href={profileUrlLocal}>{userName}</a>

  /** Grid entry. */
  def toGridRow =
    <row id={id.toString}>
      <cell>{profileHyperlinkLocal.toString}</cell>
    </row>
}

/**
 * Mapper for user table storing registered users.
 * @author Petr Hosek
 */
object User extends User with KeyedMetaMapper[Long, User] {
  override def dbTableName = "users"

  /** Current logged in user identifier */
  private object curUserId extends SessionVar[Box[String]](Empty)

  /** Get current logged in user identifier */
  def currentUserId: Box[String] = curUserId.is

  /** Current logged in user */
  private object curUser extends RequestVar[Box[User]](currentUserId.flatMap(id => find(id))) with CleanRequestVarOnSessionTransition

  /** Get current logged in user */
  def currentUser: Box[User] = curUser.is

  /** Whether currently logged in user is superuser */
  def superUser_? : Boolean = currentUser.map(_.superUser.is) openOr false

  def banned_? = currentUser.map(_.banned.is) openOr true

  def validSuperUser_? = superUser_? && !User.banned_?

  /** Whether any user is logged in. */
  def loggedIn_? = currentUserId.isDefined
  /** Log in user with given identifier. */
  def logUserIdIn(id: String) {
    curUser.remove()
    curUserId(Full(id))
  }
  /** Log in user. */
  def logUserIn(who: User) {
    curUser.remove()
    curUserId(Full(who.id.toString))
  }

  /** Log out current user. */
  def logoutCurrentUser = logUserOut()
  /** Log out user. */
  def logUserOut() {
    curUserId.remove()
    curUser.remove()
    S.request.foreach(_.request.session.terminate)
  }

  def createIfNew(openid: String): User = {
    find(By(User.openId, openid)).openOr {
      val newUser = User.create.openId(openid)
      newUser.save()
      newUser
    }
  }

  /** Singup user form. */
  def singupUserHtml =
    <lift:form class="user form">
      <fieldset>
        <p>
          <label for="name">Username:</label>
          <user:username class="text required ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="name">Full Name:</label>
          <user:fullname class="text ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="name">Email:</label>
          <user:email class="text required email ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="password">Password:</label>
          <user:password class="text required ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="openid">OpenID:</label>
          <user:openid class="text ui-widget-content ui-corner-all" />
        </p>
        <p>
          <input type="checkbox" value="false" id="agree_input" class="required ui-widget-content ui-corner-all" name="agree" />
          <label for="agree" id="agree_label">I agree to the <a href="http://www.scala-lang.org/sites/default/files/contributor_agreement.pdf" class="link">Contributor Agreement</a></label>
        </p>
        <user:submit />
      </fieldset>
    </lift:form>

  /** Edit user form. */
  def userHtml =
    <lift:form class="user form">
      <fieldset>
        <p>
          <label for="name">Username:</label>
          <user:username class="text required ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="name">Full Name:</label>
          <user:fullname class="text ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="name">Email:</label>
          <user:email class="text required email ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="password">Password:</label>
          <user:password class="text required ui-widget-content ui-corner-all" />
        </p>
        <p>
          <label for="openid">OpenID:</label>
          <user:openid class="text ui-widget-content ui-corner-all" />
        </p>
        <user:submit />
      </fieldset>
    </lift:form>

  /** Edit user dialog. */
  def edit = {
    val user = currentUser.open_!

    def doSave() {
      user.validate match {
        case Nil =>
          S.notice("User successfully saved")
          user.save()
        case n =>
          S.error(n)
      }
    }

    bind("user", userHtml,
      "username" -%> SHtml.text(user.userName.is, text => (), ("readonly", "readonly")),
      "fullname" -%> SHtml.text(user.shortName, name => {
          val idx = name.indexOf(" ")
          if (idx != -1) {
            user.firstName(name.take(idx))
            user.lastName(name.drop(idx + 1))
          } else
            user.firstName(name)
        }),
      "email" -%> SHtml.text(user.email.is, text => (), ("readonly", "readonly")),
      "password" -%> SHtml.password("", user.password(_)),
      "openid" -%> SHtml.text(user.openId.is, text => (), ("readonly", "readonly")),
      "submit" -> SHtml.hidden(doSave _))
  }

  /** Handler for path updating. */
  private def updatePath(path: String): JsCmd = {
    S.notice("Path successfully updated")
    Model.updatePath(path)
    Noop
  }

  /** Form with project properties. */
  private def projectSettings: NodeSeq = {
    var title = Properties.get("-doc-title").getOrElse("")
    var version = Properties.get("-doc-version").getOrElse("")

    def doSave(): JsCmd = {
      Properties.set("-doc-title", title)
      Properties.set("-doc-version", version)
      S.notice("Project settings successfully saved.")
      Noop
    }

    val form =
      <lift:form class="properties">
        <fieldset>
          <p>
            <label for="title">Title:</label>
            <settings:title class="text required ui-widget-content ui-corner-all" />
          </p>
          <p>
            <label for="version">Version:</label>
            <settings:version class="text required ui-widget-content ui-corner-all" />
          </p>
          <settings:submit />
          <settings:save />
          <settings:reset />
        </fieldset>
      </lift:form>

    bind("settings", form,
      "title" -%> SHtml.text(title, title = _, ("id", "title")),
      "version" -%> SHtml.text(version, version = _, ("id", "version")),
      "submit" -> SHtml.hidden(doSave _),
      "save" -> SHtml.a(Text("Save"), SubmitForm(".properties"), ("class", "button")),
      "reset" -> SHtml.a(Text("Reset"), SetValById("title", Str(title)) & SetValById("version", Str(version)), ("class", "button"))
    )
  }

  /** Categories properties. */
  private def categoriesSettings: NodeSeq = {
    var name = ""

    def doSave(): JsCmd = {
      Category.find(By(Category.name, name)) match {
        case Full(c) =>
          S.notice("Category " + name + " already exists.")
          Noop
        case _ =>
          Category.create.name(name).save()
          S.notice("Category " + name + " successfully created.")
          Replace("categories_form", categoriesSettings) & Jq(Str(".button")) ~> Button()
      }
    }

    def categoryToHtml(c: Category) =
      <tr>
        <td>
          {
            SHtml.ajaxText(
              c.name.is,
              text => {
                if (c.name.is != text )
                  c.name(text).save()
                Noop
              }
            )
          }
        </td>
        <td>
          {
            SHtml.ajaxCheckbox(
              c.anonymousView,
              bool => { c.anonymousView(bool).save(); Noop }
            )
          }
        </td>
        <td>
          {
            SHtml.ajaxCheckbox(
              c.anonymousPost,
              bool => { c.anonymousPost(bool).save(); Noop }
            )
          }
        </td>
        <td>
          {
            SHtml.a(
              ColladocConfirm("Confirm delete"),
              () => { c.valid(false).save(); Replace("categories_form", categoriesSettings) & Jq(Str(".button")) ~> Button() },
              SHtml.span(NodeSeq.Empty, Noop, ("class", "ui-icon ui-icon-trash")),
              ("class", "button")
            )
          }
        </td>
      </tr>

    val form =
      <div id="categories_form">
        <lift:form class="category">
          <h3>Categories</h3>
          <table id="categories_table">
            <tr>
              <th>Name</th>
              <th>Anonymous viewable</th>
              <th>Anonymous postable</th>
              <th></th>
            </tr>
            { Category.all map categoryToHtml _ }
            <tr>
              <td colspan="3">
                <category:name class="text required ui-widget-content ui-corner-all" />
              </td>
              <td>
                <category:save />
              </td>
            </tr>
          </table>
          <category:submit />
        </lift:form>
      </div>

    bind("category", form,
      "name" -%> SHtml.text(name, name = _),
      "submit" -> SHtml.hidden(doSave _),
      "save" -> SHtml.a(SHtml.span(NodeSeq.Empty, Noop, ("class", "ui-icon ui-icon-plus")), SubmitFormWithValidation(".category"), ("class", "button"))
    )
  }

  private def userList =
    <div id="user_list">
      <ul>
        {
          User.findAll(OrderBy(User.userName, Ascending)) map { u =>
            <li class={"ui-selectee" + (if (u.deleted_?) " deleted" else if (u.banned.is) " banned" else "")}>
              <div class="profile_link">
                {
                  Gravatar(u.email, 16) ++
                  u.profileHyperlinkLocal ++
                  (if (u.superUser.is) SHtml.span(NodeSeq.Empty, Noop, ("class", "ui-icon ui-icon-lightbulb")) else NodeSeq.Empty)
                }
              </div>
            </li>
          }
        }
      </ul>
    </div>

  /** Admin user form. */
  def adminForm =
    <div id="settings_tab">
      <ul>
        <li><a href="#user_settings">User list</a></li>
        <li><a href="#project_settings">Project settings</a></li>
        <li><a href="#discussions_settings">Discussions</a></li>
      </ul>
      <div id="user_settings">
        <input id="user_filter" class="text ui-widget-content ui-corner-all"/>
        { userList }
      </div>
      <div id="project_settings">
        { projectSettings }
      </div>
      <div id="discussions_settings">
        { categoriesSettings }
      </div>
    </div>

  /** Signup user dialog. */
  def signup = {
    val user = create

    def doSignup() {
      user.validate match {
        case Nil =>
          S.notice("User successfully created")
          user.save()
          logUserIn(user)
          RedirectTo("/")
        case n =>
          S.error(n)
      }
    }

    bind("user", singupUserHtml,
      "username" -%> SHtml.text("", user.userName(_)),
      "fullname" -%> SHtml.text("", name => {
          val idx = name.indexOf(" ")
          if (idx != -1) {
            user.firstName(name.take(idx))
            user.lastName(name.drop(idx + 1))
          } else
            user.firstName(name)
        }),
      "email" -%> SHtml.text("", user.email(_)),
      "password" -%> SHtml.password("", user.password(_)),
      "openid" -%> SHtml.text("", user.openId(_)),
      "submit" -> SHtml.hidden(doSignup _))
  }

  /** Login user form. */
  def loginHtml =
    <div class="login form">
      <lift:form>
        <fieldset>
          <p>
            <label for="name">Username:</label>
            <user:username id="username" class="text required ui-widget-content ui-corner-all" />
          </p>
          <p>
            <label for="password">Password:</label>
            <user:password id="password" class="text required ui-widget-content ui-corner-all" />
          </p>
          <user:submit />
        </fieldset>
      </lift:form>
      <div>
        <ul class="providers">
          <li class="direct" title="Google">
            <a href="javascript:void(0);" id="google"><img src="images/google.png" alt="Sign in with Google"/></a>
          </li>
          <li class="direct" title="Yahoo">
            <a href="javascript:void(0);" id="yahoo"><img src="images/yahoo.png" alt="Sign in with Yahoo"/></a>
          </li>
        </ul>
      </div>
      <a href="javascript:void(0);" id="openid_switcher" class="link">more OpenID</a>
      <form id="openid_form" class="hidden" method="post" action="/openid/login">
        <input id="openid_identifier" type="text" name="openid_identifier" class="text required ui-widget-content ui-corner-all" />
      </form>
    </div>

  /** Login user dialog. */
  def login = {
    var username: String = ""
    var password: String = "*"

    def doLogin = {
      find(By(userName, username)) match {
        case Full(user) if !user.deleted_? && user.password.match_?(password) =>
          S.notice("User logged in")
          logUserIn(user)
          RedirectTo("/")
        case _ =>
          S.error("Invalid user credentials")
      }
    }

    bind("user", loginHtml,
      "username" -%> SHtml.text("", username = _),
      "password" -%> SHtml.password("", password = _),
      "submit" -> SHtml.hidden(doLogin _))
  }

  /** Logout user. */
  def logout = {
    logoutCurrentUser
  }
}

}
}
}
