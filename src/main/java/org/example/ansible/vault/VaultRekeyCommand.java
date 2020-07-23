package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class VaultRekeyCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String encryptedFilePath;
    private final String newVaultPasswordFilePath;

    public static VaultRekeyCommand from(VaultConfiguration configuration,
                                         String encryptedFilePath,
                                         String newVaultPasswordFilePath) {
        return VaultRekeyCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .encryptedFilePath(encryptedFilePath)
                .newVaultPasswordFilePath(newVaultPasswordFilePath)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "rekey",
                "--vault-password-file", vaultPasswordFilePath,
                "--new-vault-password-file", newVaultPasswordFilePath,
                Paths.get(encryptedFilePath).toString()
        );
    }
}
