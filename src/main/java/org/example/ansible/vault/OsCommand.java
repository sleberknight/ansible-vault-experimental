package org.example.ansible.vault;

import java.util.List;
import java.util.function.Supplier;

interface OsCommand extends Supplier<List<String>> {

    List<String> getOsCommandParts();

    default List<String> get() {
        return getOsCommandParts();
    }
}
