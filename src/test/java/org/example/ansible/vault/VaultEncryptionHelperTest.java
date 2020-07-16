package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.ansible.vault.testing.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("VaultEncryptionHelper")
class VaultEncryptionHelperTest {

    private static final String ENCRYPTED_FILE_PATH = "encryption/encrypted_key_file.txt";

    // This is the variable name in the above encrypted file
    private static final String VARIABLE_NAME = "VaultSecret";

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
        doReturn(value).when(helper).executeVaultCommand(any(OsCommand.class));

        var encryptedString = Fixtures.fixture(ENCRYPTED_FILE_PATH);
        var decryptedValue = helper.decryptString(encryptedString, configuration);

        assertThat(decryptedValue).isEqualTo(value);

        verify(helper).executeVaultCommand(argThat(osCommand -> {
            var encryptedFilePath = Path.of(folder.toString(), VARIABLE_NAME + ".txt");
            var expectedCommandParts = VaultDecryptCommand.from(configuration, encryptedFilePath.toString()).getCommandParts();
            assertThat(osCommand.getCommandParts()).isEqualTo(expectedCommandParts);

            return true;
        }));
    }

    @Test
    void encryptString() {
        var plainText = "test value";
        var encryptedFixtureValue = Fixtures.fixture(ENCRYPTED_FILE_PATH);

        doReturn(encryptedFixtureValue).when(helper).executeVaultCommand(any(OsCommand.class));

        var encryptedValue = helper.encryptString(plainText, VARIABLE_NAME, configuration);

        assertThat(encryptedValue).isEqualTo(encryptedFixtureValue);

        verify(helper).executeVaultCommand(argThat(osCommand -> {
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
        doReturn(processMock).when(helper).getProcess(any(OsCommand.class));

        var inputStream = new ByteArrayInputStream(encryptedValue.getBytes(StandardCharsets.UTF_8));
        when(processMock.getInputStream()).thenReturn(inputStream);

        var mySecret = helper.executeVaultCommand(osCommandMock);

        assertThat(mySecret).isEqualTo(encryptedValue);
    }

    @Test
    void executeVaultCommand_Exception() throws IOException {
        var osCommandMock = mock(OsCommand.class);
        var processMock = mock(Process.class);
        var inputStreamMock = mock(InputStream.class);
        doReturn(processMock).when(helper).getProcess(any(OsCommand.class));

        when(processMock.getInputStream()).thenReturn(inputStreamMock);
        when(inputStreamMock.transferTo(any(OutputStream.class))).thenThrow(new IOException());

        assertThatThrownBy(() ->
                helper.executeVaultCommand(osCommandMock))
                .isExactlyInstanceOf(VaultEncryptionException.class)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasMessageStartingWith("Error transferring process output to string");
    }

}