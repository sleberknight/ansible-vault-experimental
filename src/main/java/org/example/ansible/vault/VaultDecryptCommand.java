package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class VaultDecryptCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String encryptedFilePath;

    public static OsCommand from(VaultConfiguration configuration, String encryptedFilePath) {
        return VaultDecryptCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .encryptedFilePath(encryptedFilePath)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "decrypt",
                "--vault-password-file", vaultPasswordFilePath,
                "--output", "-",
                Paths.get(encryptedFilePath).toString()
        );
    }
}
