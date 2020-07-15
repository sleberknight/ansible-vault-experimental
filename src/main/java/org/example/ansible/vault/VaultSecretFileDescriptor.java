package org.example.ansible.vault;

import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Getter
public class VaultSecretFileDescriptor {

    private static final String ANSIBLE_ENCRYPTION_PREAMBLE = "$ANSIBLE_VAULT;1.1;AES256";

    private static final String DEFAULT_FILE_EXTENSION = "txt";

    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

    private static final Path DEFAULT_DIRECTORY_PATH = Paths.get("/tmp", "/vms", "encrypted");

    private final String encryptedString;
    private final Path directoryPath;
    private final Path tempFilePath;
    private final String payloadToWrite;
    private final String fileExtension;

    public VaultSecretFileDescriptor(String encryptedString, Path directoryPath) {
        this.encryptedString = encryptedString;
        this.fileExtension = DEFAULT_FILE_EXTENSION;
        this.directoryPath = directoryPath;

        var splat = encryptedString.split(":");
        var variableName = splat[0];
        var payload = splat[1];
        var newPayload = payload.split("\\" + ANSIBLE_ENCRYPTION_PREAMBLE)[1];
        var cleanedUpPayload = WHITE_SPACE_PATTERN.matcher(newPayload).replaceAll("");
        this.tempFilePath = Paths.get(directoryPath.toString(), variableName + "." + fileExtension);
        this.payloadToWrite = ANSIBLE_ENCRYPTION_PREAMBLE + System.lineSeparator() + cleanedUpPayload;
    }
}
