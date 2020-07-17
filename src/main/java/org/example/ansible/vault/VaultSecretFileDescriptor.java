package org.example.ansible.vault;

import lombok.Getter;

import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Getter
class VaultSecretFileDescriptor {

    private static final String ANSIBLE_ENCRYPTION_PREAMBLE = "$ANSIBLE_VAULT;1.1;AES256";

    private static final String DEFAULT_FILE_EXTENSION = "txt";

    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

    private final String encryptedString;
    private final Path tempDirectoryPath;
    private final Path tempFilePath;
    private final String payloadToWrite;
    private final String fileExtension;

    VaultSecretFileDescriptor(String encryptedString, Path tempDirectoryPath) {
        this.encryptedString = encryptedString;
        this.fileExtension = DEFAULT_FILE_EXTENSION;
        this.tempDirectoryPath = tempDirectoryPath;

        var splat = encryptedString.split(":");
        var variableName = splat[0];
        var payload = splat[1];
        var newPayload = payload.split("\\" + ANSIBLE_ENCRYPTION_PREAMBLE)[1];
        var cleanedUpPayload = WHITE_SPACE_PATTERN.matcher(newPayload).replaceAll("");
        this.tempFilePath = Path.of(tempDirectoryPath.toString(), generateRandomFileName(variableName));
        this.payloadToWrite = ANSIBLE_ENCRYPTION_PREAMBLE + System.lineSeparator() + cleanedUpPayload;
    }

    private String generateRandomFileName(String variableName) {
        return variableName +
                "." +
                Integer.toUnsignedString(ThreadLocalRandom.current().nextInt()) +
                Long.toUnsignedString(ThreadLocalRandom.current().nextLong()) +
                "." + fileExtension;
    }
}
