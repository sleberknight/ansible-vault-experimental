package org.example.ansible.vault;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

public class OsCommandFactory {

    private final VaultConfiguration configuration;

    public OsCommandFactory(VaultConfiguration configuration) {
        this.configuration = configuration;
    }

    public OsCommand getOsCommand(VaultCommandType type, String key, String secretName) {
        checkArgumentNotNull(type, "type cannot be null");

        switch (type) {
            case ENCRYPT:
                return VaultEncryptStringCommand.from(configuration, key, secretName);

            case DECRYPT:
                return VaultDecryptCommand.from(configuration, key);

            default:
                throw new IllegalStateException("Unknown command type: " + type.name());
        }
    }
}
