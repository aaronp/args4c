package args4c

import java.nio.file.{Files, Path, Paths}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BaseSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with LowPriorityArgs4cImplicits:

  override def beforeAll(): Unit =
    deleteDefaultConfig()
  override def afterAll(): Unit =
    deleteDefaultConfig()

  def deleteDefaultConfig() =
    deleteFile(SecureConfig.defaultSecureConfigPath())

  def deleteFile(fileName: String): Unit = deleteFile(Paths.get(fileName))

  def deleteFile(path: Path): Unit =
    if Files.exists(path) then
      Files.delete(path)

