package org.example.ansible.vault;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.example.ansible.vault.Utils.readProcessErrorOutput;
import static org.example.ansible.vault.Utils.readProcessOutput;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.logging.LazyLogParameterSupplier.lazy;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.process.ProcessHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
public class VaultEncryptionHelper {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final int DEFAULT_TIMEOUT = 10;
    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final ProcessHelper processHelper;
    private final VaultConfiguration configuration;

    public VaultEncryptionHelper(VaultConfiguration configuration) {
        this(configuration, new ProcessHelper());
    }

    @VisibleForTesting
    VaultEncryptionHelper(VaultConfiguration configuration, ProcessHelper processHelper) {
        checkArgumentNotNull(configuration, "configuration is required");
        checkArgumentNotNull(processHelper, "processHelper is required");

        this.configuration = validateAndCopyVaultConfiguration(configuration);
        this.processHelper = processHelper;
    }

    /**
     * Validates and returns (assuming validation passed) a defensive copy of the given configuration.
     */
    private static VaultConfiguration validateAndCopyVaultConfiguration(VaultConfiguration configuration) {
        checkArgumentNotBlank(configuration.getVaultPasswordFilePath(), "vaultPasswordFilePath is required");
        checkArgument(isExistingPath(configuration.getVaultPasswordFilePath()),
                "vault password file does not exist: {}", configuration.getVaultPasswordFilePath());
        checkArgumentNotBlank(configuration.getAnsibleVaultPath(), "ansibleVaultPath is required");
        checkArgument(isExistingPath(configuration.getAnsibleVaultPath()),
                "ansible-vault executable does not exist: {}", configuration.getAnsibleVaultPath());

        return configuration.copyOf();
    }

    /**
     * Wraps the ansible-vault encrypt command. Encrypts file in place.
     */
    public Path encryptFile(Path plainTextFilePath) {
        checkArgumentNotNull(plainTextFilePath, "plainTextFilePath cannot be null");
        return encryptFile(plainTextFilePath.toString());
    }

    /**
     * Wraps the ansible-vault encrypt command. Encrypts file in place.
     */
    public Path encryptFile(String plainTextFilePath) {
        var osCommand = VaultEncryptCommand.from(configuration, plainTextFilePath);
        return executeVaultCommandWithoutOutput(osCommand, plainTextFilePath);
    }

    /**
     * Wraps the ansible-vault encrypt command using a vault ID label. Encrypts file in place.
     */
    public Path encryptFile(Path plainTextFilePath, String vaultIdLabel) {
        checkArgumentNotNull(plainTextFilePath, "plainTextFilePath cannot be null");
        return encryptFile(plainTextFilePath.toString(), vaultIdLabel);
    }

