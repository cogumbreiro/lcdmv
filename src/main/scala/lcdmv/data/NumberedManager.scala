package lcdmv.data

import scala.collection.mutable.{ArrayBuffer, HashMap, LinkedHashMap}

/**
 * Numbered objects are usually managed by this (and extended) class
 */
@SerialVersionUID(1L)
trait NumberedManager[T<:Numbered[_]] extends Serializable {
  protected val canonicalMap = new LinkedHashMap[T, T] // non-id object -> with-id object mapping
  protected val objects = new ArrayBuffer[T]   // id -> object mapping

  def values: Seq[T] = objects
  def size = objects.size

  protected def newId = objects.size
  protected def addEntry(original:T, withId:T): Unit = {
    canonicalMap += original -> withId
    objects += withId
  }
  def apply(i:Int) = objects(i)
  def assignID(original:T): T = {
    require(original.id == 0, "Object given to assignID " + original + " have already had an id: " + original.id)
    canonicalMap.get(original) match {
      case Some(withId) => withId
      case None =>
        val withId = createWithId(original)
        addEntry(original, withId)
        withId
    }
  }
  protected def createWithId(original:T): T // this is a helper called from assignID; please implement this to receive non registered original object and then return the object with id assigned

  type Input
  type GetType

  def getOrCreate(input:Input): T // how to convert input -> object (with id assigned)
  def get(input:Input): GetType // get without create. If not found, return special object for unknown object, which type can be defined in sub-classes (GetType); user may return Option[T] if not found, but in some cases (such as Word), one may want predefined unknown token.

  protected def getOrNone(input:Input): Option[T] // this defines what input is the un-registered object.

  def unknown: GetType // this return the value of get() when it failed.

  override def toString = "NumberedManager {" +
  objects.zipWithIndex.map { case (o, i)=>(i, o) }.mkString("\n") +
  "}"
}

// This is convenient when treating object which have 1-to-1 correspondens to input string -> object (usual case).
// To use this trait, please implement `createWithId` and `createCanonicalInstance` appropriately.
trait StringBaseNumberedManager[T<:Numbered[_]] extends NumberedManager[T] {
  protected val str2objIndex = new HashMap[String, Int]
  override type Input = String
  override def getOrCreate(str:String): T = str2objIndex.get(str) match {
    case Some(i) => objects(i)
    case None =>
      val obj = assignID(createCanonicalInstance(str))
      str2objIndex += str -> obj.id
      obj
  }
  override def getOrNone(str:String): Option[T] = str2objIndex.get(str) map(objects(_)) // please override this when one want to change the search behavior of objects; see CategoryManager for example.

  def createCanonicalInstance(str:String): T
}

// please use with StringBaseNumberedManager
trait UnkObjectReturner[T<:Numbered[_]] {
  type GetType = T
  def unknown: T
  def getOrNone(str:String): Option[T]
  def get(str:String): GetType = getOrNone(str) match {
    case Some(obj) => obj
    case None => unknown // unknown case
  }
}
// TODO: this is experimental; may be used when one want to treat rare-words with converted surface forms
trait UnkWithTemplateReturner[T<:Numbered[_]] extends UnkObjectReturner[T] {

  def getOrCreate(str:String): T
  override def get(str:String): GetType = getOrNone(str) match {
    case Some(obj) => obj
    case None => {
      val convertedStr = extractTemplate(str)
      super.get(convertedStr) // prevent infinite recurse
    }
  }
  protected def extractTemplate(original:String): String

  def registTemplateFor(original: String) = getOrCreate(extractTemplate(original))
}

trait OptionReturner[T<:Numbered[_]] {
  type GetType = Option[T]
  def unknown = None
  def getOrNone(str:String): Option[T]
  def get(str:String): GetType = getOrNone(str)
}
