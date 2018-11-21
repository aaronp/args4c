package args4c

import java.security.MessageDigest

import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object Encryption {
  private lazy val sha = MessageDigest.getInstance("SHA-1")

  //use first 128 bits
  private def asKey(password: Array[Byte]) = {
    java.util.Arrays.copyOf(sha.digest(password), 16)
  }

  def encryptAES(password: Array[Byte], inputString: String): (Int, Array[Byte]) = {

    val input = inputString.getBytes("UTF-8")

    val keyBytes: Array[Byte] = asKey(password)

    val sks = new SecretKeySpec(keyBytes, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, sks) //, ivSpec)
    val encrypted = Array.ofDim[Byte](cipher.getOutputSize(input.length))
    val encLen = cipher.update(input, 0, input.length, encrypted, 0)
    val total = encLen + cipher.doFinal(encrypted, encLen)
    total -> encrypted
  }

  def decryptAES(password: Array[Byte], encrypted: Array[Byte], len: Int) = {

    val keyBytes: Array[Byte] = asKey(password)
    val sks = new SecretKeySpec(keyBytes, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, sks)
    val decrypted = Array.ofDim[Byte](len)
    val decLen = cipher.update(encrypted, 0, len, decrypted, 0)
    val total = decLen + cipher.doFinal(decrypted, decLen)
    new String(decrypted, "UTF-8").take(total)
  }

}
