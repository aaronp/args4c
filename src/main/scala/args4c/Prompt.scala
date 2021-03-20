package args4c
import java.nio.file.Path

import args4c.SecureConfig.defaultPermissions
import com.typesafe.config.Config

import scala.io.StdIn

/**
  * Represents a request for user input when configuring the 'sensitive configurations'
  */
sealed trait Prompt
// marker trait for prompts for the user password
sealed trait PasswordPrompt extends Prompt

case class ReadNextKeyValuePair(requiredConfigPath: String, secureConfig: Config) extends Prompt
case class ReadNextKeyValuePairAfterError(previousInvalidEntry: String)           extends Prompt
case object PromptForPassword                                                     extends PasswordPrompt
case object PromptForUpdatedPassword                                              extends PasswordPrompt
case class PromptForExistingPassword(configPath: Path)                            extends PasswordPrompt
case object PromptForConfigFilePermissions                                        extends Prompt
case class SaveSecretPrompt(configPath: Path)                                     extends Prompt

object Prompt:

  /** @param userInput a function which accepts user input
    * @return a UserInput from the user input
    */
  def stdIn(userInput: String => String = StdIn.readLine(_)): UserInput = (format _).andThen(userInput)

  def format(prompt: Prompt): String =
    prompt match
      case PromptForPassword                                                          => "Config Password:"
      case PromptForUpdatedPassword                                                   => "New Config Password (or blank to reuse the existing one):"
      case PromptForExistingPassword(configPath)                                      => s"A config already exists at $configPath, enter password:"
      case SaveSecretPrompt(configPath)                                               => s"Save secure config to: [${configPath}]"
      case ReadNextKeyValuePair(requiredConfigPath, _) if requiredConfigPath.nonEmpty => s"$requiredConfigPath :"
      case ReadNextKeyValuePair(_, _)                                                 => "Add config path in the form <key>=<value> (leave blank when finished):"
      case ReadNextKeyValuePairAfterError(previousInvalidEntry) =>
        s"Invalid key=value pair '$previousInvalidEntry'. Entries should be in the for <path.to.config.entry>=some sensitive value"
      case PromptForConfigFilePermissions => s"Config Permissions: [$defaultPermissions]"
