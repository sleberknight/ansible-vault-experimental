package org.example.ansible.vault;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

// TODO need to check error stream, exit code, timeouts, etc. on processes!

@Slf4j
@SuppressWarnings({"java:S125"})
public class VaultEncryptionHelper {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final int DEFAULT_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final ProcessHelper processHelper;

    public VaultEncryptionHelper() {
        this(new ProcessHelper());
    }

    @VisibleForTesting
    VaultEncryptionHelper(ProcessHelper processHelper) {
        this.processHelper = processHelper;
    }

    /**
     * Wraps the ansible-vault encrypt command. Encrypts file in place.
     */
    public Path encryptFile(String plainTextFilePath, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultEncryptCommand.from(configuration, plainTextFilePath);
        return executeVaultCommandWithoutOutput(osCommand, plainTextFilePath);
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file in place.
     */
    public Path decryptFile(String encryptedFilePath, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultDecryptCommand.from(configuration, encryptedFilePath);
        return executeVaultCommandWithoutOutput(osCommand, encryptedFilePath);
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file to a new specified output path.
     * The original encrypted file is not modified.
     */
    public Path decryptFile(String encryptedFilePath,
                            String outputFilePath,
                            VaultConfiguration configuration) {
        checkArgument(!outputFilePath.equalsIgnoreCase(encryptedFilePath),
                "outputFilePath must be different than encryptedFilePath (case-insensitive)");

        validateEncryptionConfiguration(configuration);
        var osCommand = VaultDecryptCommand.from(configuration, encryptedFilePath, outputFilePath);
        executeVaultCommandWithoutOutput(osCommand, encryptedFilePath);

        return Path.of(outputFilePath);
    }

    private Path executeVaultCommandWithoutOutput(OsCommand osCommand, String filePath) {
        executeVaultCommand(osCommand);
        return new File(filePath).toPath();
    }

    /**
     * Wraps ansible-vault view command. Returns the decrypted contents of the file.
     * The original encrypted file is not modified.
     */
    public String viewFile(String encryptedFilePath, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultViewCommand.from(configuration, encryptedFilePath);
        return executeVaultCommandReturningStdout(osCommand);
    }

    private String executeVaultCommandReturningStdout(OsCommand osCommand) {
        var vaultProcess = executeVaultCommand(osCommand);
        return readProcessOutput(vaultProcess);
    }

    private Process executeVaultCommand(OsCommand osCommand) {
        LOG.debug("Ansible command: {}", lazy(osCommand::getCommandParts));

        var vaultProcess = processHelper.launch(osCommand.getCommandParts());
        var exitCode = processHelper.waitForExit(vaultProcess, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)
                .orElseThrow(() -> new VaultEncryptionException("ansible-vault did not exit before timeout"));
        LOG.debug("ansible-vault exit code: {}", exitCode);

        if (exitCode != 0) {
            var rawErrorOutput = readProcessErrorOutput(vaultProcess);
            var errorOutput = isBlank(rawErrorOutput) ? "[no stderr]" : rawErrorOutput.trim();
            LOG.debug("Error output: [{}]", errorOutput);

            var message = f("ansible-vault returned non-zero exit code {}. Stderr: {}", exitCode, errorOutput);
            throw new VaultEncryptionException(message);
        }

        return vaultProcess;
    }

    /**
     * Wraps the ansible-vault encrypt_string command.
     */
    public String encryptString(String plainText, String variableName, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        var osCommand = VaultEncryptStringCommand.from(configuration, plainText, variableName);
        return executeVaultCommandReturningStdoutOld(osCommand);
    }

    /**
     * Decrypts an encrypted string variable formatted using encrypt_string with a --name option.
     */
    public String decryptString(String encryptedString, VaultConfiguration configuration) {
        validateEncryptionConfiguration(configuration);
        checkArgumentNotBlank(configuration.getTempDirectory(),
                "configuration.tempDirectory is required for decryptString");

        var encryptedVariable = new VaultEncryptedVariable(encryptedString);
        var tempFilePath = encryptedVariable.generateRandomFilePath(configuration.getTempDirectory());

        try {
            createTempDirectoryIfNecessary(Path.of(configuration.getTempDirectory()));
            writeEncryptStringContentToTempFile(encryptedVariable, tempFilePath);
            var osCommand = VaultDecryptCommand.toStdoutFrom(configuration, tempFilePath.toString());
            return executeVaultCommandReturningStdoutOld(osCommand);
        } catch (Exception e) {
            LOG.error("Error decrypting", e);
            throw e;
        } finally {
            deleteFileQuietly(tempFilePath);
        }
    }

    // TODO Move into VaultConfiguration as a static 'validate' method?
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

        try {
            LOG.trace("Payload to write ----{}{}{}----- End payload ----",
                    LINE_SEPARATOR, encryptedVariable.getEncryptedFileContent(), LINE_SEPARATOR);

            Files.write(tempFilePath, encryptedVariable.getEncryptedFileBytes());
            LOG.debug("Wrote temporary file containing encrypt_string content: {}", tempFilePath);
        } catch (IOException e) {
            LOG.error("Error writing temp file: " + tempFilePath, e);
            throw new VaultEncryptionException("Error copying to temp file", e);
        }
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

    // TODO Replace this with executeVaultCommandReturningStdout
    @VisibleForTesting
    String executeVaultCommandReturningStdoutOld(OsCommand osCommand) {
        LOG.debug("Ansible command: {}", lazy(osCommand::getCommandParts));
        var vaultProcess = launchProcess(osCommand);
        return readProcessOutput(vaultProcess);
    }

    String readProcessOutput(Process process) {
        return readInputStreamAsString(process.getInputStream());
    }

    String readProcessErrorOutput(Process process) {
        return readInputStreamAsString(process.getErrorStream());
    }

    String readInputStreamAsString(InputStream inputStream) {
        try {
            var outputStream = new ByteArrayOutputStream();
            inputStream.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VaultEncryptionException("Error converting InputStream to String", e);
        }
    }

    @VisibleForTesting
    Process launchProcess(OsCommand command) {
        return processHelper.launch(command.getCommandParts());
    }
}
