package org.example.ansible.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionConfiguration {

    private String ansibleVaultPath;
    private String vaultPasswordFilePath;
    private String tempDirectory;

}
