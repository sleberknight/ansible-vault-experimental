package org.example.ansible.vault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VaultConfiguration")
class VaultConfigurationTest {

    @Nested
    class Builder {

        @Test
        void shouldUseTempDirectoryIfSupplied() {
            var config = VaultConfiguration.builder()
                    .ansibleVaultPath("/usr/bin/ansible-vault")
                    .vaultPasswordFilePath("/data/vault/.vault_pass")
                    .tempDirectory("/data/vault/tmp")
                    .build();

            assertThat(config.getTempDirectory()).isEqualTo("/data/vault/tmp");
        }

        @Test
        void shouldAssignTempDirectoryIfNotSupplied() {
            var config = VaultConfiguration.builder()
                    .ansibleVaultPath("/usr/bin/ansible-vault")
                    .vaultPasswordFilePath("/data/vault/.vault_pass")
                    .build();

            assertTempDirectoryIsJavaTempDir(config);
        }
    }

    @Nested
    class NoArgsConstructor {

        @Test
        void shouldAssignTempDirectory() {
            var config = new VaultConfiguration();

            assertTempDirectoryIsJavaTempDir(config);
        }
    }

    @Nested
    class Copy {

        @Test
        void shouldCreateCopy() {
            var original = VaultConfiguration.builder()
                    .ansibleVaultPath("/usr/bin/ansible-vault")
                    .vaultPasswordFilePath("/data/vault/.vault_pass")
                    .build();

            var copy = original.copyOf();

            assertThat(copy)
                    .isNotSameAs(original)
                    .isEqualToComparingFieldByField(original);
        }
    }

    private void assertTempDirectoryIsJavaTempDir(VaultConfiguration config) {
        assertThat(config.getTempDirectory()).isEqualTo(System.getProperty("java.io.tmpdir"));
    }
}