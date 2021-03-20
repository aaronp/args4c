package args4c

class EncryptionTest extends BaseSpec:

  "Encryption" should {
    "encrypt/decrypt" in {
      val key              = "password".getBytes()
      val original         = "some sensitive data"
      val (len, encrypted) = Encryption.encryptAES(key, original)

      val (_, encrypedWithSamePW)      = Encryption.encryptAES("password".getBytes(), original)
      val (_, encrypedWithDifferentPW) = Encryption.encryptAES("PassWord".getBytes(), original)

      encrypted.toList should not be original.getBytes("UTF-8").toList
      encrypted.toList should not be encrypedWithDifferentPW.toList
      encrypted.toList shouldBe encrypedWithSamePW.toList

      val backAgain = Encryption.decryptAES(key, encrypted, len)
      backAgain shouldBe original
    }
  }
