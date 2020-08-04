package org.example.ansible.example;

import static com.google.common.base.Verify.verify;

import org.apache.commons.io.FileUtils;
import org.example.ansible.vault.VaultConfiguration;
import org.example.ansible.vault.VaultEncryptionHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple class to exercise ansible-vault encrypt manually.
 */
@SuppressWarnings({"java:S106", "java:S1192", "java:S125"})
public class MainEncryptFile {

    public static void main(String[] args) throws IOException {
        var separator = File.separator;

        // Assumes path is /usr/local/bin/ansible-vault ... change if different!
        var ansibleVaultExecPath = Path.of(separator, "usr", "local", "bin", "ansible-vault");

        var vaultPasswordPath = Path.of(".", "src", "main", "resources", "ansible-vault", ".vault_pass");

        var tmpDir = Path.of("/tmp/vault-encrypt");
        FileUtils.deleteDirectory(tmpDir.toFile());

        Files.createDirectory(tmpDir);
        var filePath = Path.of(tmpDir.toString(), "tmp" + System.nanoTime() + ".txt");
        var textFile = Files.writeString(filePath, "some plain text" + System.lineSeparator());

        var config = VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultExecPath.toString())
                .vaultPasswordFilePath(vaultPasswordPath.toString())
                .build();

        var helper = new VaultEncryptionHelper(config);

        var encryptedFile = helper.encryptFile(textFile);

        verify(encryptedFile.equals(textFile), "encryptedFile (%s) != textFile (%s)",
                encryptedFile, textFile);

        var encryptedContent = Files.readString(encryptedFile, StandardCharsets.UTF_8);

        System.out.println("Encrypted file: " + encryptedFile);
        System.out.println("Encrypted file content:");
        System.out.println(encryptedContent);
        System.out.println("----- End encrypted file content -----");

        System.out.println();
        System.out.println("Now cause failure by trying to encrypt the already-encrypted file...");

        try {
            helper.encryptFile(encryptedFile);
        } catch (Exception e) {
            System.err.println("Error encrypting " + textFile);
            System.err.println(e.getClass());
            System.err.println(e.getMessage());
        }
    }
}
