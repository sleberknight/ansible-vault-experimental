package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class VaultViewCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String encryptedFilePath;

    public static VaultViewCommand from(VaultConfiguration configuration, String encryptedFilePath) {
        return VaultViewCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .encryptedFilePath(encryptedFilePath)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        return List.of(
                ansibleVaultPath,
                "view",
                "--vault-password-file", vaultPasswordFilePath,
                Paths.get(encryptedFilePath).toString()
        );
    }
}
