package me.samei.xtool.benchkit.v1.domain

import java.util.concurrent.{ ForkJoinPool, ForkJoinWorkerThread }
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ Actor, ActorRef }
import me.samei.xtool.benchkit.v1.domain.data._
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

        class ImplV1(
            override val identity: data.Identity,
            val systemECCap: Int =  1,
            val workECCap : Int = 4
        ) extends Context {

            // override def systemEC = util.Cores("system", 1)
            override def systemEC =
                ExecutionContext.fromExecutor(new ForkJoinPool(systemECCap))

            // override def workEC = util.Cores("system", 3)
            override def workEC =
                ExecutionContext.fromExecutor(new ForkJoinPool(workECCap))

            override val startedAt : Millis =
                System.currentTimeMillis()

            override def now : Millis =
                System.currentTimeMillis()
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

        def concurrencyLimit: data.Count

        def running: data.Count
        def done: data.Count
        def fail: data.Count

        def incRunning: data.Count
        def incDone: data.Count
        def incFail: data.Count

        def detail(context: Context): data.Detail
        def snapshot(context: Context): data.Snapshot
        def continue(context: Context): data.ApplyLoad
    }

    object Controller {

        trait BaseV1 extends Controller {

            private val _init = new AtomicInteger(0)
            private val _running = new AtomicInteger(0)
            private val _done = new AtomicInteger(0)
            private val _fail = new AtomicInteger(0)

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

            override def detail(context: Context) : data.Detail =
                data.Detail(context.now, running, done, fail)

            override def snapshot(context: Context) : Snapshot =
                data.Snapshot(context.now, running)
        }

        case class TillTimeV1 (
            val duration: data.Millis,
            override val concurrencyLimit : data.Count
        ) extends BaseV1 {

            require(duration > 0)

            override def continue (context : Context) = {
                running match {
                    case rs if context.now >= context.startedAt + duration => data.ApplyLoad.End
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

        protected val benchmark: Context
        protected val state: S
        protected val scenario: Scenario[S, R]
        protected val controller: Controller
        protected val reporter: ActorRef = self

        protected  def loggerName: String
        protected val logger = LoggerFactory.getLogger(s"$loggerName")

        def runOne(controller: Controller, context: Context, state: S) = {

            if (logger.isDebugEnabled()) logger.debug("RunOne, Intro")
            val beforeStart = controller.snapshot(context)

            Future{
                scenario.apply(benchmark, state)
            }(benchmark.workEC)
                .flatMap{ rsl => rsl }(benchmark.systemEC)
                .onComplete {

                    case Success(result @ data.Done(value)) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Done")
                        val report = genReport(beforeStart, context, result)
                        reporter ! report

                    case Success(result @ data.Fail(error, cause)) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Error")
                        val report = genReport(beforeStart, context, result)
                        reporter ! report

                    case Failure(cause) =>
                        if (logger.isDebugEnabled()) logger.debug("RunOne, Fail", cause)
                        val report = genReport(
                            beforeStart, context,
                            data.Fail("Scenario Failure", Some(cause))
                        )
                        reporter ! report

                }(benchmark.workEC)
        }

        def genReport(beforeStart: Snapshot, context: Context, result: Result[_]) = {
            val atEnd = controller.snapshot(context)
            data.Report(scenario.desc, beforeStart, atEnd, result)
        }

        @tailrec final def runTill(controller: Controller) : Boolean = {
            controller.continue(benchmark) match {
                case data.ApplyLoad.Continue =>
                    controller.incRunning
                    runOne(controller, benchmark, state)
                    runTill(controller)
                case data.ApplyLoad.Wait => false
                case data.ApplyLoad.End => true
            }
        }

        def handleReport(report: Report): Unit = {

            if (report.result.isSuccessful)
                controller.incDone else controller.incFail
        }

        def logState = {
            logger.info(s"CurrentState, Running: ${controller.running}, Done: ${controller.done}, Fail: ${controller.fail}, Now: ${controller.now}")
        }

        override def preStart(): Unit = {
            logger.debug(s"Start, RunTill, ${self}")
            runTill(controller)
        }

        override def postStop(): Unit = {
            logger.debug(s"Stop, ${self}")
        }

        override def receive = {

            case report:data.Report =>

                logger.debug(s"${report}")
                handleReport(report)

                controller.continue(benchmark) match {

                    case data.ApplyLoad.Continue =>
                        logger.debug("Apply more concurrent load ...")
                        runTill(controller)

                    case data.ApplyLoad.Wait =>
                        logger.debug("Wait for a little time ....")

                    case data.ApplyLoad.End if controller.running > 0 =>
                        logger.debug("Should to wait to finish on going requests...")

                    case data.ApplyLoad.End =>
                        logger.debug("END")
                        context stop self
                }

                logState
        }
    }


}