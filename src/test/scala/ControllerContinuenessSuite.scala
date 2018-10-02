
import me.samei.xtool.benchkit.v1.domain.logic.Controller.TillTimeV1
import me.samei.xtool.benchkit.v1.domain.logic.Context
import me.samei.xtool.benchkit.v1.domain.data
import org.scalatest._
import scala.concurrent.duration._

class ControllerContinuenessSuite extends FlatSpec with MustMatchers {

    it must "get apply from ..." in {

        val context = new Context.ImplV1(data.Identity("localhost", "simple_me"))

        val duration = 3 seconds

        val controller = TillTimeV1((3 seconds).toMillis, 10)

        (1 to 9) foreach { i =>
            info(s"Increment Running: ${controller.incRunning} / ${controller.detail}")
            controller.continue(context) mustBe data.ApplyLoad.Continue
        }

        info(s"Increment Running: ${controller.incRunning} / ${controller.detail}")
        controller.continue(context) mustBe data.ApplyLoad.Wait

        info(s"Wait for some duration (${duration})")
        Thread.sleep(duration.toMillis)

        val currentCommand = controller.continue(context)
        info(s"Current Command: ${currentCommand}")

        currentCommand mustBe data.ApplyLoad.End
    }

}
