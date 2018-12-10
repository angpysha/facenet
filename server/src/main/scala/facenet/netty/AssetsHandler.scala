package facenet.netty

import java.io._
import java.net.{ URL, URLDecoder }
import java.text.SimpleDateFormat
import java.time.{ ZoneId, ZoneOffset }
import java.util._

import facenet.netty.ResourceFile.AssetFile
import io.netty.buffer.Unpooled
import io.netty.channel.{ ChannelFutureListener, _ }
import io.netty.handler.codec.http.HttpMethod.GET
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import AssetsHandler._
import io.netty.channel.ChannelHandler.Sharable

object AssetsHandler {
  val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"
  val HTTP_DATE_UTC_TIMEZONE = "UTC"
  val HTTP_CACHE_SECONDS = 120
  val defaultClassLoader = classOf[AssetsHandler].getClassLoader

  val JS = "js"
  val CSS = "css"
  val ICO = "ico"
  val HTML = "text/html; charset=utf-8"
  val PNG = "png"

  def parseMimeType(ext: String) = {
    if (ext == JS) "text/javascript; charset=UTF-8"
    else if (ext == CSS) "text/css; charset=UTF-8"
    else if (ext == ICO) "image/x-icon"
    else if (ext == PNG) "image/x-png"
    else ""
  }

  val EmptyPath = "/"
  val ASSETS_SEGMENTS = "/assets/"
  val PUBLIC_FOLDER = "public/"
}

@Sharable
class AssetsHandler extends SimpleChannelInboundHandler[FullHttpRequest] {

  override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest): Unit = {
    if (!request.decoderResult.isSuccess) {
      sendError(ctx, BAD_REQUEST)
    } else if (request.method ne GET) {
      sendError(ctx, METHOD_NOT_ALLOWED)
    } else {
      val uri = request.uri
      val path = sanitizeUri(uri)
      if (path.isEmpty || path == EmptyPath) {
        val appPage = facenet.js.FaceAuthScript().render
        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.toByteBuf(appPage))
        r.headers.set(HttpHeaderNames.CONTENT_TYPE, HTML)
        r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
        ctx.channel().writeAndFlush(r).addListener(ChannelFutureListener.CLOSE)
      } else if (!uri.endsWith(EmptyPath)) {
        val assertUri = uri.replace(ASSETS_SEGMENTS, PUBLIC_FOLDER)
        Option(defaultClassLoader.getResource(assertUri)) flatMap ResourceFile.apply match {
          case Some(AssetFile(url, length, lastModified)) =>
            val mimeType = parseMimeType(assertUri.substring(assertUri.lastIndexOf('.') + 1, assertUri.length))
            writeAsset(url, assertUri, length, mimeType, lastModified, ctx)
          case _ =>
            println(s"Unknown content: $uri")
            sendError(ctx, NOT_FOUND)
        }
      } else {
        //ctx.fireChannelRead(request)
        println(s"Doesn't support folders: $uri")
        sendError(ctx, NOT_FOUND)
      }
    }
  }

  private def writeAsset(url: URL, resource: String, fileLength: Long, mimeType: String,
    lastModified: Long, ctx: ChannelHandlerContext) = {
    val _ =
      try {
        //val bts = org.apache.commons.io.IOUtils.toByteArray(url.openStream)
        val bts = ByteBufs.chunkedBlockingRead(url.openStream, resource)
        val resp = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.bytesToByteBuf(bts))
        resp.headers.set(HttpHeaderNames.CONTENT_TYPE, mimeType)
        resp.headers.set(HttpHeaderNames.CONTENT_LENGTH, resp.content.readableBytes)
        setDateAndCacheHeaders(resp, lastModified)
        ctx.channel().writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE)
      } catch {
        case ex: Exception =>
          ex.printStackTrace()
          ctx.channel().writeAndFlush(sendError(ctx, INTERNAL_SERVER_ERROR)).addListener(ChannelFutureListener.CLOSE)
      }
  }

  private def sanitizeUri(uri: String) =
    URLDecoder.decode(uri, CharsetUtil.UTF_8.name).replace('/', File.separatorChar)

  private def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus): Unit = {
    val response = new DefaultFullHttpResponse(HTTP_1_1, status,
      Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8))
    response.headers.set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
    // Close the connection as soon as the error message is sent.
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  private def setDateAndCacheHeaders(response: HttpResponse, lastModified: Long): Unit = {
    val dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT)
    dateFormatter.setTimeZone(TimeZone.getTimeZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)))

    // Date header
    val time = new GregorianCalendar
    response.headers.set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime))
    // Cache headers
    time.add(Calendar.SECOND, HTTP_CACHE_SECONDS)
    response.headers.set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime))
    response.headers.set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS)
    response.headers.set(HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(lastModified)))
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    if (ctx.channel.isActive) sendError(ctx, INTERNAL_SERVER_ERROR)
  }
}