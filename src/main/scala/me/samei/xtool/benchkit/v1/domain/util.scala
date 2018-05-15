package me.samei.xtool.benchkit.v1.domain

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ ForkJoinPool, ForkJoinWorkerThread, ThreadFactory }

import org.slf4j.{ Logger, LoggerFactory }

import scala.annotation.tailrec
import scala.concurrent.{ BlockContext, CanAwait, ExecutionContext, ExecutionContextExecutor }

object util {

    trait OnPanic extends Thread.UncaughtExceptionHandler

    object OnPanic {

        class StackTraceOnStdout extends OnPanic {

            override def uncaughtException (
                thread : Thread,
                cause: Throwable
            ) : Unit = {
                cause.printStackTrace(System.out)
            }

            override def toString = s"${getClass.getName}"
        }

        class StackTraceOnLogger (logger: Logger) extends OnPanic {

            logger.info(s"Init, Class: ${getClass.getName}")

            override def uncaughtException (
                thread : Thread,
                cause : Throwable
            ) : Unit = {
                logger.warn(
                    s"Unhancled Exception on Thread(Id: ${thread.getId}, Name: ${thread.getName})",
                    cause
                )
            }

            override def toString = s"${getClass.getName}(logger: ${logger.getName})"

        }

        def printStackTrace = new StackTraceOnStdout
        def logStackTrace(logger: Logger) = new StackTraceOnLogger(logger)
        def logStackTrace(name: String) = new StackTraceOnLogger(LoggerFactory.getLogger(name))
    }


    type Cores = ExecutionContextExecutor

    object Cores {

        def apply(name: String, max: data.Count): Cores = {
            val factory = new Stdlib212Factory(
                max, name, true
            )
            ExecutionContext.fromExecutor(
                new ForkJoinPool(max, factory, factory.onPanic, true)
            )
        }

        trait Factory extends ThreadFactory with ForkJoinPool.ForkJoinWorkerThreadFactory

        class Stdlib212Factory(
            val maximum: Int,
            val name: String,
            val daemonic: Boolean,
        ) extends Factory {

            require(name ne null, "DefaultThreadFactory.prefix must be non null")
            require(maximum > 0, "DefaultThreadFactory.maxThreads must be greater than 0")

            private val logger = LoggerFactory.getLogger(name)

            val onPanic = OnPanic.logStackTrace(s"${name}.onpanic")

            private final val currentNumberOfThreads = new AtomicInteger(0)

            @tailrec private final def reserveThread(): Boolean = currentNumberOfThreads.get() match {
                case `maximum` | Int.`MaxValue` => false
                case other => currentNumberOfThreads.compareAndSet(other, other + 1) || reserveThread()
            }

            @tailrec private final def deregisterThread(): Boolean = currentNumberOfThreads.get() match {
                case 0 => false
                case other => currentNumberOfThreads.compareAndSet(other, other - 1) || deregisterThread()
            }

            def wire[T <: Thread](thread: T): T = {
                thread.setDaemon(daemonic)
                thread.setUncaughtExceptionHandler(onPanic)
                thread.setName(s"${name}.thread.${thread.getId}")
                thread
            }

            // As per ThreadFactory contract newThread should return `null` if cannot create new thread.
            def newThread(runnable: Runnable): Thread =
                if (reserveThread())
                    wire(new Thread(new Runnable {
                        // We have to decrement the current thread count when the thread exits
                        override def run() = try runnable.run() finally deregisterThread()
                    })) else null

            def newThread(fjp: ForkJoinPool): ForkJoinWorkerThread =
                if (reserveThread()) {
                    wire(new ForkJoinWorkerThread(fjp) with BlockContext {
                        // We have to decrement the current thread count when the thread exits
                        final override def onTermination(exception: Throwable): Unit = deregisterThread()
                        final override def blockOn[T](thunk: =>T)(implicit permission: CanAwait): T = {
                            var result: T = null.asInstanceOf[T]
                            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker {
                                @volatile var isdone = false
                                override def block(): Boolean = {
                                    result = try {
                                        // When we block, switch out the BlockContext temporarily so that nested blocking does not created N new Threads
                                        BlockContext.withBlockContext(BlockContext.defaultBlockContext) { thunk }
                                    } finally {
                                        isdone = true
                                    }

                                    true
                                }
                                override def isReleasable = isdone
                            })
                            result
                        }
                    })
                } else null
        }


    }





}
