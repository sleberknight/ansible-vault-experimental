package org.example.ansible.vault;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiStrings.f;

import lombok.Builder;

import java.util.List;

@Builder
public class VaultEncryptStringCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultIdLabel;
    private final String vaultPasswordFilePath;
    private final String variableName;
    private final String plainText;

    public static VaultEncryptStringCommand from(VaultConfiguration configuration,
                                                 String plainText,
                                                 String variableName) {
        return from(configuration, null, plainText, variableName);
    }

    public static VaultEncryptStringCommand from(VaultConfiguration configuration,
                                                 String vaultIdLabel,
                                                 String plainText,
                                                 String variableName) {
        return VaultEncryptStringCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultIdLabel(vaultIdLabel)
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .variableName(variableName)
                .plainText(plainText)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        if (isNull(vaultIdLabel)) {
            return List.of(
                    ansibleVaultPath,
                    "encrypt_string",
                    "--vault-password-file", vaultPasswordFilePath,
                    "--name", variableName,
                    plainText
            );
        }

        return List.of(
                ansibleVaultPath,
                "encrypt_string",
                "--vault-id", vaultIdArgument(),
                "--name", variableName,
                plainText
        );
    }

    private String vaultIdArgument() {
        return f("{}@{}", vaultIdLabel, vaultPasswordFilePath);
    }
}
