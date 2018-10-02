package me.samei.xtool.benchkit.v1.domain
import akka.actor.{ Actor, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy, Terminated }
import me.samei.xtool.benchkit.v1.domain.data.Async
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.control.NonFatal

object MainTest {

    type State = Unit
    type Result = Unit

    class Worker(
        val waitFor: data.Millis = 200,
        val id: data.Identity = data.Identity("localhost","zero"),
        override val loggerName : String = "worker"
    ) extends logic.Worker[State, Result] {

        override protected val benchmark : logic.Context =
            new logic.Context.ImplV1(id)

        override protected val state : State = ()

        override protected val scenario : logic.Scenario[State, Result] =
            new logic.Scenario[State, Result] {

                override def desc : data.Description =
                    data.Description("simple", Nil)

                override def apply (
                    context : logic.Context,
                    state : State
                ) : Async[Result] = {
                    Thread.sleep(waitFor)
                    Future.successful(data.Done(()))
                }
            }


        override protected val controller : logic.Controller =
            new logic.Controller.TillTimeV1(
                (10 seconds).toMillis, 1000
            )
    }

    def worker = Props { new Worker() }

    class Super extends Actor {

        private val logger = LoggerFactory.getLogger("super")

        override def supervisorStrategy = OneForOneStrategy() {
            case NonFatal(cause) =>
                context stop self
                SupervisorStrategy.Stop
        }

        override def preStart() = {
            logger.info(s"Start, ${self}")
            val ref = context actorOf (worker,"main")
            context watch ref
        }

        override def receive : Receive = {
            case Terminated(ref) =>
                logger.info(s"STOPPED: ${ref}")
                context stop self
        }

        override def postStop() = {
            logger.info(s"Stop, ${self}")
            context.system.terminate()
        }

    }

    def supervisor = Props { new Super }

    def main(args: Array[String]): Unit = {

        val actorySystem = ActorSystem("benchmark")

        val ref = actorySystem.actorOf(supervisor, "supervisor")

        Await.result(actorySystem.whenTerminated, Duration.Inf)
    }
}
