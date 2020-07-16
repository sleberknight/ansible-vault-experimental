package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VaultDecryptCommand")
class VaultDecryptCommandTest {

    private VaultConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = VaultConfiguration.builder()
                .ansibleVaultPath("/opt/ansible/ansible-vault")
                .vaultPasswordFilePath("~/.vault_pass")
                .tempDirectory("/opt/ansible/tmp")
                .build();
    }

    @Test
    void shouldBuildCommand() {
        var encryptedFileName = "MySecret.txt";
        var command = VaultDecryptCommand.from(configuration, encryptedFileName);

        assertThat(command.getCommandParts()).containsExactly(
                configuration.getAnsibleVaultPath(),
                "decrypt",
                "--vault-password-file",
                configuration.getVaultPasswordFilePath(),
                "--output",
                "-",
                encryptedFileName
        );
    }
}