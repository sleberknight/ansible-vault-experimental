package org.example.ansible.vault;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.ansible.vault.Utils.subListExcludingLast;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.ansible.vault.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.base.process.ProcessHelper;
import org.kiwiproject.collect.KiwiLists;
import org.mockito.ArgumentMatcher;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * This test mocks out the actual ansible-vault invocations. It therefore tests everything except the
 * actual ansible-vault command execution, which is OK for a unit test.
 * <p>
 * {@link VaultEncryptionHelperIntegrationTest} makes actual calls to ansible-vault, assuming it exists.
 */
@DisplayName("VaultEncryptionHelper")
class VaultEncryptionHelperTest {

    private static final String ENCRYPT_STRING_1_1_FORMAT = "ansible-vault/encrypt_string_1.1.txt";

    // This is the variable name in the above encrypted file
    private static final String VARIABLE_NAME = "db_password";

    @TempDir
    Path folder;

    private VaultEncryptionHelper helper;
    private VaultConfiguration configuration;
    private ProcessHelper processHelper;
    private Process process;

    @BeforeEach
    void setUp() throws IOException {
        var vaultFilePath = Files.createFile(Path.of(folder.toString(), "ansible-vault"));
        var passwordFilePath = Files.createFile(Path.of(folder.toString(), ".vault_pass.txt"));
        Files.writeString(passwordFilePath, "password100");

        configuration = VaultConfiguration.builder()
                .ansibleVaultPath(vaultFilePath.toString())
                .vaultPasswordFilePath(passwordFilePath.toString())
                .tempDirectory(folder.toString())
                .build();

        processHelper = mock(ProcessHelper.class);
        process = mock(Process.class);
        helper = new VaultEncryptionHelper(processHelper);
    }

    @Nested
    class EncryptFile {

        @Test
        void shouldReturnEncryptedPath_WhenSuccessful() {
            mockOsProcess(processHelper, process, 0, null, "Encryption successful");

            var plainTextFile = "/data/etc/secrets.yml";

            var encryptedFile = helper.encryptFile(plainTextFile, configuration);

            assertThat(encryptedFile).isEqualTo(Path.of(plainTextFile));

            var command = VaultEncryptCommand.from(configuration, plainTextFile);
            verify(processHelper).launch(command.getCommandParts());
        }

        @Test
        void shouldThrowException_WhenExitCodeIsNonZero() {
            var errorOutput = "ERROR! input is already encrypted";
            mockOsProcess(processHelper, process, 1, null, errorOutput);

            var plainTextFile = "/data/etc/secrets.yml";

            assertThatThrownBy(() -> helper.encryptFile(plainTextFile, configuration))
                    .isExactlyInstanceOf(VaultEncryptionException.class)
                    .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);

