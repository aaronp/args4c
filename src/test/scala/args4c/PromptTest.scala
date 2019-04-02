package args4c

import java.nio.file.Paths

class PromptTest extends BaseSpec {

  "Prompt.format" should {
    List[Prompt](
      ReadNextKeyValuePair,
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
