package org.example.ansible.example;

import org.example.ansible.vault.VaultConfiguration;
import org.example.ansible.vault.VaultEncryptionHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is a simple class to exercise the ansible vault code manually, and have it
 * actually execute ansible-vault.
 */
@SuppressWarnings({"java:S106", "java:S1192", "java:S125"})
public class Main {

    public static void main(String[] args) throws IOException {
        var separator = File.separator;

        // Assumes path is /usr/local/bin/ansible-vault ... change if different!
        var ansibleVaultExecPath = Path.of(separator, "usr", "local", "bin", "ansible-vault");

        // Use files in this project (in src/main/resources)
//        var fileName = "encrypt_string_1.1.txt";
        var fileName = "encrypt_string_1.2.txt";
        var vaultPasswordPath = Path.of(".", "src", "main", "resources", "ansible-vault", ".vault_pass");
        var tempPath = Path.of(".", "src", "main", "resources", "ansible-vault", "temp");
        var encryptedPath = Path.of(".", "src", "main", "resources", "ansible-vault", fileName);

        // Use files in /tmp/vault-play
//        var vaultPasswordPath = Path.of(separator, "tmp", "vault-play", ".vault_pass");
//        var tempPath = Path.of(separator, "tmp", "vault-play", "temp");
//        var encryptedPath = Path.of(separator, "tmp", "vault-play", "baz.yml");

        System.out.println("Reading from encrypted path: " + encryptedPath);
        System.out.println("Using vault password file: " + vaultPasswordPath);
        System.out.println("Using temporary folder: " + tempPath);
        System.out.println();

        var encryptedString = Files.readString(encryptedPath, StandardCharsets.UTF_8);
        System.out.println("Encrypted string:");
        System.out.println(encryptedString);
        System.out.println();

        var config = VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultExecPath.toString())
                .vaultPasswordFilePath(vaultPasswordPath.toString())
                .tempDirectory(tempPath.toString())
                .build();

        var helper = new VaultEncryptionHelper(config);

        var decryptedValue = helper.decryptString(encryptedString);
        printDecryptedValue(decryptedValue);

        // --- Do several encrypt/decrypt cycles ----

        var variableName = "MyVaultSecret";
        var plainText = decryptedValue;

        for (int i = 1; i <= 5; i++) {
            System.out.printf(
                    "---------- Iteration %d -------------------------------------------------------------------%n%n", i);

            var encryptedValue = helper.encryptString(plainText, variableName);
            printEncryptedValue(encryptedValue);

            var reDecryptedValue = helper.decryptString(encryptedValue);
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