            var command = VaultEncryptCommand.from(configuration, plainTextFile);
            verify(processHelper).launch(command.getCommandParts());
        }
    }

    @Nested
    class DecryptFile {

        @Nested
        class InPlace {

            @Test
            void shouldReturnDecryptedPath_WhenSuccessful() {
                mockOsProcess(processHelper, process, 0, null, "Decryption successful");

                var encryptedFile = "/data/etc/secrets.yml";

                var decryptedFile = helper.decryptFile(encryptedFile, configuration);

                assertThat(decryptedFile).isEqualTo(Path.of(encryptedFile));

                var command = VaultDecryptCommand.from(configuration, encryptedFile);
                verify(processHelper).launch(command.getCommandParts());
            }

            @Test
            void shouldThrowException_WhenExitCodeIsNonZero() {
                var errorOutput = "ERROR! input is not vault encrypted data/etc/secrets.yml is not a vault encrypted file for /etc/secrets.yml";
                mockOsProcess(processHelper, process, 1, null, errorOutput);

                var encryptedFilePath = "/etc/secrets.yml";

                assertThatThrownBy(() -> helper.decryptFile(encryptedFilePath, configuration))
                        .isExactlyInstanceOf(VaultEncryptionException.class)
                        .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);

                var command = VaultDecryptCommand.from(configuration, encryptedFilePath);
                verify(processHelper).launch(command.getCommandParts());
            }
        }

        @Nested
        class ToNewFile {

            @Test
            void shouldReturnNewPath_WhenSuccessful() {
                mockOsProcess(processHelper, process, 0, null, "Decryption successful");

                var encryptedFile = "/data/crypt/secrets.yml";
                var outputFile = "/data/var/secrets.yml";

                var decryptedFile = helper.decryptFile(encryptedFile, outputFile, configuration);

                assertThat(decryptedFile).isEqualTo(Path.of(outputFile));

                var command = VaultDecryptCommand.from(configuration, encryptedFile, outputFile);
                verify(processHelper).launch(command.getCommandParts());
            }

            @ParameterizedTest
            @CsvSource({
                    "/data/crypt/secrets.yml,/data/crypt/secrets.yml",
                    "/data/crypt/secrets.yml,/data/crypt/Secrets.yml",
                    "/data/crypt/secrets.yml,/data/crypt/SECRETS.yml"
            })
            void shouldNotPermitNewFileLocationToOverwriteEncryptedFile(String encryptedFile, String outputFile) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> helper.decryptFile(encryptedFile, outputFile, configuration))
                        .withMessage("outputFilePath must be different than encryptedFilePath (case-insensitive)");
            }

            @Test
            void shouldThrowException_WhenExitCodeIsNonZero() {
                var errorOutput = "ERROR! input is not vault encrypted data/etc/secrets.yml is not a vault encrypted file for /etc/secrets.yml";
                mockOsProcess(processHelper, process, 1, null, errorOutput);

                var encryptedFile = "/data/crypt/secrets.yml";
                var outputFile = "/data/var/secrets.yml";

                assertThatThrownBy(() -> helper.decryptFile(encryptedFile, outputFile, configuration))
                        .isExactlyInstanceOf(VaultEncryptionException.class)
                        .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);
            }
        }
    }

    @Nested
    class ViewFile {

        @Test
        void shouldReturnDecryptedContent_WhenSuccessful() {
            var plainText = "the secret stash";
            mockOsProcess(processHelper, process, 0, plainText, null);

            var encryptedFile = "/data/etc/secrets.yml";

            var decryptedContents = helper.viewFile(encryptedFile, configuration);

            assertThat(decryptedContents).isEqualTo(plainText);

            var command = VaultViewCommand.from(configuration, encryptedFile);
            verify(processHelper).launch(command.getCommandParts());
        }

        @Test
        void shouldThrowException_WhenExitCodeIsNonZero() {
            var errorOutput = "ERROR! input is not vault encrypted data/etc/secrets.yml is not a vault encrypted file for /etc/secrets.yml";
            mockOsProcess(processHelper, process, 1, null, errorOutput);

            var encryptedFilePath = "/etc/secrets.yml";

            assertThatThrownBy(() -> helper.viewFile(encryptedFilePath, configuration))
                    .isExactlyInstanceOf(VaultEncryptionException.class)
                    .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);

            var command = VaultViewCommand.from(configuration, encryptedFilePath);
            verify(processHelper).launch(command.getCommandParts());
        }
    }

    @Nested
    class EncryptString {

        @Test
        void shouldReturnEncryptedString_WhenSuccessful() {
            var encryptedContent = Fixtures.fixture(ENCRYPT_STRING_1_1_FORMAT);

            mockOsProcess(processHelper, process, 0, encryptedContent, "Encryption successful");

            var plainText = "this is the plain text";
            var variableName = "some_variable";
            var result = helper.encryptString(plainText, variableName, configuration);

            assertThat(result).isEqualTo(encryptedContent);

            var command = VaultEncryptStringCommand.from(configuration, plainText, variableName);
            verify(processHelper).launch(command.getCommandParts());
        }

        @Test
        void shouldThrowException_WhenExitCodeIsNonZero() {
            var errorOutput = "ERROR! input is already encrypted";
            mockOsProcess(processHelper, process, 1, null, errorOutput);

            var plainText = "my-password";
            var variableName = "db_password";
            assertThatThrownBy(() ->
                    helper.encryptString(plainText, variableName, configuration))
                    .isExactlyInstanceOf(VaultEncryptionException.class)
                    .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);

            var command = VaultEncryptStringCommand.from(configuration, plainText, variableName);
            verify(processHelper).launch(command.getCommandParts());
        }
    }

    @Nested
    class DecryptString {

        @Test
        void shouldDecryptEncryptedVariable_WhenSuccessful() {
            var plainText = "secret sauce";
            mockOsProcess(processHelper, process, 0, plainText, "Decryption successful");

            var encryptedString = Fixtures.fixture(ENCRYPT_STRING_1_1_FORMAT);

            var result = helper.decryptString(encryptedString, configuration);

            assertThat(result).isEqualTo(plainText);

            var encryptedFilePath = Path.of(folder.toString(), VARIABLE_NAME + ".txt");

            // Verify the command that was launched. This is more difficult here due to
            // the way we need to write the encrypt_string content to a temporary file
            // which has a random component in its name to avoid possibility of file name
            // collisions.
            verify(processHelper).launch(argThat(matchesExpectedCommand(encryptedFilePath)));
        }

        private ArgumentMatcher<List<String>> matchesExpectedCommand(Path encryptedFilePath) {
            return (List<String> commandParts) -> {
                // Check command up until last argument (file name, which has a random component)
                var vaultDecryptCommand = VaultDecryptCommand.toStdoutFrom(configuration, encryptedFilePath.toString());
                var vaultDecryptCommandParts = vaultDecryptCommand.getCommandParts();
                var expectedPartsExcludingLast = subListExcludingLast(vaultDecryptCommandParts);

                var commandPartsExcludingLast = subListExcludingLast(commandParts);

                assertThat(commandPartsExcludingLast)
                        .describedAs("Command until filename should be the same")
                        .isEqualTo(expectedPartsExcludingLast);

                // Check file name, but ignore the random numbers in the middle of it
                var lastPart = KiwiLists.last(commandParts);
                assertThat(lastPart)
                        .describedAs("File name should start with %s end with .txt", VARIABLE_NAME)
                        .startsWith(Path.of(folder.toString(), VARIABLE_NAME + ".").toString())
                        .endsWith(".txt");

                return true;
            };
        }

        @Test
        void shouldThrowException_WhenExitCodeIsNonZero() {
            var errorOutput = "ERROR! input is already encrypted";
            mockOsProcess(processHelper, process, 1, null, errorOutput);

            var encryptedString = Fixtures.fixture(ENCRYPT_STRING_1_1_FORMAT);

            assertThatThrownBy(() -> helper.decryptString(encryptedString, configuration))
                    .isExactlyInstanceOf(VaultEncryptionException.class)
                    .hasMessage("ansible-vault returned non-zero exit code 1. Stderr: %s", errorOutput);
        }
    }

    // Things this method mocks:
    //
    // mockProcessHelper:
    // launch (returns mockProcess)
    // waitForExit (return Optional<exitCode>)
    //
    // mockProcess:
    // getInputStream
    // getErrorStream
    private static void mockOsProcess(ProcessHelper mockProcessHelper,
                                      Process mockProcess,
                                      @Nullable Integer exitCode,
                                      @Nullable String stdOutput,
                                      @Nullable String errorOutput) {

        when(mockProcessHelper.launch(anyList())).thenReturn(mockProcess);
        when(mockProcessHelper.waitForExit(same(mockProcess), anyLong(), any(TimeUnit.class)))
                .thenReturn(Optional.ofNullable(exitCode));

        var stdOutInputStream = newInputStream(stdOutput);
        when(mockProcess.getInputStream()).thenReturn(stdOutInputStream);

        var errorInputStream = newInputStream(errorOutput);
        when(mockProcess.getErrorStream()).thenReturn(errorInputStream);
    }

    private static InputStream newInputStream(@Nullable String value) {
        if (isNull(value)) {
            return InputStream.nullInputStream();
        }

        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

}