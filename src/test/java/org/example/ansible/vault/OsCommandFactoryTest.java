package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OsCommandFactoryTest {

    private static final String ANSIBLE_VAULT_PATH = "/opt/ansible/ansible-vault";
    private static final String PASSWORD_FILE_PATH = "/path/to/password_file";

    private OsCommandFactory factory;

    @BeforeEach
    void setUp() {
        var config = VaultConfiguration.builder()
                .ansibleVaultPath(ANSIBLE_VAULT_PATH)
                .vaultPasswordFilePath(PASSWORD_FILE_PATH)
                .build();

        factory = new OsCommandFactory(config);
    }

    @Test
    void getOsCommand_Encrypt() {
        var osCommand = factory.getOsCommand(VaultCommandType.ENCRYPT, "My-Secret", "my-secret-name");

        assertThat(osCommand).isExactlyInstanceOf(VaultEncryptStringCommand.class);

        assertThat(osCommand.getOsCommandParts()).containsExactly(
                ANSIBLE_VAULT_PATH,
                "encrypt_string",
                "My-Secret",
                "--vault-password-file", PASSWORD_FILE_PATH,
                "--name", "my-secret-name"
        );
    }

    @Test
    void getOsCommand_Decrypt() {
        var osCommand = factory.getOsCommand(VaultCommandType.DECRYPT, "My-Secret", "my-secret-name");

        assertThat(osCommand).isExactlyInstanceOf(VaultDecryptCommand.class);

        assertThat(osCommand.getOsCommandParts()).containsExactly(
                ANSIBLE_VAULT_PATH,
                "decrypt",
                "--vault-password-file", PASSWORD_FILE_PATH,
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