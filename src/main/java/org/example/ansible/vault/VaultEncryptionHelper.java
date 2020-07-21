package org.example.ansible.vault;

import static com.google.common.base.Preconditions.checkArgument;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@SuppressWarnings({"java:S125"})
public class VaultEncryptionHelper {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Wraps the ansible-vault encrypt_string command.
     */
    public String encryptString(String plainText, String variableName, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultEncryptStringCommand.from(configuration, plainText, variableName);
        return executeVaultCommand(osCommand);
    }

    /**
     * Decrypts an encrypted string variable formatted using encrypt_string with a --name option.
     */
    public String decryptString(String encryptedString, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);

        var encryptedVariable = new VaultEncryptedVariable(encryptedString);
        var tempFilePath = encryptedVariable.generateRandomFilePath(configuration.getTempDirectory());

        try {
            createTempDirectoryIfNecessary(Path.of(configuration.getTempDirectory()));
            writeEncryptStringContentToTempFile(encryptedVariable, tempFilePath);
            var osCommand = VaultDecryptCommand.from(configuration, tempFilePath.toString());
            return executeVaultCommand(osCommand);
        } catch (Exception e) {
            LOG.error("Error decrypting", e);
            throw e;
        } finally {
            deleteFileQuietly(tempFilePath);
        }
    }

    private static void validateEncryptionConfiguration(VaultConfiguration configuration) {
        checkArgument(doesPathExist(configuration.getVaultPasswordFilePath()),
                "vault password file does not exist: {}", configuration.getVaultPasswordFilePath());
        checkArgument(doesPathExist(configuration.getAnsibleVaultPath()),
                "ansible-vault executable does not exist: {}", configuration.getAnsibleVaultPath());
    }

    private static boolean doesPathExist(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    private void writeEncryptStringContentToTempFile(VaultEncryptedVariable encryptedVariable,
                                                     Path tempFilePath) {

        // TODO Why don't we just use Files.writeString or Files.write???
        try (var outputStream = newBufferedOutputStream(tempFilePath)) {
            var bytes = encryptedVariable.getEncryptedFileBytes();
            var inputStream = new BufferedInputStream(new ByteArrayInputStream(bytes));
            LOG.trace("Payload to write ----{}{}", LINE_SEPARATOR, encryptedVariable.getEncryptedFileContent());
            LOG.trace("End payload ----");

            inputStream.transferTo(outputStream);
            LOG.debug("Wrote temporary file containing encrypt_string content: {}", tempFilePath);
        } catch (IOException e) {
            LOG.error("Error copying to temp file: " + tempFilePath, e);
            throw new VaultEncryptionException("Error copying to temp file", e);
        }
    }

    private BufferedOutputStream newBufferedOutputStream(Path tempFilePath) throws FileNotFoundException {
        return new BufferedOutputStream(new FileOutputStream(tempFilePath.toFile()));
    }

    private static void createTempDirectoryIfNecessary(Path tempDirectoryPath) {
        try {
            Files.createDirectories(tempDirectoryPath);
        } catch (IOException e) {
            var message = format("Error creating temporary directory: {}", tempDirectoryPath);
            LOG.error(message);
            throw new UncheckedIOException(message, e);
        }
    }

    private void deleteFileQuietly(Path path) {
        try {
            LOG.debug("Delete path: {}", path);
            Files.delete(path);
        } catch (IOException e) {
            LOG.error("Could not delete path: {}", path, e);
        }
    }

    @VisibleForTesting
    String executeVaultCommand(OsCommand osCommand) {
        LOG.debug("Ansible command: {}", lazy(osCommand::getCommandParts));
        var vaultProcess = launchProcess(osCommand);
        return readProcessOutputAsString(vaultProcess);
    }

    String readProcessOutputAsString(Process encryptionProcess) {
        try {
            var outputStream = new ByteArrayOutputStream();
            encryptionProcess.getInputStream().transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultEncryptionException("Error transferring process output to string ", e);
        }
    }

    @VisibleForTesting
    Process launchProcess(OsCommand command) {
        return new ProcessHelper().launch(command.getCommandParts());
    }
}
