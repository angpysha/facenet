package facenet.netty

import io.netty.handler.codec.http._

abstract class UriPath {

  def /(child: String) = new /(this, child)

  def toList: List[String]

  def parent: UriPath

  def lastOption: Option[String]

  def startsWith(other: UriPath): Boolean
}

case object Root extends UriPath {
  def toList: List[String] = Nil

  def parent = this

  def lastOption: Option[String] = None

  override def toString = ""

  def startsWith(other: UriPath) = other == Root
}

case class /(parent: UriPath, child: String) extends UriPath {
  lazy val toList: List[String] = parent.toList ++ List(child)

  def lastOption: Option[String] = Some(child)

  lazy val asString = parent.toString + "/" + child

  override def toString = asString

  def startsWith(other: UriPath) = {
    val components = other.toList
    (toList take components.length) == components
  }
}

object UriPath {

  def apply(uri: String): UriPath = {
    val uriPath = uri.takeWhile(_ != '?')

    if (uriPath == "" || uriPath == "/")
      Root
    else if (!uriPath.startsWith("/"))
      UriPath("/" + uriPath)
    else {
      val slash = uriPath.lastIndexOf('/')
      val prefix = UriPath(uriPath.substring(0, slash))
      if (slash == uriPath.length - 1) {
        prefix
      } else {
        prefix / uriPath.substring(slash + 1)
      }
    }
  }

  def apply(first: String, rest: String*): UriPath =
    rest.foldLeft(Root / first)(_ / _)

  def apply(list: List[String]): UriPath = list.foldLeft(Root: UriPath)(_ / _)

  def unapplySeq(path: UriPath): Option[List[String]] = Some(path.toList)

  def unapplySeq(request: FullHttpRequest): Option[List[String]] =
    Some(UriPath(request.uri).toList)

  def unapply(request: FullHttpRequest): Option[UriPath] =
    Some(UriPath(request.uri))

  def unapply(req: HttpRequest): Option[(HttpMethod, UriPath)] = {
    Some((req.method, UriPath(req.uri)))
  }
}

object -> {
  /**
   * HttpMethod extractor:
   * (request.method, Path(request.path)) match {
   * case Method.Get -> Root / "test.json" => ...
   */
  def unapply(req: FullHttpRequest): Option[(HttpMethod, UriPath)] = {
    Some((req.method, UriPath(req.uri)))
  }

  def unapply(req: DefaultHttpRequest): Option[(HttpMethod, UriPath)] = {
    Some((req.method, UriPath(req.uri)))
  }

  def unapply(req: HttpRequest): Option[(HttpMethod, UriPath)] = {
    Some((req.method, UriPath(req.uri)))
  }
}

protected class NumericPathVar[A](cast: String => A) {
  def unapply(str: String): Option[A] = {
    if (!str.isEmpty)
      try {
        Some(cast(str))
      } catch {
        case _: NumberFormatException =>
          None
      }
    else
      None
  }
}

object IntVar extends NumericPathVar(_.toInt)

object LongVar extends NumericPathVar(_.toLong)

object DoubleVar extends NumericPathVar(_.toDouble)