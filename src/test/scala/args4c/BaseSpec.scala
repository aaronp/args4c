package args4c
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class BaseSpec extends WordSpec with Matchers with BeforeAndAfterAll with LowPriorityArgs4cImplicits {

  override def beforeAll(): Unit = {
    import eie.io._
    SecretConfig.defaultSecretConfigPath().asPath.delete()
  }
  override def afterAll(): Unit = {
    import eie.io._
    SecretConfig.defaultSecretConfigPath().asPath.delete()
  }
}
