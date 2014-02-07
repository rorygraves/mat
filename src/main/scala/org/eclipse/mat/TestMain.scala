package org.eclipse.mat

import org.eclipse.mat.parser.internal.SnapshotFactoryImpl
import java.io.File
import org.eclipse.mat.util.IProgressListener
import org.eclipse.mat.util.IProgressListener.Severity

object TestMain extends App {
  val factory = new SnapshotFactoryImpl()
  val snapshot = factory.openSnapshot(new File("/workspace/shelmet/heap.bin"), new java.util.HashMap[String, String](),
    new IProgressListener {
      def sendUserMessage(severity: Severity, message: String, exception: Throwable): Unit = {
        println(s"UserMessage $severity $message,$exception")
      }

      var cancelled = false

      def isCanceled: Boolean = cancelled

      def done() {}

      def worked(work: Int): Unit = {
        println(s"worked $work")
      }

      def setCanceled(value: Boolean): Unit = {
        cancelled = value
      }

      def subTask(name: String): Unit = {
        println(s"subtask $name")
      }

      def beginTask(name: String, totalWork: Int): Unit = {
        println(s"beginTask $name $totalWork")
      }
    })

  System.out.println(snapshot.getClasses)
}
