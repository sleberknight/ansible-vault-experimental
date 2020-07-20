package org.example.ansible.vault;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
class Utils {

    public static List<String> subListExcludingLast(List<String> input) {
        return input.subList(0, input.size() - 1);
    }

    public static List<String> subListFrom(List<String> input, int number) {
        return input.subList(number - 1, input.size());
    }
}
