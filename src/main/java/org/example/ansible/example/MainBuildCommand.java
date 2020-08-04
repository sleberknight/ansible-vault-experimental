package org.example.ansible.example;

import org.example.ansible.vault.OsCommand;
import org.example.ansible.vault.VaultConfiguration;
import org.example.ansible.vault.VaultDecryptCommand;
import org.example.ansible.vault.VaultEncryptStringCommand;

import java.io.File;
import java.nio.file.Path;

/**
 * Simple class to show building a command.
 */
@SuppressWarnings({"java:S106"})
public class MainBuildCommand {

    public static void main(String[] args) {
        var separator = File.separator;
        var ansibleVaultExecPath = Path.of(separator, "usr", "local", "bin", "ansible-vault");
        var vaultPasswordPath = Path.of(".", "src", "main", "resources", "ansible-vault", ".vault_pass");
        var config = VaultConfiguration.builder()
                .ansibleVaultPath(ansibleVaultExecPath.toString())
                .vaultPasswordFilePath(vaultPasswordPath.toString())
                .build();

        var encryptStringCommand = VaultEncryptStringCommand.from(config, "some plain text", "varName");
        printCommand(encryptStringCommand);

        var decryptCommand = VaultDecryptCommand.from(config, "/data/vault/etc/secrets.yml");
        printCommand(decryptCommand);
    }

    private static void printCommand(OsCommand command) {
        System.out.println(command.getCommandParts());
    }
}
