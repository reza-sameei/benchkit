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
        val waitFor: data.Millis = (10 seconds).toMillis,
        val id: data.Identity = data.Identity("localhost","zero"),
        override val loggerName : String = "worker"
    ) extends logic.Worker[State, Result] {

        override protected def benchmark : logic.Context =
            new logic.Context.ImplV1(id)

        override protected def state : State = ()

        override protected def scenario : logic.Scenario[State, Result] =
            new logic.Scenario[State, Result] {

                override def desc : data.Description =
                    data.Description("simple-thread-sleep", Nil)

                override def apply (
                    context : logic.Context,
                    state : State
                ) : Async[Result] = {
                    Thread.sleep(waitFor)
                    Future.successful(data.Done(()))
                }
            }


        override protected def controller : logic.Controller =
            new logic.Controller.TillTimeV1(
                (1 minutes).toMillis, 3
            )
    }

    def worker = Props { new Worker() }

    class Super extends Actor {

        private val logger = LoggerFactory.getLogger(getClass)

        override def supervisorStrategy = OneForOneStrategy() {
            case NonFatal(cause) =>
                context stop self
                SupervisorStrategy.Stop
        }

        override def preStart() = {
            println(s"Start, ${self}")
            context.actorOf(worker,"main")
        }

        override def receive : Receive = {
            case Terminated(ref) => context stop self
        }

        override def postStop() = {
            println(s"Stop, ${self}")
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
