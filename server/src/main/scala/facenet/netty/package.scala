package facenet

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import io.netty.handler.codec.http.FullHttpResponse

import scala.concurrent.Future

package object netty {

  case class NamedThreadFactory(name: String) extends ThreadFactory {
    private val namePrefix = s"$name-thread"
    private val threadNumber = new AtomicInteger(1)
    private val group: ThreadGroup = Thread.currentThread.getThreadGroup

    override def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, s"$namePrefix-${threadNumber.getAndIncrement}", 0L)
      t.setDaemon(true)
      t
    }
  }

  trait RestCall[T] {
    def run(arg: T): Future[Either[Throwable, FullHttpResponse]]
  }

  object RestCall {
    def apply[T: RestCall]: RestCall[T] = implicitly[RestCall[T]]
  }

}
