package org.example.ansible.vault;

import static java.util.Objects.isNull;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VaultConfiguration {

    private String ansibleVaultPath;
    private String vaultPasswordFilePath;
    private String tempDirectory;

    @Builder
    public VaultConfiguration(String ansibleVaultPath, String vaultPasswordFilePath, String tempDirectory) {
        this.ansibleVaultPath = ansibleVaultPath;
        this.vaultPasswordFilePath = vaultPasswordFilePath;
        this.tempDirectory = isNull(tempDirectory) ? getJavaTempDir() : tempDirectory;
    }

    private String getJavaTempDir() {
        return System.getProperty("java.io.tmpdir");
    }
}
