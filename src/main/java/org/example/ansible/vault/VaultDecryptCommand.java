package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class VaultDecryptCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String encryptedFileName;

    public static OsCommand from(VaultConfiguration configuration, String encryptedFileName) {
        return VaultDecryptCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .encryptedFileName(encryptedFileName)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "decrypt",
                "--vault-password-file", vaultPasswordFilePath,
                "--output", "-",
                Paths.get(encryptedFileName).toString()
        );
    }
}
