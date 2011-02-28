package scala.tools.colladoc.page

import tools.nsc.doc.model.{TemplateEntity, MemberEntity, Package, DocTemplateEntity}
import xml.NodeSeq
import scala.tools.colladoc.snippet._
import collection.mutable.HashMap

class Search(rootPack: Package) extends scala.tools.colladoc.page.Template(rootPack) {
  /** Page title. */
  override val title = "Search"

  /** Page body. */
  override val body =
<body class="value" onload="windowTitle();" scroll="no">
      <div id="definition">
            <img src="/images/search_big.png"/>
            <h1>Search for: <lift:SearchOps.sText/></h1>
            <p><a href="#" id="linkURL" style="font-size:10px;color:#ffffff;visibility:visible;">Copy Search URL</a></p>
          </div>
	       <search:header />

         <div style="bottom:0;display:block;position:absolute;width:100%;overflow:auto;top:140pt;">

            <div id="template">
                   <search:pages />
                   <search:results />

             </div>
         </div>
       <div id="tooltip" ></div>
    </body>

 def bodyHeader(xhtml:NodeSeq):NodeSeq = {


          <div id="mbrsel">
          <div id='textfilter'><span class='pre'/><span class='input'><input type='text' accesskey='/'/></span><span class='post'/></div>
          {
            <div id="symboltype">
              <span class="filtertype">Symbol</span>
              <ol>
                <li class="package in">Package</li>
                <li class="type in">Type</li>
                <li class="object in">Object</li>
                <li class="class in">Class</li>
                <li class="trait in">Trait</li>
                <li class="constructor in">Constructor</li>
                <li class="def in">Def</li>
                <li class="val in">Val</li>
                <li class="var in">Var</li>
              </ol>
            </div>
          }
          {
            <div id="visbl">
              <span class="filtertype">Visibility</span>
              <ol><li class="public out">Public</li><li class="all in">All</li></ol>
            </div>
          }
          {
            <div id="impl">
              <span class="filtertype">Impl.</span>
              <ol><li class="concrete in">Concrete</li><li class="abstract in">Abstract</li></ol>
            </div>
          }
        </div>

}
  /**
   * Renders list of comments to its xhtml representation.
   * @param cmts list of comments
   * @return xhtml comments representation
   */
  def resultsToHtml(results: Iterable[MemberEntity]): NodeSeq = {
    // Groups members by containing type.
    def aggregateMembers(mbrs: Iterable[MemberEntity]) = {
      val containingTypeMap = new HashMap[DocTemplateEntity, List[MemberEntity]] {
        override def default(key: DocTemplateEntity) = Nil
      }

      for (mbr <- mbrs) {
        val tpl = mbr match {
          case tpl: DocTemplateEntity => tpl
          case _ => mbr.inTemplate
        }

        // Add this member to the current list of members for this type.
        containingTypeMap(tpl) = (mbr :: containingTypeMap(tpl))
      }

      containingTypeMap
    }

    <xml:group>
      {
        aggregateMembers(results) flatMap { case (containingType, mbrs) =>

          <div class={"searchResult" +
                    (if (containingType.isTrait || containingType.isClass) " type"
                    else " value")
                }>
            <h4 class="definition">
              <a href={ relativeLinkTo(containingType) }>
                  <img src={ relativeLinkTo{List(kindToString(containingType) + ".png", "lib")} }/>
              </a>
              <span>
                {
                  if (containingType.isRootPackage) "root package"
                  else containingType.qualifiedName }
              </span>
            </h4>

            <div>
              { membersToHtml(mbrs) }
            </div>
          </div>
        }
      }
    </xml:group>
  }

  /**
   *  Renders sequence of member entities to its xhtml representation.
   * @param mbrs sequence of member entities
   * @return xhtml comments representation
   */
  def membersToHtml(mbrs: Iterable[MemberEntity]): NodeSeq = {
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
            <ol>{ constructors map {memberToHtml(_) } }</ol>
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
}