package args4c

import java.nio.file.Paths

import com.typesafe.config.ConfigFactory

class PromptTest extends BaseSpec {

  "Prompt.format" should {
    List[Prompt](
      ReadNextKeyValuePair("", ConfigFactory.empty()),
      ReadNextKeyValuePair("required.path", ConfigFactory.empty()),
      ReadNextKeyValuePairAfterError("input"),
      PromptForPassword,
      PromptForUpdatedPassword,
      PromptForExistingPassword(Paths.get("configPath")),
      PromptForConfigFilePermissions,
      SaveSecretPrompt(Paths.get("path"))
    ).foreach { prompt =>
      s"format $prompt" in {
        Prompt.format(prompt) should not be (empty)
      }
    }
  }
}
