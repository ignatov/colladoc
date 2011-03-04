package scala.tools.colladoc.snippet

import tools.colladoc.model.SearchIndex
import tools.colladoc.page.Search
import tools.nsc.doc.model.MemberEntity
import xml._
import net.liftweb.util.Helpers._
import net.liftweb.http.SHtml._
import net.liftweb.http.js.{JE, JsCmds, JsExp}
import net.liftweb.http.js.JE._
import scala.{ xml}
import net.liftweb.http.{S, SHtml, RequestVar, StatefulSnippet}
import scala.tools.colladoc.search._
import org.apache.lucene.search._
import scala.collection.JavaConversions._

import org.apache.lucene.search.IterablePaging.TotalHitsRef
import org.apache.lucene.search.IterablePaging
import net.liftweb.common.Empty

/**
 * Search snippet.
 */
class SearchOps extends StatefulSnippet{
  import tools.colladoc.lib.DependencyFactory._
  object queryRequestVar extends RequestVar[String](S.param("q") openOr "")
  object pageRequestVar extends RequestVar[String](S.param("page") openOr "1")

  var hasMember:Boolean = false

  // TODO: Maybe we should set this value in the SearchPage?

  val pageNo:Int = (pageRequestVar.is).toInt
  val resultsPerPage = 30
  var totalResultsPerquery = 0
  var resultsCount = 0


  val dispatch: DispatchIt ={ case "show" => show _
                              case "body" => body _
                              case "sText" => sText
                            }

  def sText (xhtml:NodeSeq): NodeSeq = {
    {Unparsed(searchValue)}
  }

  def iRecCount (xhtml:NodeSeq): NodeSeq = {
    {if (resultsPerPage < totalResultsPerquery) Unparsed("<span id='recCount'>"+resultsPerPage + "</span> out of " + totalResultsPerquery) else Unparsed(totalResultsPerquery + " out of " + totalResultsPerquery)}
  }
  var searchValue = queryRequestVar.is

  lazy val searchPage = new Search(model.vend.rootPackage)

  /** Return history title. */
  def title(xhtml: NodeSeq): NodeSeq =
    Text(searchPage.title)


  def show(xhtml:NodeSeq):NodeSeq = {
      <xml:group>
      <label for="searchText">Search :</label>
      { text(searchValue, v => searchValue = v) % ("size" -> "10") % ("id" -> "searchText") }
      { submit("Go", () => S.redirectTo("search", () => queryRequestVar(searchValue))) }
      </xml:group>

  }

  /** Return history body. */
  def body(xhtml: NodeSeq): NodeSeq = {

    bind("search", searchPage.body,
         "results" -> search(searchValue),
         if (hasMember) {"header" -> searchPage.bodyHeader _ } else {"header" -> <div/>},
         "count" -> iRecCount _
    )
  }
  def search(query : String) :NodeSeq =
  {
    println("Searching for: " + query)
    if(query.trim ne "")
    {
      ScoogleParser.parse(query) match
      {
        case SyntaxError(msg) => errorToHtml(msg)
        case searchQuery:SearchQuery => displayResults(LuceneQuery.toLuceneQuery(searchQuery))
      }
    }
    else
    {
	    errorToHtml("We dont like empty queries")
    }
  }

  def displayResults(query:Query):NodeSeq =
  {
    println("test")
    var searcher : IndexSearcher = null
    try {

      searcher = new IndexSearcher(index.vend.directory, true)
      println("Lucene Query: " + query.toString)
      searchResults(searcher, query, pageNo)
    }
    finally {
      if (searcher != null) { searcher.close() }
    }
  }
   /*
   // Display results without paging
   def displayResults(query:Query):NodeSeq =
  {
    var searcher : IndexSearcher = null
    try {
      val hitsPerPage = 50
      val collector = TopScoreDocCollector.create(hitsPerPage, true)
      searcher = new IndexSearcher(index.vend.directory, true)

      println("Lucene Query: " + query.toString)

      searcher.search(query, collector)

      // Collect the entities that were returned
      val entityResults = collector.topDocs().scoreDocs.map((hit) => {
        val doc = searcher.doc(hit.doc)
        val entitylookupKey = Integer.parseInt(doc.get(SearchIndex.entityLookupField))
        val entityResult = index.vend.entityLookup.get(entitylookupKey)
        entityResult
      })

      println("Results: " + entityResults.toString)
      resultsToHtml(entityResults)
    }
    finally {
      if (searcher != null) {
        searcher.close()
      }
    }
  }
   */

  def searchResults(searcher : IndexSearcher, query : Query, pageNumber : Int)={
    val totalHitsRef = new TotalHitsRef()
		val paging = new IterablePaging(searcher, query, 1000)
    val itemsPerPage = resultsPerPage
    val skipPages = (pageNumber - 1)* itemsPerPage

		val entityResults = paging.skipTo(skipPages).gather(itemsPerPage).
                        totalHits(totalHitsRef).
                        map((hit) => {
        val doc = searcher.doc(hit.doc)
        val entitylookupKey = Integer.parseInt(searcher.doc(hit.doc).
                                      get(SearchIndex.entityLookupField))
        val entityResult = index.vend.entityLookup.get(entitylookupKey)

        entityResult
      })

      println("Results: " + totalHitsRef.totalHits())

      // return total records count. is used to show on screen
      totalResultsPerquery = totalHitsRef.totalHits()
      resultsToHtml(entityResults)

  }



  /** Render search results **/
  def resultsToHtml(members : Iterable[MemberEntity]) = {
    resultsCount = members.size

    <div id="searchResults">
      {
        if (members.nonEmpty) {
        hasMember=true
          searchPage.resultsToHtml(members)

        } else {
         hasMember=false
         <div style="margin:25px 50px;" id="noResults"> Sorry, but the search couldn't find anything to fetch...
           <br/><br/>
           <p>Please try the following: </p>
             <ul style="margin:25px 50px;">
               <li>blah 1</li>
               <li>blah 2</li>
               <li>blah 3</li>
             </ul>
         </div>

        }
      }
    </div>
  }

  def errorToHtml(msg : String) = {

    <div id="searchResults">
         <div style="margin:25px 50px;"> Error with the syntax: {msg}
           <br/>
		       <br/>
             For supported query syntax samples, please refer to the <a href="/syntax.html" onclick="window.open(this.href, 'newWindow', 'height=600, width=500, left=50, top=50, toolbar=no, location=no, directories=no, status=no, menubar=no, scrollbars=no, resizable=no, copyhistory=no,'); return false">Syntax reference</a>
         </div>
    </div>
  }
}

