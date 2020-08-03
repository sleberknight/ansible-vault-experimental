package org.example.ansible.vault;

import static java.util.Objects.isNull;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * This is mutable in case it is used in injected configuration, e.g. in a Dropwizard configuration file.
 */
@Getter
@Setter
public class VaultConfiguration {

    private String ansibleVaultPath;
    private String vaultPasswordFilePath;
    private String tempDirectory;

    public VaultConfiguration() {
        this.tempDirectory = getJavaTempDir();
    }

    @Builder
    public VaultConfiguration(String ansibleVaultPath, String vaultPasswordFilePath, String tempDirectory) {
        this.ansibleVaultPath = ansibleVaultPath;
        this.vaultPasswordFilePath = vaultPasswordFilePath;
        this.tempDirectory = isNull(tempDirectory) ? getJavaTempDir() : tempDirectory;
    }

    private String getJavaTempDir() {
        return System.getProperty("java.io.tmpdir");
    }

    public VaultConfiguration copyOf() {
        return VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultPath)
                .vaultPasswordFilePath(vaultPasswordFilePath)
                .tempDirectory(tempDirectory)
                .build();
    }
}
