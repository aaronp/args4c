package args4c

import java.io.File

package object io {

  def deleteDir(file :File) : Boolean = {
    if (file.exists()) {
      if (file.isDirectory) {
        val original = file.listFiles
        val subDel = original.filter(deleteDir).size == original.size
        file.delete && subDel
      } else {
        file.delete()
      }
    } else {
      true
    }
  }

}
