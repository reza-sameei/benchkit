
import me.samei.xtool.benchkit.v1.domain.logic.Controller.{TillTimeV1, TillCountV1}
import me.samei.xtool.benchkit.v1.domain.logic.Context
import me.samei.xtool.benchkit.v1.domain.data
import org.scalatest._

import scala.concurrent.duration._

class ControllerContinuenessSuite extends FlatSpec with MustMatchers {

    it must "test continue on Time base controller" in {

        val context = new Context.ImplV1(data.Identity("localhost", "simple_me"))

        val duration = 3 seconds

        val controller = TillTimeV1((3 seconds).toMillis, 10)

        (1 to 9) foreach { i =>
            info(s"Increment Running: ${controller.incRunning} / ${controller.detail}")
            controller.continue(context) mustBe data.ApplyLoad.Continue
        }

        info(s"Increment Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Wait

        info(s"Increment Done: ${controller.incDone} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue

        info(s"Increment Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Wait

        info(s"Wait for some duration (${duration})")
        Thread.sleep(duration.toMillis)

        val currentCommand = controller.continue(context)
        info(s"Current Command: ${currentCommand}")

        currentCommand mustBe data.ApplyLoad.End
    }


    it must "test continute on Count base controller" in {

        val context = new Context.ImplV1(data.Identity("localhost", "simple_me"))

        val count = 7

        val controller = new TillCountV1(count, 3)

        info(s"Just Detail: ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue

        (1 to 2) foreach { _ =>
            info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
            controller.continue(context) mustBe data.ApplyLoad.Continue
        }

        info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Wait

        info(s"Inc Done: ${controller.incDone} / ${controller.detail}")
        info(s"Inc Fail: ${controller.incDone} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue

        info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue
        info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Wait


        info(s"Inc Done: ${controller.incDone} / ${controller.detail}")
        info(s"Inc Fail: ${controller.incDone} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue

        info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Continue
        info(s"Inc Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.End

        val currentCommand = controller.continue(context)
        info(s"Current Command: ${currentCommand}")

        currentCommand mustBe data.ApplyLoad.End
    }


}
