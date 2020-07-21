package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.condition.OS.LINUX;
import static org.junit.jupiter.api.condition.OS.MAC;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * This test only runs on Linux or macOS, and only then if ansible-vault is actually installed
 * in one of the expected locations.
 */
@DisplayName("VaultEncryptionHelper (Integration Test)")
@EnabledOnOs({LINUX, MAC})
class VaultEncryptionHelperIntegrationTest {

    // Location expected on macOS; assuming installed via Homebrew
    private static final String MACOS_HOMEBREW_ANSIBLE_PATH = "/usr/local/bin/ansible-vault";

    // Location expected on Linux; expected installed via yum, apt-get, etc.
    private static final String LINUX_ANSIBLE_PATH = "/usr/bin/ansible-vault";

    private static final String PASSWORD = "password100";

    private static String ansibleVaultFile;

    @BeforeAll
    static void beforeAll() {
        ansibleVaultFile = pathOfAnsibleVault().orElse("/dummy/ansible-vault");
        assumeTrue(Files.exists(Path.of(ansibleVaultFile)), "ansible-vault not found");
    }

    private static Optional<String> pathOfAnsibleVault() {
        if (SystemUtils.IS_OS_MAC) {
            return Optional.of(MACOS_HOMEBREW_ANSIBLE_PATH);
        } else if (SystemUtils.IS_OS_LINUX) {
            return Optional.of(LINUX_ANSIBLE_PATH);
        }
        return Optional.empty();
    }

    private VaultEncryptionHelper helper;
    private VaultConfiguration config;

    @TempDir
    Path tempDirPath;

    @BeforeEach
    void setUp() throws IOException {
        helper = new VaultEncryptionHelper();
        var tempDir = tempDirPath.toString();

        var passwordFilePath = Path.of(tempDir, ".vault_pass");
        Files.writeString(passwordFilePath, PASSWORD);

        config = VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultFile)
                .vaultPasswordFilePath(passwordFilePath.toString())
                .tempDirectory(tempDir)
                .build();
    }

    @Nested
    class EncryptString {

        @ParameterizedTest
        @CsvSource({
                "my_password,password,1-2-3-4-5",
                "the_answer,42",
                "some_variable,67890-12345"
        })
        void shouldEncryptStringInValidFormat(String variableName, String plainText) {
            var encryptedString = helper.encryptString(plainText, variableName, config);
            var variable = new VaultEncryptedVariable(encryptedString);

            assertThat(variable.getVariableName()).isEqualTo(variableName);
        }

        @ParameterizedTest
        @CsvSource({
                "my_password,this is my password",
                "secret_variable,42",
                "another_variable,12345"
        })
        void shouldEncryptAndDecryptStrings(String variableName, String plainText) {
            var encryptedString = helper.encryptString(plainText, variableName, config);
            var decryptedString = helper.decryptString(encryptedString, config);

            assertThat(decryptedString).isEqualTo(plainText);
        }
    }
}