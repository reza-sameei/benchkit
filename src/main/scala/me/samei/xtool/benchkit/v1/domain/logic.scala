package me.samei.xtool.benchkit.v1.domain

import java.util.concurrent.{ ForkJoinPool, ForkJoinWorkerThread }
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorRef
import me.samei.xtool.benchkit.v1.domain.data.{ Count, Millis, Snapshot }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object logic {

    trait Context {
        def systemEC: ExecutionContextExecutor
        def workEC: ExecutionContextExecutor
        def startedAt: data.Millis
        def now: data.Millis
        def identity: data.Identity
    }

    object Context {

        class ImplV1(override val identity: data.Identity) extends Context {

            override def systemEC = util.Cores("system", 1)

            override def workEC = util.Cores("system", 3)

            override val startedAt : Millis = System.currentTimeMillis()

            override def now : Millis = System.currentTimeMillis()
        }
    }

    trait Scenario[S,R] {
        def desc: data.Description
        def apply(context: Context, state: S): data.Async[R]
    }

    /**
      * Eventual Consistency between value (running + don + fail)
      */
    trait Controller {

        def startedAt: data.Millis
        def now: data.Millis
        def running: data.Count
        def done: data.Count
        def fail: data.Count

        def plusRunning: data.Count
        def plusDone: data.Count
        def plusFail: data.Count

        def detail: data.Detail
        def snapshot: data.Snapshot
        def continue(context: Context): Boolean
    }

    object Controller {

        trait BaseV1 extends Controller {

            private val _running = new AtomicInteger(0);
            private val _done    = new AtomicInteger(0);
            private val _fail    = new AtomicInteger(0);

            override val startedAt : data.Millis = now

            override def now : Millis = System.currentTimeMillis()

            override def running : Count = _running.get

            override def done : Count = _done.get

            override def fail : Count = _fail.get

            override def plusRunning : Count = _running.incrementAndGet

            override def plusDone : Count = {
                _running.decrementAndGet
                _done.incrementAndGet
            }

            override def plusFail : Count = {
                _running.decrementAndGet
                _fail.incrementAndGet
            }

            override def detail : data.Detail = data.Detail(now, running, done, fail)

            override def snapshot : Snapshot = data.Snapshot(now, running)
        }

        case class TillTimeV1 (
            val till : data.Millis
        ) extends BaseV1 {

            require(till > startedAt)

            override def continue (context : Context) : Boolean = now < till
        }

        case class TillCountV1(
            till: data.Count
        ) extends BaseV1 {

            require(till > 0)

            override def continue(context: Context): Boolean = running + done + fail < till
        }

    }

    trait Worker[S, R] {
        def context: Context
        def state: S
        def scenario: Scenario[S, R]
        def controller: Controller
        def reporter: ActorRef

        def run = {
            val beforeStart = controller.snapshot
            Future{
                scenario.apply(context, state)
            }(context.workEC)
                .flatMap{ rsl => rsl }(context.systemEC)
                .onComplete {
                    case Success(result @ data.Done(value)) =>
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd, result
                        )
                        reporter ! report
                    case Success(result @ data.Fail(error, cause)) =>
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd, result
                        )
                        reporter ! report
                    case Failure(cause) =>
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd,
                            data.Fail("Scenario Failure", Some(cause))
                        )
                        reporter ! report
                }
        }

        def recieve = {
            case _:data.Report =>
                if (controller.continue(context)) run
            case _:data.Cancel =>

        }
    }


}