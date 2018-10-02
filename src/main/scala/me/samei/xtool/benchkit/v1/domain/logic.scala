package me.samei.xtool.benchkit.v1.domain

import java.util.concurrent.{ ForkJoinPool, ForkJoinWorkerThread }
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ Actor, ActorRef }
import me.samei.xtool.benchkit.v1.domain.data.{ Count, Millis, Snapshot }
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
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

        def concurrencyLimit: data.Count

        def running: data.Count
        def done: data.Count
        def fail: data.Count

        def incRunning: data.Count
        def incDone: data.Count
        def incFail: data.Count

        def detail: data.Detail
        def snapshot: data.Snapshot

        def continue(context: Context): data.ApplyLoad
    }

    object Controller {

        trait BaseV1 extends Controller {

            private val _init = new AtomicInteger(0)
            private val _running = new AtomicInteger(0)
            private val _done = new AtomicInteger(0)
            private val _fail = new AtomicInteger(0)

            override val startedAt : data.Millis = now

            override def now : Millis = System.currentTimeMillis()

            override def running : Count = _running.get

            override def done : Count = _done.get

            override def fail : Count = _fail.get

            override def incRunning : Count = _running.incrementAndGet

            override def incDone : Count = {
                _running.decrementAndGet
                _done.incrementAndGet
            }

            override def incFail : Count = {
                _running.decrementAndGet
                _fail.incrementAndGet
            }

            override def detail : data.Detail = data.Detail(now, running, done, fail)

            override def snapshot : Snapshot = data.Snapshot(now, running)
        }

        case class TillTimeV1 (
            val duration: data.Millis,
            override val concurrencyLimit : data.Count
        ) extends BaseV1 {

            require(duration > 0)

            val till = startedAt + duration

            override def continue (context : Context) = {
                running match {
                    case rs if now >= till => data.ApplyLoad.End
                    case rs if rs >= concurrencyLimit => data.ApplyLoad.Wait
                    case rs => data.ApplyLoad.Continue
                }
            }
        }

        case class TillCountV1(
            till: data.Count,
            override val concurrencyLimit : data.Count
        ) extends BaseV1 {

            require(till > 0)

            override def continue(context: Context) = {
                running match {
                    case rs if rs + done + fail >= till => data.ApplyLoad.End
                    case rs if rs >= concurrencyLimit => data.ApplyLoad.Wait
                    case rs => data.ApplyLoad.Continue
                }
            }
        }

    }

    trait Worker[S, R] extends Actor {

        protected def benchmark: Context
        protected def state: S
        protected def scenario: Scenario[S, R]
        protected def controller: Controller
        protected def reporter: ActorRef = self

        protected  def loggerName: String
        protected val logger = LoggerFactory.getLogger(s"$loggerName")

        def runOne = {
            if (logger.isDebugEnabled()) logger.debug("RunOne, ...")
            val beforeStart = controller.snapshot
            Future{
                if (logger.isDebugEnabled()) logger.debug("RunOne, Apply")
                scenario.apply(benchmark, state)
            }(benchmark.workEC)
                .flatMap{ rsl => rsl }(benchmark.systemEC)
                .onComplete {
                    case Success(result @ data.Done(value)) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Done")
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd, result
                        )
                        reporter ! report
                    case Success(result @ data.Fail(error, cause)) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Error")
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd, result
                        )
                        reporter ! report
                    case Failure(cause) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Fail", cause)
                        val atEnd = controller.snapshot
                        val report = data.Report(
                            scenario.desc, beforeStart, atEnd,
                            data.Fail("Scenario Failure", Some(cause))
                        )
                        reporter ! report
                }(benchmark.workEC)
        }

        @tailrec final def runTill : Boolean = {
            val rsl = controller.continue(benchmark)
            logger.debug(s"RunTill, => ${rsl}, ${self}")
            rsl match {
                case data.ApplyLoad.Continue =>
                    runOne
                    runTill
                case data.ApplyLoad.Wait => false
                case data.ApplyLoad.End => true
            }
        }

        override def preStart(): Unit = {
            logger.debug(s"Start, RunTill, ${self}")
            runTill
        }

        override def postStop(): Unit = {
            logger.debug(s"Stop, ${self}")
        }

        override def receive = {
            case _:data.Report => if (runTill) context stop self
        }
    }


}