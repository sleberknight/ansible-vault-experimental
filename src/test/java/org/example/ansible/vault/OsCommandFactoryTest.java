package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OsCommandFactoryTest {

    private EncryptionConfiguration config;
    private OsCommandFactory factory;

    @BeforeEach
    void setUp() {
        config = EncryptionConfiguration.builder()
                .vaultPasswordFilePath("vaultId")
                .ansibleVaultPath("vaultExecutablePath")
                .build();

        factory = new OsCommandFactory(config);
    }

    @Test
    void getOsCommand_Encrypt() {
        var osCommand = factory.getOsCommand(VaultCommandType.ENCRYPT, "My-Secret", "my-secret-name");

        assertThat(osCommand).isExactlyInstanceOf(AnsibleVaultEncryptStringCommand.class);

        assertThat(osCommand.getOsCommandParts()).containsExactly(
                "vaultExecutablePath",
                "encrypt_string",
                "My-Secret",
                "--vault-password-file", "vaultId",
                "--name", "my-secret-name"
        );
    }

    @Test
    void getOsCommand_Decrypt() {
        var osCommand = factory.getOsCommand(VaultCommandType.DECRYPT, "My-Secret", "my-secret-name");

        assertThat(osCommand).isExactlyInstanceOf(AnsibleVaultDecryptCommand.class);

        assertThat(osCommand.getOsCommandParts()).containsExactly(
                "vaultExecutablePath",
                "decrypt",
                "--vault-password-file", "vaultId",
                "--output", "-",
                "My-Secret"
        );
    }

    @Test
    void getOsCommand_IllegalStateException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.getOsCommand(null, "My-Secret", "my-secret-name"))
                .withMessage("type cannot be null");
    }
}