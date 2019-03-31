package args4c
import java.nio.file.Path

import args4c.SecretConfig.defaultPermissions

sealed trait Prompt
case object ReadNextKeyValuePair extends Prompt
case class ReadNextKeyValuePairAfterError(previousInvalidEntry : String) extends Prompt
case object PromptForPassword extends Prompt
case object PromptForUpdatedPassword extends Prompt
case class PromptForExistingPassword(configPath : Path) extends Prompt
case object PromptForConfigFilePermissions extends Prompt
case class SaveSecretPrompt(configPath: String) extends Prompt


object Prompt {
  def stdIn(userInput : String => String) : Prompt => String = {
    val message = (_ : Prompt) match {
      case PromptForPassword => "Config Password:"
      case SaveSecretPrompt(configPath) => s"Save secret config to: [${configPath}]"
      case PromptForUpdatedPassword => "New Config Password (or blank to reuse the existing one):"
      case PromptForExistingPassword(configPath) => s"A config already exists at $configPath, enter password:"
      case ReadNextKeyValuePair => "Add config path in the form <key>=<value> (leave blank when finished):"
      case ReadNextKeyValuePairAfterError(previousInvalidEntry) => s"Invalid key=value pair '$previousInvalidEntry'. Entries should be in the for <path.to.config.entry>=some sensitive value"
      case PromptForConfigFilePermissions => s"Config Permissions: [$defaultPermissions]"
    }

    message.andThen(userInput)
  }
}