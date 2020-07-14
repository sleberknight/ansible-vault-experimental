package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class VaultDecryptCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String secretName;
    private final String encryptedSecretFileName;

    public static OsCommand from(VaultConfiguration configuration, String encryptedSecretFileName) {
        return VaultDecryptCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .encryptedSecretFileName(encryptedSecretFileName)
                .build();
    }

    @Override
    public List<String> getOsCommandParts() {
        return List.of(
                ansibleVaultPath,
                "decrypt",
                "--vault-password-file", vaultPasswordFilePath,
                "--output", "-",
                Paths.get(encryptedSecretFileName).toString()
        );
    }
}
