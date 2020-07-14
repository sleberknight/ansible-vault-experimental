package org.example.ansible.vault;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is a simple class to exercise the ansible vault code manually, and have it
 * actually execute ansible-vault.
 */
@SuppressWarnings({"java:S106", "java:S1192"})
public class Main {

    public static void main(String[] args) throws IOException {
        var separator = File.separator;

        // Assumes path is /usr/local/bin/ansible-vault ... change if different!
        var ansibleVaultExecPath = Path.of(separator, "usr", "local", "bin", "ansible-vault");

        // Use files in this project (in src/main/resources)
        var vaultPasswordPath = Path.of(".", "src", "main", "resources", "encryption", ".vault_pass");
        var tempPath = Path.of(".", "src", "main", "resources", "encryption", "temp");
        var encryptedPath = Path.of(".", "src", "main", "resources", "encryption", "encrypted_key_file.txt");

        // Use files in /tmp/vault-play
//        var vaultPasswordPath = Path.of(separator, "tmp", "vault-play", ".vault_pass");
//        var tempPath = Path.of(separator, "tmp", "vault-play", "temp");
//        var encryptedPath = Path.of(separator, "tmp", "vault-play", "baz.yml");

        System.out.println("Reading from encrypted path: " + encryptedPath);
        System.out.println("Using vault password file: " + vaultPasswordPath);
        System.out.println("Using temporary folder: " + tempPath);
        System.out.println();

        var encryptedContent = Files.readString(encryptedPath);
        System.out.println("Encrypted content:");
        System.out.println(encryptedContent);
        System.out.println();

        var config = EncryptionConfiguration.builder()
                .ansibleVaultPath(ansibleVaultExecPath.toString())
                .vaultPasswordFilePath(vaultPasswordPath.toString())
                .tempDirectory(tempPath.toString())
                .build();

        var helper = new VaultEncryptionHelper();

        var decryptedValue = helper.getDecryptedKeyValue(encryptedContent, config);
        printDecryptedValue(decryptedValue);

        // --- Do several encrypt/decrypt cycles ----

        var secretName = "VaultSecret";
        var plainText = decryptedValue;

        for (int i = 1; i <= 5; i++) {
            System.out.printf(
                    "---------- Iteration %d -------------------------------------------------------------------%n%n", i);

            var encryptedValue = helper.getEncryptedValue(plainText, secretName, config);
            printEncryptedValue(encryptedValue);

            var reDecryptedValue = helper.getDecryptedKeyValue(encryptedValue, config);
            printDecryptedValue(reDecryptedValue);

            plainText = reDecryptedValue;

            System.out.println();
        }
    }

    private static void printDecryptedValue(String decryptedValue) {
        System.out.printf("Decrypted value: [%s]%n", decryptedValue);
    }

    private static void printEncryptedValue(String encryptedValue) {
        System.out.println("Encrypted value:");
        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println(encryptedValue);
        System.out.println("------------------------------------------------------------------------------------------");
    }
}
