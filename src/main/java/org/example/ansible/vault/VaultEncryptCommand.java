package org.example.ansible.vault;

import lombok.Builder;

import java.util.List;

@Builder
public class VaultEncryptCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String plainTextFilePath;

    public static VaultEncryptCommand from(VaultConfiguration configuration, String plainTextFilePath) {
        return VaultEncryptCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .plainTextFilePath(plainTextFilePath)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "encrypt",
                "--vault-password-file", vaultPasswordFilePath,
                plainTextFilePath
        );
    }
}
