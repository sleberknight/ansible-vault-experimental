package org.example.ansible.vault;

import java.util.List;
import java.util.function.Supplier;

public interface OsCommand extends Supplier<List<String>> {

    List<String> getOsCommandParts();

    default List<String> get() {
        return getOsCommandParts();
    }
}
