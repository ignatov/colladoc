package scala.tools.colladoc.search

import java.util.{ArrayList}
import org.apache.lucene.index.Term
import org.apache.lucene.search._
import org.apache.lucene.search.BooleanClause.Occur
import spans._
import tools.colladoc.model.SearchIndex
import collection.mutable.{ListBuffer, ArrayBuffer}

/**
 * User: Miroslav Paskov
 * Date: 2/9/11
 * Time: 3:25 PM
 */

/**
 * The query translator converts a SearchQuerty to a Lucene Query object that can be executed on a Lucene index.
 *
 * The structure of the queries is closely related to the way classes are indexed. See SearchIndex for more information.
 */
object LuceneQueryTranslator
{
  import SearchIndex._

  implicit def convertToArrayList[T](l:List[T]):ArrayList[T] =
  {
    val array = new ArrayList[T](l.size)
    for(item <- l) { array.add(item) }
    array
  }

  def toLuceneQueryString(syntaxQuery:SearchQuery):String =
  {
    transform(syntaxQuery).toString()
  }

  def toLuceneQuery(syntaxQuery:SearchQuery):Query =
  {
    transform(syntaxQuery)
  }

  def transform(syntaxQuery:SearchQuery):Query =
  {
    syntaxQuery match
    {
      case And(queries) => transformAnd(queries map transform)
      case Or(queries) => transformOr(queries map transform)
      case Not(query) => transformNot(transform(query))
      case Group(query) => transformGroup(transform(query))

      case Comment(words) => transformComment(words map transformWord(commentField))

      case Class(id, ext, withs) => transformTypeEntity(classField)(id, ext, withs)
      case Object(id, ext, withs) => transformTypeEntity(objectField)(id, ext, withs)
      case Trait(id, ext, withs) => transformTypeEntity(traitField)(id, ext, withs)

      case Extends(id) => transformExtends(id)
      case Withs(withs) => transformAnd((withs map transformWith).toList)
      case Package(id) => transformPackage(id)

      case Def(id, params, ret) => transformDef(id, params, ret)

      case Val(id, ret) => transformVal(id, ret)
      case Var(id, ret) => transformVar(id, ret)

      case word:Identifier => transformRootWord(word)

      case _ => null
    }
  }

  def maybeTransformReturn(ret:Option[Type]) : Query =
  {
    ret match
    {
      case None => null
      case Some(retId) => transformType(returnsField)(retId)
    }
  }

  def transformVal(id:Identifier, ret:Option[Type]):Query =
  {
    transformAnd(List(transformOr(List(typeTermQuery(valField), typeTermQuery(varField))), transformWord(nameField)(id), maybeTransformReturn(ret)))
  }

  def transformVar(id:Identifier, ret:Option[Type]):Query =
  {
    transformAnd(List(transformOr(List(typeTermQuery(varField), typeTermQuery(valField))), transformWord(nameField)(id), maybeTransformReturn(ret)))
  }

  /**
   * Returns a tuple - (Parameters Count Query, Parameters Query)
   *
   * The case with no restrictions is handled by the caller.
   */
  def transformMethodParams(params:List[ParamType]):(Query, Query) =
  {
    // A method search query is built as collection of lucene span queries.
    // each AnyParams will produce a new spanQuery "block".
    val allBlocks = new ListBuffer[Query]

    var blockStart = 0
    var blockEnd = 0
    var block:ArrayBuffer[SpanQuery] = null;

    // Minimum and maximum number of parameters is estimated and a nujmeric lucene query is produced.
    var minCount = 0;
    var maxCount = 0;

    def endSpan()
    {
      if(block != null)
      {
        val near = new SpanNearQuery(block.toArray, 0, true)
        val position = new SpanPositionRangeQuery(near, blockStart, maxCount)
        allBlocks += position;

        block = null;
      }
    }

    def addToSpan(sq:SpanQuery)
    {
      if(block == null)
      {
        blockStart = minCount
        blockEnd = maxCount
        block = new ArrayBuffer[SpanQuery]
      }

      block += sq
    }

    for(p <- params)
    {
      p match {
        case AnyParams() => endSpan(); maxCount += 10
        case SimpleType(AnyWord(), List()) => endSpan();  minCount += 1; maxCount +=1;
        case SimpleType(Word(str), List()) => addToSpan(new SpanTermQuery(new Term(methodParams, str))); minCount += 1; maxCount += 1;
        case id:Type => addToSpan(new SpanMultiTermQueryWrapper((transformType(methodParams)(id)).asInstanceOf[WildcardQuery])); minCount += 1; maxCount += 1;
      }
    }

    endSpan()

    val countQuery = NumericRangeQuery.newIntRange(methodParamsCount, minCount, maxCount, true, true)

    // Optimization when searching for methods that have no parameters or without wildcards.
    val paramsQuery = allBlocks.size match
    {
      case 0 => null
      case 1 => allBlocks(0)
      case _ => transformAnd(allBlocks.toList)
    }

    (countQuery, paramsQuery)
  }

