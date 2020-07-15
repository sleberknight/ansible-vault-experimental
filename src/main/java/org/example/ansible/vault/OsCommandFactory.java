package org.example.ansible.vault;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

class OsCommandFactory {

    private final VaultConfiguration configuration;

    public OsCommandFactory(VaultConfiguration configuration) {
        this.configuration = configuration;
    }

    // TODO Not sure it's worth having this entire class at all, since the arguments are so different
    //  By renaming them to what they are in the encrypt_string and decrypt cases, that becomes very noticeable!
    //  Plus variableName is only used for encrypt_string
    OsCommand getOsCommand(VaultCommandType type,
                           String plainTextOrEncryptedFileName,
                           String variableName) {

        checkArgumentNotNull(type, "type cannot be null");

        switch (type) {
            case ENCRYPT_STRING:
                return VaultEncryptStringCommand.from(configuration, plainTextOrEncryptedFileName, variableName);

            case DECRYPT:
                return VaultDecryptCommand.from(configuration, plainTextOrEncryptedFileName);

            default:
                throw new IllegalStateException("Unknown command type: " + type.name());
        }
    }
}
