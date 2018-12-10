package facenet.shared

object Headers {

  def login(login: String) = s"$login-gantt-login"

  val FromClient = "X-Client-Auth"
  val FromServer = "X-Server-Auth"
}
