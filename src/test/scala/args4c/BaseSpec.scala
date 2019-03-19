package args4c
import java.nio.file.{Files, Paths}

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class BaseSpec extends WordSpec with Matchers with BeforeAndAfterAll with LowPriorityArgs4cImplicits {

  override def beforeAll(): Unit = {
    deleteDefaultConfig()
  }
  override def afterAll(): Unit = {
    deleteDefaultConfig()
  }

  def deleteDefaultConfig() = {
    deleteFile(SecretConfig.defaultSecretConfigPath())
  }

  def deleteFile(fileName: String) = {
    val path = Paths.get(fileName)
    if (Files.exists(path)) {
      Files.delete(path)
    }
  }

}
