package facenet.shared

object protocol {

  case class Time(year: Int, m: Int, d: Int)
  case class OrderLine(
    id: String,
    name: String,
    group: String,
    startDt: Time,
    endDt: Time)
  case class Gantt(lines: Seq[OrderLine])

  case class GanttTask(
    id: Int,
    order: Int,
    name: String,
    starts: String,
    ends: String,
    style: String,
    link: String,
    resource: String,
    comp: Int,
    group: Int, //group:indicates whether this is a group task (parent) - Numeric;  0 = normal task, 1 = standard group task, 2 = combined group task
    parent: Int,
    open: Int,
    depends: String,
    notes: String,
    `type`: Int = 0, //0 - Order, 1 - production, 2 - Order details, 3 - production details)
    prod: Int = 0,
    conflicts: List[String] = List.empty)

  case class GanttTasks(tasks: Seq[GanttTask])

  object http {

    object parameters {
      val recordId = "record"
      val orderId = "order"
      val conflictIds = "conflicts"
      val token = "token"

      val plan = "plan"
      val fact = "fact"
    }

    object segments {
      val order = "order"
      val production = "production"
      val conflict = "conflict"
      val details = "details"
    }

  }

}
