package args4c

import java.security.MessageDigest

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object Encryption {
  private lazy val sha = MessageDigest.getInstance("SHA-256")

  //use first 128 bits
  private def asKey(password: Array[Byte]) = {
    java.util.Arrays.copyOf(sha.digest(password), 16)
  }

  def clear(pwd: Array[Byte]): Unit = {
    pwd.indices.foreach(i => pwd.update(i, 'x'.toByte))
  }

  /** @param password    the password used for encrypting the text
    * @param inputString the text to encrypt using AES
    * @return the total bytes and encrypted text
    */
  def encryptAES(password: Array[Byte], inputString: String): (Int, Array[Byte]) = {

    val input = inputString.getBytes("UTF-8")

    val keyBytes: Array[Byte] = asKey(password)

    val sks    = new SecretKeySpec(keyBytes, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, sks)
    val encrypted = Array.ofDim[Byte](cipher.getOutputSize(input.length))
    val encLen    = cipher.update(input, 0, input.length, encrypted, 0)
    val total     = encLen + cipher.doFinal(encrypted, encLen)
    total -> encrypted
  }

  def decryptAES(password: Array[Byte], encrypted: Array[Byte], len: Int): String = {

    val keyBytes: Array[Byte] = asKey(password)
    val sks                   = new SecretKeySpec(keyBytes, "AES")
    val cipher                = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, sks)
    val decrypted = Array.ofDim[Byte](len)
    val decLen    = cipher.update(encrypted, 0, len, decrypted, 0)
    val total     = decLen + cipher.doFinal(decrypted, decLen)
    new String(decrypted, "UTF-8").take(total)
  }

}
