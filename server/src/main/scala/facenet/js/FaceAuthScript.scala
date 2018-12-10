package facenet.js

import scalatags.Text.all._

object FaceAuthScript {
  val webCamera = "web_camera"
  val photo = "photo"
  val buttonId = "takePic"
  val resultId = "auth-result"

  def apply() =
    html(
      head(
        body(
          link(rel := "stylesheet", href := "/assets/lib/bootstrap/css/bootstrap.css"),
          link(rel := "stylesheet", href := "/assets/lib/bootstrap/css/cam.css")),

        script(`type` := "text/javascript", src := "/assets/lib/jquery/jquery.js"),
        script(`type` := "text/javascript", src := "/assets/lib/bootstrap/js/bootstrap.js"),

        script(`type` := "text/javascript", src := "/assets/lib/camera.js"),
        script(`type` := "text/javascript", src := "/assets/lib/webcam.min.js"),

        script(`type` := "text/javascript", src := "/assets/client-jsdeps.js"),
        script(`type` := "text/javascript", src := "/assets/client-opt.js"),

        div(cls := "web-cam centered")(div(id := webCamera)),
        button(id := buttonId, name := "action", `type` := "button", style := "centered")("auth"),
        div(id := resultId, style := "centered"),

        div(id := photo, style := "centered; display: none"),
        script(s"""facenet.FaceAuthApp().main('$webCamera', '$photo', '$buttonId', '$resultId')""")))

}
