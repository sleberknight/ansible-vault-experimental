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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.ansible.vault.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.base.process.ProcessHelper;
import org.kiwiproject.collect.KiwiLists;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Notes:
// This original test mocks a lot (specifically the actual ansible-vault commands)
// so we need to think about how/if we can make it better. Could we detect if ansible-vault
// is available, or assume a known location and if present use it and test using the
// actual command?
//
// TODO How can this be improved so as not to mock too much but without needing ansible-vault installed?

@DisplayName("VaultEncryptionHelper")
class VaultEncryptionHelperTest {

    private static final String ENCRYPT_STRING_1_1_FORMAT = "ansible-vault/encrypt_string_1.1.txt";

    // This is the variable name in the above encrypted file
    private static final String VARIABLE_NAME = "db_password";

    @TempDir
    Path folder;

    private VaultEncryptionHelper helper;
    private VaultConfiguration configuration;

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

        helper = spy(VaultEncryptionHelper.class);
    }

    @Test
    void decryptString() {
        var value = "test-encrypt";
        doReturn(value).when(helper).executeVaultCommandReturningStdoutOld(any(OsCommand.class));

        var encryptedString = Fixtures.fixture(ENCRYPT_STRING_1_1_FORMAT);
        var decryptedValue = helper.decryptString(encryptedString, configuration);

        assertThat(decryptedValue).isEqualTo(value);

        verify(helper).executeVaultCommandReturningStdoutOld(argThat(osCommand -> {
            // Check command up until last argument (file name)
            var encryptedFilePath = Path.of(folder.toString(), VARIABLE_NAME + ".txt");
            var vaultDecryptCommandParts =
                    VaultDecryptCommand.toStdoutFrom(configuration, encryptedFilePath.toString()).getCommandParts();

            var commandParts = osCommand.getCommandParts();
            var partsExcludingLast = subListExcludingLast(commandParts);

            var expectedPathsExcludingLast = subListExcludingLast(vaultDecryptCommandParts);
            assertThat(partsExcludingLast).isEqualTo(expectedPathsExcludingLast);

            // Check file name, but ignore the random numbers in the middle of it
            var lastPath = KiwiLists.last(commandParts);
            assertThat(lastPath)
                    .startsWith(Path.of(folder.toString(), VARIABLE_NAME + ".").toString())
                    .endsWith(".txt");

            return true;
        }));
    }

    @Disabled("in process of being replaced...")
    @Test
    void encryptString() {
        var plainText = "test value";
        var encryptedFixtureValue = Fixtures.fixture(ENCRYPT_STRING_1_1_FORMAT);

        doReturn(encryptedFixtureValue).when(helper).executeVaultCommandReturningStdoutOld(any(OsCommand.class));

        var encryptedValue = helper.encryptString(plainText, VARIABLE_NAME, configuration);

        assertThat(encryptedValue).isEqualTo(encryptedFixtureValue);

        verify(helper).executeVaultCommandReturningStdoutOld(argThat(osCommand -> {
            var expectedCommandParts = VaultEncryptStringCommand.from(configuration, plainText, VARIABLE_NAME).getCommandParts();
            assertThat(osCommand.getCommandParts())
                    .isEqualTo(expectedCommandParts);

            return true;
        }));
    }

    @Test
    void executeVaultCommand() {
        var encryptedValue = "oogabooga";
        var osCommandMock = mock(OsCommand.class);
        var processMock = mock(Process.class);
        doReturn(processMock).when(helper).launchProcess(any(OsCommand.class));

        var inputStream = newInputStream(encryptedValue);
        when(processMock.getInputStream()).thenReturn(inputStream);

        var mySecret = helper.executeVaultCommandReturningStdoutOld(osCommandMock);

        assertThat(mySecret).isEqualTo(encryptedValue);
    }

    @Test
    void executeVaultCommand_Exception() throws IOException {
        var osCommandMock = mock(OsCommand.class);
        var processMock = mock(Process.class);
        var inputStreamMock = mock(InputStream.class);
        doReturn(processMock).when(helper).launchProcess(any(OsCommand.class));

        when(processMock.getInputStream()).thenReturn(inputStreamMock);
        when(inputStreamMock.transferTo(any(OutputStream.class))).thenThrow(new IOException());

        assertThatThrownBy(() ->
                helper.executeVaultCommandReturningStdoutOld(osCommandMock))
                .isExactlyInstanceOf(VaultEncryptionException.class)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasMessageStartingWith("Error converting InputStream to String");
    }

    @Nested
    class EncryptFile {

        private VaultEncryptionHelper helper;
        private ProcessHelper processHelper;
        private Process process;

        @BeforeEach
        void setUp() {
            processHelper = mock(ProcessHelper.class);
            process = mock(Process.class);
            helper = new VaultEncryptionHelper(processHelper);
        }

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

        private VaultEncryptionHelper helper;
        private ProcessHelper processHelper;
        private Process process;

        @BeforeEach
        void setUp() {
            processHelper = mock(ProcessHelper.class);
            process = mock(Process.class);
            helper = new VaultEncryptionHelper(processHelper);
        }

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

        private VaultEncryptionHelper helper;
        private ProcessHelper processHelper;
        private Process process;

        @BeforeEach
        void setUp() {
            processHelper = mock(ProcessHelper.class);
            process = mock(Process.class);
            helper = new VaultEncryptionHelper(processHelper);
        }

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

        private VaultEncryptionHelper helper;
        private ProcessHelper processHelper;
        private Process process;

        @BeforeEach
        void setUp() {
            processHelper = mock(ProcessHelper.class);
            process = mock(Process.class);
            helper = new VaultEncryptionHelper(processHelper);
        }

        @Test
        void shouldReturnEncryptedString_WhenSuccessful() {
            var encryptedContent = Fixtures.fixture("ansible-vault/encrypt_string_1.1.txt");

            mockOsProcess(processHelper, process, 0, encryptedContent, "Encryption successful");

            var result = helper.encryptString("this is the plain text", "some_variable", configuration);

            assertThat(result).isEqualTo(encryptedContent);
        }

        @Test
        void shouldThrowException_WhenExitCodeIsNonZero() {
            var errorOutput = "ERROR! input is already encrypted";
            mockOsProcess(processHelper, process, 1, null, errorOutput);

            assertThatThrownBy(() ->
                    helper.encryptString("my-password", "db_password", configuration))
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