package org.example.ansible.vault;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import lombok.Builder;

import javax.annotation.Nullable;
import java.util.List;

@Builder
public class VaultEncryptCommand implements OsCommand {

    private final String ansibleVaultPath;
    private final String vaultIdLabel;
    private final String vaultPasswordFilePath;
    private final String plainTextFilePath;

    public static VaultEncryptCommand from(VaultConfiguration configuration, String plainTextFilePath) {
        return from(configuration, null, plainTextFilePath);
    }

    public static VaultEncryptCommand from(VaultConfiguration configuration,
                                           @Nullable String vaultIdLabel,
                                           String plainTextFilePath) {
        checkArgumentNotNull(configuration, "configuration cannot be null");
        checkArgumentNotBlank(plainTextFilePath, "plainTextFilePath cannot be blank");

        return VaultEncryptCommand.builder()
                .ansibleVaultPath(configuration.getAnsibleVaultPath())
                .vaultIdLabel(vaultIdLabel)
                .vaultPasswordFilePath(configuration.getVaultPasswordFilePath())
                .plainTextFilePath(plainTextFilePath)
                .build();
    }

    @Override
    public List<String> getCommandParts() {
        if (isNull(vaultIdLabel)) {
            return List.of(
                    ansibleVaultPath,
                    "encrypt",
                    "--vault-password-file", vaultPasswordFilePath,
                    plainTextFilePath
            );
        }

        return List.of(
                ansibleVaultPath,
                "encrypt",
                "--vault-id", vaultIdArgument(),
                plainTextFilePath
        );
    }

    private String vaultIdArgument() {
        return f("{}@{}", vaultIdLabel, vaultPasswordFilePath);
    }
}
