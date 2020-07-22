package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VaultConfiguration")
class VaultConfigurationTest {

    @Test
    void shouldUseTempDirectoryIfSupplied() {
        var config = VaultConfiguration.builder()
                .ansibleVaultPath("/usr/bin/ansible-vault")
                .vaultPasswordFilePath("/data/vault/.vault_pass")
                .tempDirectory("/data/vault/tmp")
                .build();

        assertThat(config.getTempDirectory()).isEqualTo("/data/vault/tmp");
    }

    @Test
    void shouldAssignTempDirectoryIfNotSupplied() {
        var config = VaultConfiguration.builder()
                .ansibleVaultPath("/usr/bin/ansible-vault")
                .vaultPasswordFilePath("/data/vault/.vault_pass")
                .build();

        assertThat(config.getTempDirectory()).isEqualTo(System.getProperty("java.io.tmpdir"));
    }
}