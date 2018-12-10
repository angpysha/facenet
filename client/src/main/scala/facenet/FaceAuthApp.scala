package facenet

import org.scalajs.dom

import scala.scalajs.js.Dynamic.{ global => jscript }
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLImageElement
import org.querki.jquery._

import scala.scalajs.js.JSON
import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext.Implicits.global

@JSExport
object FaceAuthApp {
  val photoId = "user_img"
  val timeoutMillis = 3000

  def auth(destPhotoDiv: String, resultId: String) = {
    dom.console.log("snapshot has taken")
    jscript.takeSnapshot(destPhotoDiv, photoId)

    val image = dom.document.getElementById(photoId).asInstanceOf[HTMLImageElement]
    val base64ImageContent = image.src.substring(23) //drop prefix
    val start = System.currentTimeMillis
    Ajax.post(
      url = "/api/" + facenet.shared.HttpRoutes.compare,
      data = base64ImageContent,
      timeout = timeoutMillis)
      .map(r => JSON.parse(r.responseText))
      .onComplete {
        case Success(parsedJson) =>
          val latency = System.currentTimeMillis - start
          val json = parsedJson.asInstanceOf[scala.scalajs.js.Dictionary[scala.scalajs.js.Any]]
          val result = json(facenet.shared.HttpRoutes.key).toString
          $(s"#$resultId").html(s"<h6>$result</h6>")
          dom.console.log(s"latency: $latency millis - $result")
        case Failure(error) =>
          dom.console.log(s"error: ${error.getMessage}")
      }
  }

  @JSExport
  def main(where: String, destPhotoDiv: String, buttonId: String, resultId: String): Unit = {
    $(dom.document).ready(() => {
      jscript.startCamera(s"#$where")
      /*val loginButton =
        button(id := takeAPic, name := "action",
          cls := "btn waves-effect waves-light", `type` := "button")("TakeAPic")
      */
      $(s"#$buttonId").click(() => auth(destPhotoDiv, resultId))

      /*dom.window.setTimeout({ () =>
        dom.console.log("snapshot has taken")
        jscript.takeSnapshot(destPhotoDiv, photoId)

        val image = dom.document.getElementById(photoId).asInstanceOf[HTMLImageElement]
        val base64ImageContent = image.src.substring(23)

        Ajax.post(
          url = "/api/" + facenet.shared.HttpRoutes.compare,
          data = base64ImageContent,
          timeout = timeoutMillis)
          .map(r => JSON.parse(r.responseText))
          .onComplete {
            case Success(parsedJson) =>
              val json = parsedJson.asInstanceOf[scala.scalajs.js.Dictionary[scala.scalajs.js.Any]]
              val result = json(facenet.shared.HttpRoutes.key).toString
              $("#auth-result").html(s"<h4>$result</h4>")
              dom.console.log(result)
            case Failure(error) =>
              dom.console.log(s"error: ${error.getMessage}")
          }

        /*val reqSettings: JQueryAjaxSettings =
          Dynamic.literal(
            method = "POST",
            data = base64ImageCnt,
            url = "/api/" + facematch.shared.HttpRoutes.compare,
            success = (data: scala.scalajs.js.Any, textStatus: String, headers: JQueryXHR) => {
              val json = data.asInstanceOf[scala.scalajs.js.Dictionary[scala.scalajs.js.Any]]
              dom.console.log(json(facematch.shared.HttpRoutes.key).toString)
              //dom.console.log(JSON.parse(data.toString))
            },
            error = (jqXHR: JQueryXHR, textStatus: String, errorCode: String) => {
              dom.console.log(s"jqXHR=$jqXHR,text=$textStatus,http_code:$errorCode")
            }).asInstanceOf[JQueryAjaxSettings]

        $.ajax(reqSettings)
        */
      }, 5000l)
      */
    })
  }
}