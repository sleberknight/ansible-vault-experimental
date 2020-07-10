package org.example.ansible.vault;

import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

@Getter
public class SecretFileDescriptor {

    private static final String ANSIBLE_ENCRYPTION_PREAMBLE = "$ANSIBLE_VAULT;1.1;AES256";

    private static final String DEFAULT_FILE_EXTENSION = "txt";

    private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\s+");

    private static final Path DEFAULT_DIRECTORY_PATH = Paths.get("/tmp", "/vms", "encrypted");

    private final String key;
    private final Path directoryPath;
    private final Path tempKeyFile;
    private final String payloadToWrite;
    private final String fileExtension;

    public SecretFileDescriptor(String key, Path directoryPath) {
        this.key = key;
        this.fileExtension = DEFAULT_FILE_EXTENSION;
        this.directoryPath = directoryPath;

        var keyParts = key.split(":");
        var keyFileName = keyParts[0];
        var payload = keyParts[1];
        var newPayload = payload.split("\\" + ANSIBLE_ENCRYPTION_PREAMBLE)[1];
        var cleanedUpPayload = WHITE_SPACE_PATTERN.matcher(newPayload).replaceAll("");
        this.tempKeyFile = Paths.get(directoryPath.toString(), keyFileName + "." + fileExtension);
        this.payloadToWrite = ANSIBLE_ENCRYPTION_PREAMBLE + System.lineSeparator() + cleanedUpPayload;
    }
}
