package org.example.ansible.vault;

import lombok.Builder;

import java.nio.file.Paths;
import java.util.List;

@Builder
public class AnsibleVaultDecryptCommand implements OsCommand {

    private final String vaultId;
    private final String applicationPath;
    private final String secretName;
    private final String encryptedSecretFileName;

    public static OsCommand from(EncryptionConfiguration configuration, String encryptedSecretFileName) {
        return AnsibleVaultDecryptCommand.builder()
                .applicationPath(configuration.getAnsibleVaultPath())
                .vaultId(configuration.getAnsibleVaultIdPath())
                .encryptedSecretFileName(encryptedSecretFileName)
                .build();
    }

    @Override
    public List<String> getOsCommandParts() {
        return List.of(
                applicationPath, "decrypt",
                "--vault-password-file", vaultId,
                "--output", "-",
                Paths.get(encryptedSecretFileName).toString()
        );
    }
}
