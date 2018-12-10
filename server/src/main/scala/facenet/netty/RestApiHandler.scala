package facenet.netty

import java.io._
import java.util.{ Base64, UUID }
import java.nio.file.{ Files, Paths }
import java.nio.charset.StandardCharsets

import io.netty.channel._
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import HttpVersion._
import HttpResponseStatus._
import facenet.shared.HttpRoutes
import io.netty.channel.ChannelHandler.Sharable
import org.bytedeco.javacpp.opencv_imgcodecs
import org.datavec.image.loader.NativeImageLoader
import org.deeplearning4j.nn.graph.ComputationGraph
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.indexing.NDArrayIndex

import scala.annotation.tailrec
import scala.util.control.NonFatal

object RestApiHandler {
  val JsonContentType = "application/json; charset=utf-8"

  case class Compare2Faces(base64ImageContent: String)

  case class FindOnPicture(base64ImageCnt: String)

  val nativeImageLoader = new NativeImageLoader(96, 96, 3)

  private def transpose(indArray: INDArray): INDArray = {
    val one = Nd4j.create(1, 96, 96)
    one.assign(indArray.get(NDArrayIndex.point(0), NDArrayIndex.point(2)))
    val two = Nd4j.create(1, 96, 96)
    two.assign(indArray.get(NDArrayIndex.point(0), NDArrayIndex.point(1)))
    val three = Nd4j.create(1, 96, 96)
    three.assign(indArray.get(NDArrayIndex.point(0), NDArrayIndex.point(0)))
    Nd4j.concat(0, one, two, three).reshape(Array[Int](1, 3, 96, 96))
  }

  private def forwardPass(indArray: INDArray, cGraph: ComputationGraph) = {
    val output = cGraph.feedForward(indArray, false)
    val embeddings = cGraph.getVertex("encodings")
    val dense = output.get("dense")
    embeddings.setInputs(dense)
    val embeddingValues = embeddings.doForward(false, LayerWorkspaceMgr.builder.defaultNoWorkspace.build)
    //logger.debug("dense = " + dense);
    //logger.debug("encodingsValues = " + embeddingValues);
    embeddingValues
  }

  private def normalize(read: INDArray) = read.div(255.0)

  private def writeImageToFile(bytes: Array[Byte], file: File): Unit = {
    val buffer = new Array[Byte](1024 * 5)
    val in = new ByteArrayInputStream(bytes)
    val out = new FileOutputStream(file)

    @tailrec def readChunk(): Unit = in.read(buffer) match {
      case -1 => ()
      case n =>
        out.write(buffer, 0, n)
        readChunk()
    }

    try readChunk() catch {
      case NonFatal(ex) =>
        throw new Exception(s"Couldn't write to file ${file.getAbsoluteFile} error", ex)
    } finally {
      in.close
      out.flush
      out.close
    }
  }

  def readFile(original: File): Array[Byte] = {
    val buffer = new Array[Byte](1024 * 5)
    val out = new ByteArrayOutputStream()
    val in = new FileInputStream(original)

    @tailrec def readChunk(): Unit = in.read(buffer) match {
      case -1 => ()
      case n =>
        out.write(buffer, 0, n)
        readChunk()
    }

    try readChunk()
    catch {
      case NonFatal(ex) =>
        throw new Exception(s"Couldn't read from file ${original.getAbsoluteFile} error", ex)
    } finally in.close()
    out.toByteArray
  }

  implicit def instance0(implicit ex: ExecutionContext, cGraph: ComputationGraph): RestCall[Compare2Faces] =
    (arg: Compare2Faces) =>
      Future {
        val original = Paths.get("./models/origin/origin.jpg").toFile
        val bytes = Base64.getDecoder.decode(arg.base64ImageContent)

        val path = "./users/temp/" + UUID.randomUUID.toString + ".jpg"
        val temp = Files.createFile(Paths.get(path))
        writeImageToFile(bytes, temp.toFile)

        //doesn't work
        //val originalMat = new Mat(readFile(original), false)
        //val candidateMat = new Mat(bytes, false)

        val originalMat = opencv_imgcodecs.imread(original.getAbsolutePath, 1)
        val candidateMat = opencv_imgcodecs.imread(temp.toFile.getAbsolutePath, 1)

        println("original:" + originalMat.arraySize + " candidate:" + candidateMat.arraySize)

        val encodings0 = forwardPass(normalize(transpose(nativeImageLoader.asMatrix(originalMat))), cGraph)
        val encodings1 = forwardPass(normalize(transpose(nativeImageLoader.asMatrix(candidateMat))), cGraph)
        val euclidean = encodings0.distance2(encodings1)

        if (euclidean < 0.41) println("auth: " + euclidean) else println("Unknown user: " + euclidean)

        //Files.delete(temp)
        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.toByteBuf(s"""{"${HttpRoutes.key}":$euclidean}"""))
        r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
        r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
        r.headers.add("Access-Control-Allow-Origin", "*")
        r.headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE")
        r.headers.add("Access-Control-Allow-Headers", "*")
        Right(r)
      } recover {
        case NonFatal(ex) =>
          ex.printStackTrace
          Left(ex)
      }

  implicit def instance1(implicit ex: ExecutionContext): RestCall[FindOnPicture] =
    (arg: FindOnPicture) =>
      Future {
        ???
      }
}

@Sharable
class RestApiHandler(implicit ex: ExecutionContext, cGraph: ComputationGraph) extends ChannelInboundHandlerAdapter {

  import RestApiHandler._

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    super.channelActive(ctx)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    super.channelInactive(ctx)

  private def sendSimpleErrorResponse(ctx: ChannelHandlerContext, status: HttpResponseStatus): ChannelFuture = {
    val r = new DefaultHttpResponse(HTTP_1_1, status)
    r.headers.set(HttpHeaderNames.CONNECTION, "close")
    r.headers.set(HttpHeaderNames.CONTENT_LENGTH, "0")
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  private def sendErrorResponse(ctx: ChannelHandlerContext, error: Throwable) = {
    val r = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR,
      ByteBufs.toByteBuf(error.getMessage))
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    import facenet.shared.HttpRoutes
    msg match {
      case req: FullHttpRequest =>
        req match {
          case HttpMethod.POST -> Root / "api" / HttpRoutes.compare if req.decoderResult == DecoderResult.SUCCESS =>
            RestCall[Compare2Faces].run(Compare2Faces(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          case HttpMethod.POST -> Root / "api" / HttpRoutes.recognize if req.decoderResult == DecoderResult.SUCCESS =>
            RestCall[FindOnPicture].run(FindOnPicture(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          case other =>
            if (other.method == HttpMethod.GET && (other.uri == AssetsHandler.EmptyPath || other.uri.contains(AssetsHandler.ASSETS_SEGMENTS)))
              ctx.fireChannelRead(msg)
            else
              sendSimpleErrorResponse(ctx, BAD_REQUEST)
        }
    }
  }
}