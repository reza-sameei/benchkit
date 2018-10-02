package me.samei.xtool.benchkit.v1.domain

import scala.concurrent.Future

object data {

    sealed trait ApplyLoad
    object ApplyLoad {
        case object Continue extends ApplyLoad
        case object Wait extends ApplyLoad
        case object End extends ApplyLoad
    }

    type Millis = Long

    type Count = Int

    case class Snapshot(
        time: Millis,
        running: Int
    )

    case class Detail(
        time: Millis,
        running: Int,
        done: Int,
        fail: Int
    )

    case class Description(
        name: String,
        tags: Seq[String]
    )

    case class Identity(host: String, name: String)

    case class Report(
        desc: Description,
        beforeStart: Snapshot,
        atEnd: Snapshot,
        result: Result[_]
    )

    sealed trait Result[T] { def isSuccessful: Boolean }
    case class Done[T](value: T) extends Result[T] { override val isSuccessful = true }
    case class Fail[T](error: String, cause: Option[Throwable] = None) extends Result[T] { override val isSuccessful = false }

    type Async[T] = Future[Result[T]]

    case class Stop(desc: String) // Don't generate more senarios
    case class Cancel(desc: String) // Ignore remained responses

}


