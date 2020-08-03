package org.example.ansible.vault;

import static com.google.common.base.Verify.verify;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple class to exercise ansible-vault decrypt manually (in place replacement).
 */
@SuppressWarnings({"java:S106", "java:S1192", "java:S125"})
public class MainDecryptFile {

    public static void main(String[] args) throws IOException {
        var separator = File.separator;

        // Assumes path is /usr/local/bin/ansible-vault ... change if different!
        var ansibleVaultExecPath = Path.of(separator, "usr", "local", "bin", "ansible-vault");

        var vaultPasswordPath = Path.of(".", "src", "main", "resources", "ansible-vault", ".vault_pass");

        var tmpDir = Path.of("/tmp/vault-decrypt");
        FileUtils.deleteDirectory(tmpDir.toFile());

        Files.createDirectory(tmpDir);
        var filePath = Path.of(tmpDir.toString(), "tmp" + System.nanoTime() + ".txt");
        var plainText = "some plain text" + System.lineSeparator();
        var textFile = Files.writeString(filePath, plainText);

        var config = VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultExecPath.toString())
                .vaultPasswordFilePath(vaultPasswordPath.toString())
                .build();

        var helper = new VaultEncryptionHelper(config);

        var encryptedFile = helper.encryptFile(textFile.toString(), config);

        System.out.println("Encrypted file: " + encryptedFile);

        var decryptedFile = helper.decryptFile(encryptedFile.toString(), config);

        System.out.println("Decrypted file: " + decryptedFile);

        var content = Files.readString(decryptedFile, StandardCharsets.UTF_8);

        verify(plainText.equals(content),
                "decrypted content [%s] and plain text [%s] do not match",
                content, plainText);
    }
}
