package org.example.ansible.vault;

import lombok.Builder;

import java.util.List;

@Builder
public class VaultEncryptStringCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String variableName;
    private final String plainText;

    public static OsCommand from(VaultConfiguration configuration, String plainText, String variableName) {
        return VaultEncryptStringCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .variableName(variableName)
                .plainText(plainText)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "encrypt_string", plainText,
                "--vault-password-file", vaultPasswordFilePath,
                "--name", variableName
        );
    }
}