  def transformDef(id:Identifier, params:List[List[ParamType]], ret:Option[Type]):Query =
  {
    val flattened = params.flatten(l => l);

    var paramQueries = transformMethodParams(flattened)

    if(params.size == 0)
    {
      paramQueries = (null, null)
    }

    transformAnd(List(typeTermQuery(defField), transformWord(nameField)(id), paramQueries._1, paramQueries._2, maybeTransformReturn(ret)))
  }

  def transformPackage(id:Identifier):Query =
  {
    transformAnd(List(
      typeTermQuery(packageField),
      transformWord(nameField)(id)
    ))
  }

  def transformWith(id:Type):Query =
  {
    transformType(withsField)(id)
  }

  def transformExtends(id:Type):Query =
  {
    transformType(extendsField)(id)
  }

  def transformTypeEntity(typeName:String)(id:Identifier, ext:Option[Type], withs:List[Type]):Query =
  {
    ext match {
      case None => transformAnd(List(typeTermQuery(typeName), transformWord(nameField)(id), transformAnd((withs map transformWith).toList)))
      case Some(base) => transformAnd(List(typeTermQuery(typeName), transformWord(nameField)(id), transformType(extendsField)(base), transformAnd((withs map transformWith).toList)))
    }
  }

  def typeTermQuery(str:String):Query =
  {
    new TermQuery(new Term(typeField, str))
  }

  def transformRootWord(word:Identifier) : Query =
  {
    transformOr(List(
      transformWord(nameField)(word),
      transformWord(commentField)(word)
    ))
  }

  def transformType(columnName:String)(id:ParamType) : Query =
  {
    id match {
      case SimpleType(word, List()) => transformWord(columnName)(word)
      case other => new WildcardQuery(new Term(columnName, typeToString(other)))
    }
  }

  def typeToString(id:ParamType) : String =
  {
    id match
    {
      case SimpleType(str, List()) => idToString(str)
      case SimpleType(str, generics) => idToString(str) + generics.map(typeToString).mkString("[", ",", "]")
      case Tuple(elements) => elements.map(typeToString).mkString("(", ",", ")")
      case Func(List(), ret) => "(*)>" + typeToString(ret)
      case Func(params, ret) => params.flatten(l =>l).map(typeToString).mkString("(", ",", ")>") + typeToString(ret)
      case AnyParams() => "*"
    }
  }

  def idToString(id:Identifier) : String =
  {
    id match {
      case Word(id) =>  id
      case ExactWord(str) => '"' + str + '"'
      case EndWith(str) => "*" + str
      case StartWith(str) => str + "*"
      case Contains(str) => "*" + str + "*"
      case AnyWord() => "*"
    }
  }

  def transformWord(columnName:String)(id:Identifier) : Query =
  {
    id match {
      case ExactWord(str) => {
        val result = new PhraseQuery()
        result.add(new Term(columnName, str))
        result
      }
      case AnyWord() => null
      case Word(str) => new TermQuery(new Term(columnName, str))
      case id => new WildcardQuery(new Term(columnName, idToString(id)))
    }
  }

  def transformComment(words:List[Query]) : Query =
  {
    val result = new BooleanQuery();
    words.filter(_ != null) foreach {result.add(_, Occur.SHOULD)}
    result
  }

  def transformGroup(query:Query):Query = query

  def transformNot(query:Query):Query =
  {
    val result = new BooleanQuery();
    result.add(query, Occur.MUST_NOT)
    result
  }

  /**
   * Creates a boolean OR query from the non-null queries in the list.
   */
  def transformOr(queries:List[Query]):Query =
  {
    val result = new BooleanQuery();
    queries.filter(_ != null) foreach {result.add(_, Occur.SHOULD)}
    result
  }

  /**
   * Creates a boolean AND query from the non-null queries in the list.
   */
  def transformAnd(queries:List[Query]):Query =
  {
    val result = new BooleanQuery();
    queries.filter(_ != null) foreach {result.add(_, Occur.MUST)}
    result.getClauses().size match {
      case 0 => null
      case _ => result
    }
  }
}

