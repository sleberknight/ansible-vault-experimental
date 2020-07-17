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
public class VaultConfiguration {

    private String ansibleVaultPath;
    private String vaultPasswordFilePath;

    // TODO Should this default to the user's tmp dir?
    private String tempDirectory;

}
