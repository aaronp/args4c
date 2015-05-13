package args4c.io

import java.io.{OutputStream, FileOutputStream, OutputStreamWriter, File}

import scala.compat.Platform._
import scala.io.Source
import scala.sys.process.ProcessLogger
import scala.util.Properties

object IoImplicits {

  implicit class FileAsRichFile(val file : java.io.File) extends AnyVal {

    def text : String = {
      val src = Source.fromFile(file)
      try {
        src.getLines.mkString(EOL)
      } finally {
        src.close()
      }
    }

    private def write(os : OutputStream, content : String) = {
      val osw = new OutputStreamWriter(os)
      try {
        osw.write(content)
        osw.flush()
      } finally {
        osw.close()
      }
    }

    def append(content : String): Unit = write(new FileOutputStream(file, true), content)
    def text_=(content : String): Unit = write(new FileOutputStream(file, false), content)
  }

  implicit class RichFileString(val str : String) extends AnyVal {
    def asFile : java.io.File = new java.io.File(str)
  }
}
