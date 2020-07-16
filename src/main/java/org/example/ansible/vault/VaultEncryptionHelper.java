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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@SuppressWarnings({"java:S125"})
public class VaultEncryptionHelper {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    public String encryptString(String plainText, String variableName, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultEncryptStringCommand.from(configuration, plainText, variableName);
        return executeVaultCommand(osCommand);
    }

    public String decryptString(String encryptedString, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);

        var secretFileDescriptor =
                new VaultSecretFileDescriptor(encryptedString, Paths.get(configuration.getTempDirectory()));

        var tempFilePath = secretFileDescriptor.getTempFilePath();

        try {
            copyEncryptedKeyToTempFile(secretFileDescriptor);
            var osCommand = VaultDecryptCommand.from(configuration, tempFilePath.toString());
            return executeVaultCommand(osCommand);
        } catch (Exception e) {
            LOG.error("Error decrypting", e);
            throw e;
        } finally {
            cleanUpTempFile(tempFilePath);
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

    private void copyEncryptedKeyToTempFile(VaultSecretFileDescriptor fileDescriptor) {
        var tempFile = getTempFile(fileDescriptor.getDirectoryPath(), fileDescriptor.getTempFilePath());
        var bytes = fileDescriptor.getPayloadToWrite().getBytes(StandardCharsets.UTF_8);

        try (var outputStream = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            var inputStream = new BufferedInputStream(new ByteArrayInputStream(bytes));
            LOG.trace("Payload to write ----{}{}", LINE_SEPARATOR, fileDescriptor.getPayloadToWrite());
            LOG.trace("End payload ----");

            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            LOG.error("Error copying to temp file", e);
            throw new VaultEncryptionException("Error copying to temp file", e);
        }
    }

    // TODO
    //  This does not need to actually create the temp file
    //  It should be changed and renamed to 'createTemporaryDirectoriesIfNecessary' or similar
    //  The log messages need to be changed
    private static File getTempFile(Path directoryPath, Path tempKeyFile) {
        try {
            Files.createDirectories(directoryPath);
            Files.createFile(tempKeyFile);
            var tempFile = tempKeyFile.toFile();
            LOG.debug("Wrote temp file: {}", tempFile);

            return tempFile;
        } catch (IOException e) {
            var message = format("Could not get temp file {} in path {}", tempKeyFile, directoryPath);
            LOG.error(message, e);
            throw new VaultEncryptionException(message, e);
        }
    }

    @VisibleForTesting
    void cleanUpTempFile(Path path) {
        try {
            LOG.debug("Cleanup temp file {}", path);
            Files.delete(path);
        } catch (IOException e) {
            LOG.error("Could not delete file: {}", path, e);
        }
    }

    @VisibleForTesting
    String executeVaultCommand(OsCommand osCommand) {
        LOG.debug("Ansible command: {}", lazy(osCommand::getCommandParts));
        var vaultProcess = getProcess(osCommand);
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
    Process getProcess(OsCommand command) {
        return new ProcessHelper().launch(command.getCommandParts());
    }
}
