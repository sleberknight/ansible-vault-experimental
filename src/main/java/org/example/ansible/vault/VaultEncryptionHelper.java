package org.example.ansible.vault;

import static org.kiwiproject.base.KiwiPreconditions.checkArgument;
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

    public String getDecryptedKeyValue(String key, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var secretFileDescriptor = new VaultSecretFileDescriptor(key, Paths.get(configuration.getTempDirectory()));
        var tempKeyFile = secretFileDescriptor.getTempKeyFile();

        try {
            copyEncryptedKeyToTempFile(secretFileDescriptor);

            return executeVaultOsCommand(tempKeyFile.toString(),
                    VaultCommandType.DECRYPT,
                    "VaultKey",
                    configuration);
        } catch (Exception e) {
            LOG.error("Error decrypting", e);
            throw e;
        } finally {
            cleanUpTempFile(tempKeyFile);
        }
    }

    private static void validateEncryptionConfiguration(VaultConfiguration configuration) {
        checkArgument(
                doesPathExist(configuration.getVaultPasswordFilePath()),
                VaultEncryptionException.class,
                "vault password file does not exist: {}", configuration.getVaultPasswordFilePath()
        );
        checkArgument(
                doesPathExist(configuration.getAnsibleVaultPath()),
                VaultEncryptionException.class,
                "ansible-vault executable does not exist: {}", configuration.getAnsibleVaultPath()
        );
    }

    private static boolean doesPathExist(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    public String getEncryptedValue(String key, String secretName, VaultConfiguration configuration) {
        return executeVaultOsCommand(key, VaultCommandType.ENCRYPT, secretName, configuration);
    }

    private void copyEncryptedKeyToTempFile(VaultSecretFileDescriptor fileDescriptor) {
        var keyFile = getTempFile(fileDescriptor.getDirectoryPath(), fileDescriptor.getTempKeyFile());
        var bytes = fileDescriptor.getPayloadToWrite().getBytes(StandardCharsets.UTF_8);

        try (var outputStream = new BufferedOutputStream(new FileOutputStream(keyFile))) {
            var inputStream = new BufferedInputStream(new ByteArrayInputStream(bytes));
            // Modified original logging a bit to more clearly show start & end of payload content
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
    String executeVaultOsCommand(String key,
                                 VaultCommandType commandType,
                                 String secretName,
                                 VaultConfiguration configuration) {

        logArgsToExecuteVaultOsCommand(key, commandType, secretName, configuration);

        var osCommand = getOsCommand(key, commandType, secretName, configuration);
        LOG.debug("Ansible command: {}", lazy(osCommand::getOsCommandParts));
        var encryptionProcess = getProcess(osCommand);

        return processCommandStream(encryptionProcess);
    }

    // This should eventually be removed, but want to see exactly what's being passed now...
    // (to see, you'll obviously need to change the logback.xml configuration to TRACE level)
    private void logArgsToExecuteVaultOsCommand(String key,
                                                VaultCommandType commandType,
                                                String secretName,
                                                VaultConfiguration configuration) {

        LOG.trace("executeVaultOsCommand args:");
        LOG.trace("key: {}", key);
        LOG.trace("commandType: {}", commandType);
        LOG.trace("secretName: {}", secretName);
        LOG.trace("configuration.ansibleVaultPath: {}", configuration.getAnsibleVaultPath());
        LOG.trace("configuration.vaultPasswordFilePath: {}", configuration.getVaultPasswordFilePath());
        LOG.trace("configuration.tempDirectory: {}", configuration.getTempDirectory());
    }

    String processCommandStream(Process encryptionProcess) {
        var outputStream = new ByteArrayOutputStream();

        try {
            encryptionProcess.getInputStream().transferTo(outputStream);
        } catch (IOException e) {
            throw new VaultEncryptionException("Error reading/writing encryption stream", e);
        }

        return outputStream.toString(StandardCharsets.UTF_8);
    }

    @VisibleForTesting
    Process getProcess(OsCommand command) {
        return new ProcessHelper().launch(command.getOsCommandParts());
    }

    @VisibleForTesting
    OsCommand getOsCommand(String key,
                           VaultCommandType commandType,
                           String secretName,
                           VaultConfiguration configuration) {

        return new OsCommandFactory(configuration).getOsCommand(commandType, key, secretName);
    }
}
