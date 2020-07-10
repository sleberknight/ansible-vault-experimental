package org.example.ansible.vault;

import lombok.Builder;

import java.util.List;

@Builder
public class AnsibleVaultEncryptCommand implements OsCommand {

    private final String vaultId;
    private final String applicationPath;
    private final String secretName;
    private final String secret;

    public static OsCommand from(EncryptionConfiguration configuration, String key, String secretName) {
        return AnsibleVaultEncryptCommand.builder()
                .applicationPath(configuration.getAnsibleVaultPath())
                .vaultId(configuration.getAnsibleVaultIdPath())
                .secretName(secretName)
                .secret(key)
                .build();
    }

    // Changed original code which used --vault-id to instead use --vault-password-file
    // which seems to be what it should have done. It just so happens that using
    // the --vault-id option with just the password file works, e.g. instead of
    // --vault-id theVaultId@.vault_pass you can use it like this: --vault-id .vault_pass
    // And it will work...but since the original code passes the password file to
    // --vault-id I am speculating it should have been --vault-password-file in the first
    // place.
    @Override
    public List<String> getOsCommandParts() {
        return List.of(
                applicationPath, "encrypt_string", secret,
//                "--vault-id", vaultId,
                "--vault-password-file", vaultId,
                "--name", secretName
        );
    }
}
