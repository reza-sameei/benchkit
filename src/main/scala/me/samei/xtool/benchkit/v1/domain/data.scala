package me.samei.xtool.benchkit.v1.domain

import scala.concurrent.Future

object data {

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

    sealed trait Result[T]
    case class Done[T](value: T) extends Result[T]
    case class Fail[T](error: String, cause: Option[Throwable] = None) extends Result[T]

    type Async[T] = Future[Result[T]]

    case class Cancel(desc: String)

}


