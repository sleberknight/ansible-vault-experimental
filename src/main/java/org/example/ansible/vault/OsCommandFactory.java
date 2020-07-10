package org.example.ansible.vault;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

public class OsCommandFactory {

    private final EncryptionConfiguration configuration;

    public OsCommandFactory(EncryptionConfiguration configuration) {
        this.configuration = configuration;
    }

    public OsCommand getOsCommand(VaultCommandType type, String key, String secretName) {
        checkArgumentNotNull(type, "type cannot be null");

        switch (type) {
            case ENCRYPT:
                return AnsibleVaultEncryptCommand.from(configuration, key, secretName);

            case DECRYPT:
                return AnsibleVaultDecryptCommand.from(configuration, key);

            default:
                throw new IllegalStateException("Unknown command type: " + type.name());
        }
    }
}
