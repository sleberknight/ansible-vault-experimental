package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.example.ansible.vault.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Notes:
// This original test mocks a lot (specifically the actual ansible-vault commands)
// so we need to think about how/if we can make it better. Could we detect if ansible-vault
// is available, or assume a known location and if present use it and test using the
// actual command?

class VaultEncryptionHelperTest {

    private static final String ENCRYPTION_ENCRYPTED_KEY_FILE_TXT =
            "encryption/encrypted_key_file.txt";

    // Original code used the (obsolete) KiwiTempDirectory.
    // Replaced with JUnit Jupiter's @TempDir
    @TempDir
    Path folder;

    private VaultEncryptionHelper helper;
    private VaultConfiguration configuration;

    @BeforeEach
    void setUp() throws IOException {
        var passwordFilePath = Files.createFile(Path.of(folder.toString(), ".vault_pass.txt"));
        var vaultFilePath = Files.createFile(Path.of(folder.toString(), "ansible-vault"));
        Files.writeString(passwordFilePath, "test");

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
        doReturn(value).when(helper).executeVaultOsCommand(
                anyString(),
                eq(VaultCommandType.DECRYPT),
                anyString(),
                any(VaultConfiguration.class));

        var encryptedString = Fixtures.fixture(ENCRYPTION_ENCRYPTED_KEY_FILE_TXT);
        var decryptedValue = helper.decryptString(encryptedString, configuration);

        assertThat(decryptedValue).isEqualTo(value);
    }

    @Test
    void encryptString() {
        var plainText = "test value";
        var encryptedFixtureValue = Fixtures.fixture(ENCRYPTION_ENCRYPTED_KEY_FILE_TXT);

        doReturn(encryptedFixtureValue).when(helper).executeVaultOsCommand(
                anyString(),
                eq(VaultCommandType.ENCRYPT_STRING),
                anyString(),
                any(VaultConfiguration.class));

        var encryptedValue = helper.encryptString(plainText, "Vault-secret", configuration);

        assertThat(encryptedValue).isEqualTo(encryptedFixtureValue);
    }

    @Test
    void executeVaultCommand() {
        var encryptedValue = "oogabooga";
        var osCommandMock = mock(OsCommand.class);
        var processMock = mock(Process.class);
        configureMocks(osCommandMock, processMock);

        var inputStream = new ByteArrayInputStream(encryptedValue.getBytes(StandardCharsets.UTF_8));
        when(processMock.getInputStream()).thenReturn(inputStream);

        var mySecret = helper.executeVaultOsCommand("secret-squirrel", VaultCommandType.ENCRYPT_STRING, "mySecret", configuration);

        assertThat(mySecret).isEqualTo(encryptedValue);
    }

    @Test
    void executeVaultCommand_Exception() throws IOException {
        var osCommandMock = mock(OsCommand.class);
        var processMock = mock(Process.class);
        var inputStreamMock = mock(InputStream.class);
        configureMocks(osCommandMock, processMock);

        when(processMock.getInputStream()).thenReturn(inputStreamMock);

        // Original code using IOUtils#copy needs the commented out mocking (which is really white-box)
        // Changing to use transferTo means we can tell the mock to throw the exception when it is called
        //when(inputStreamMock.read()).thenThrow(new IOException());
        when(inputStreamMock.transferTo(any(OutputStream.class))).thenThrow(new IOException());

        assertThatThrownBy(() ->
                helper.executeVaultOsCommand("secret-squirrel",
                        VaultCommandType.ENCRYPT_STRING,
                        "mySecret",
                        configuration))
                .isExactlyInstanceOf(VaultEncryptionException.class)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasMessageStartingWith("Error reading/writing encryption stream");
    }

    private void configureMocks(OsCommand osCommandMock, Process processMock) {
        doReturn(osCommandMock).when(helper).getOsCommand(
                anyString(),
                eq(VaultCommandType.ENCRYPT_STRING),
                anyString(),
                any(VaultConfiguration.class));

        doReturn(processMock).when(helper).getProcess(any(OsCommand.class));
    }

    @Test
    void getOsCommand() {
        var command = helper.getOsCommand("secret-squirrel", VaultCommandType.ENCRYPT_STRING, "mySecret", configuration);

        assertThat(command).isExactlyInstanceOf(VaultEncryptStringCommand.class)
                .extracting("ansibleVaultPath", "vaultPasswordFilePath", "variableName", "plainText")
                .contains(
                        configuration.getAnsibleVaultPath(),
                        configuration.getVaultPasswordFilePath(),
                        "secret-squirrel",
                        "mySecret"
                );
    }
}