    /**
     * Wraps the ansible-vault encrypt command using a vault ID label. Encrypts file in place.
     */
    public Path encryptFile(String plainTextFilePath, String vaultIdLabel) {
        var osCommand = VaultEncryptCommand.from(configuration, vaultIdLabel, plainTextFilePath);
        return executeVaultCommandWithoutOutput(osCommand, plainTextFilePath);
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file in place.
     */
    public Path decryptFile(Path encryptedFilePath) {
        checkArgumentNotNull(encryptedFilePath, "encryptedFilePath cannot be null");
        return decryptFile(encryptedFilePath.toString());
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file in place.
     */
    public Path decryptFile(String encryptedFilePath) {
        var osCommand = VaultDecryptCommand.from(configuration, encryptedFilePath);
        return executeVaultCommandWithoutOutput(osCommand, encryptedFilePath);
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file to a new specified output path.
     * The original encrypted file is not modified.
     */
    public Path decryptFile(Path encryptedFilePath, Path outputFilePath) {
        checkArgumentNotNull(encryptedFilePath, "encryptedFilePath cannot be null");
        checkArgumentNotNull(outputFilePath, "outputFilePath cannot be null");
        return decryptFile(encryptedFilePath.toString(), outputFilePath.toString());
    }

    /**
     * Wraps ansible-vault decrypt command. Decrypts file to a new specified output path.
     * The original encrypted file is not modified.
     */
    public Path decryptFile(String encryptedFilePath, String outputFilePath) {
        checkArgumentNotBlank(encryptedFilePath, "encryptedFilePath cannot be blank");
        checkArgumentNotBlank(outputFilePath, "outputFilePath cannot be blank");
        checkArgument(!outputFilePath.equalsIgnoreCase(encryptedFilePath),
                "outputFilePath must be different than encryptedFilePath (case-insensitive)");

        var osCommand = VaultDecryptCommand.from(configuration, encryptedFilePath, outputFilePath);
        executeVaultCommandWithoutOutput(osCommand, encryptedFilePath);

        return Path.of(outputFilePath);
    }

    /**
     * Wraps ansible-vault view command. Returns the decrypted contents of the file.
     * The original encrypted file is not modified.
     */
    public String viewFile(Path encryptedFilePath) {
        checkArgumentNotNull(encryptedFilePath, "encryptedFilePath cannot be null");
        return viewFile(encryptedFilePath.toString());
    }

    /**
     * Wraps ansible-vault view command. Returns the decrypted contents of the file.
     * The original encrypted file is not modified.
     */
    public String viewFile(String encryptedFilePath) {
        var osCommand = VaultViewCommand.from(configuration, encryptedFilePath);
        return executeVaultCommandReturningStdout(osCommand);
    }

    /**
     * Wraps ansible-vault rekey command. Returns the path of the rekeyed file.
     */
    public Path rekeyFile(Path encryptedFilePath, Path newVaultPasswordFilePath) {
        checkArgumentNotNull(encryptedFilePath, "encryptedFilePath cannot be null");
        checkArgumentNotNull(newVaultPasswordFilePath, "newVaultPasswordFilePath cannot be null");
        return rekeyFile(encryptedFilePath.toString(), newVaultPasswordFilePath.toString());
    }

    /**
     * Wraps ansible-vault rekey command. Returns the path of the rekeyed file.
     */
    public Path rekeyFile(String encryptedFilePath, String newVaultPasswordFilePath) {
        checkArgumentNotBlank(encryptedFilePath, "encryptedFilePath cannot be blank");
        checkArgumentNotBlank(newVaultPasswordFilePath, "newVaultPasswordFilePath cannot be blank");
        checkArgument(!newVaultPasswordFilePath.equalsIgnoreCase(configuration.getVaultPasswordFilePath()),
                "newVaultPasswordFilePath file must be different than configuration.vaultPasswordFilePath (case-insensitive)");

        var osCommand = VaultRekeyCommand.from(configuration, encryptedFilePath, newVaultPasswordFilePath);
        return executeVaultCommandWithoutOutput(osCommand, encryptedFilePath);
    }

    private Path executeVaultCommandWithoutOutput(OsCommand osCommand, String filePath) {
        executeVaultCommand(osCommand);
        return Path.of(filePath);
    }

    /**
     * Wraps the ansible-vault encrypt_string command.
     */
    public String encryptString(String plainText, String variableName) {
        var osCommand = VaultEncryptStringCommand.from(configuration, plainText, variableName);
        return executeVaultCommandReturningStdout(osCommand);
    }

    /**
     * Wraps the ansible-vault encrypt_string command  using an optional vault ID label.
     */
    public String encryptString(String vaultIdLabel, String plainText, String variableName) {
        var osCommand = VaultEncryptStringCommand.from(configuration, vaultIdLabel, plainText, variableName);
        return executeVaultCommandReturningStdout(osCommand);
    }

    /**
     * Decrypts an encrypted string variable formatted using encrypt_string with a --name option.
     */
    public String decryptString(String encryptedString) {
        checkArgumentNotBlank(configuration.getTempDirectory(),
                "configuration.tempDirectory is required for decryptString");

        var encryptedVariable = new VaultEncryptedVariable(encryptedString);
        var tempFilePath = encryptedVariable.generateRandomFilePath(configuration.getTempDirectory());

        try {
            createTempDirectoryIfNecessary(Path.of(configuration.getTempDirectory()));
            writeEncryptStringContentToTempFile(encryptedVariable, tempFilePath);
            var osCommand = VaultDecryptCommand.toStdoutFrom(configuration, tempFilePath.toString());
            return executeVaultCommandReturningStdout(osCommand);
        } catch (Exception e) {
            LOG.error("Error decrypting", e);
            throw e;
        } finally {
            deleteFileQuietly(tempFilePath);
        }
    }

    private static boolean isExistingPath(String filePath) {
        return Files.exists(Path.of(filePath));
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

    private void writeEncryptStringContentToTempFile(VaultEncryptedVariable encryptedVariable,
                                                     Path tempFilePath) {

        try {
            LOG.trace("Payload to write ----{}{}{}----- End payload ----",
                    LINE_SEPARATOR, encryptedVariable.getEncryptedFileContent(), LINE_SEPARATOR);

            Files.write(tempFilePath, encryptedVariable.getEncryptedFileBytes());
            LOG.debug("Wrote temporary file containing encrypt_string content: {}", tempFilePath);
        } catch (IOException e) {
            LOG.error("Error writing temp file: " + tempFilePath, e);
            throw new UncheckedIOException("Error copying to temp file", e);
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

}
