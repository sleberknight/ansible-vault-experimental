package org.example.ansible.vault;

import lombok.Builder;

import java.util.List;

@Builder
public class VaultEncryptStringCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultPasswordFilePath;
    private final String secretName;
    private final String secret;

    public static OsCommand from(VaultConfiguration configuration, String key, String secretName) {
        return VaultEncryptStringCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .secretName(secretName)
                .secret(key)
                .build();
    }

    @Override
    public List<String> getOsCommandParts() {
        return List.of(
                ansibleVaultPath,
                "encrypt_string", secret,
                "--vault-password-file", vaultPasswordFilePath,
                "--name", secretName
        );
    }
}
