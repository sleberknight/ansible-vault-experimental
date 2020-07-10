package org.example.ansible.vault;

import static com.google.common.collect.Lists.newArrayList;
import static org.kiwiproject.base.KiwiStrings.format;

import com.google.common.base.Splitter;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
class OsCommandArguments {

    private static final Splitter SPLITTER = Splitter.on(' ').omitEmptyStrings().trimResults();

    static String getArgumentValue(String commandLine, String argumentFlag) {
        return getArgumentValue(SPLITTER.splitToList(commandLine), argumentFlag);
    }

    static String getArgumentValue(List<String> commandParts, String argumentFlag) {
        var argFlagIndex = commandParts.indexOf(argumentFlag);
        if (argFlagIndex == -1) {
            throw new IllegalArgumentException(
                    format("Argument flag [%s] not found in command parts %s", argFlagIndex, commandParts));
        }

        int argValueIndex = argFlagIndex + 1;
        if (argValueIndex >= commandParts.size()) {
            throw new IllegalArgumentException(
                    format("Argument flag [%s] found at index %s, but the next index %s is beyond the size of the command parts %s",
                            argFlagIndex, argFlagIndex, argValueIndex, commandParts)
            );
        }

        return commandParts.get(argValueIndex);
    }

    static String verbosityArg(boolean verboseOutput, String verboseOutputLevel) {
        return verboseOutput ? format(" %s true", dashedShortOption(verboseOutputLevel)) : "";
    }

    static List<String> verbosityArgsList(boolean verboseOutput, String verboseOutputLevel) {
        return verboseOutput ? newArrayList(dashedShortOption(verboseOutputLevel), "true") : newArrayList();
    }

    static String dashedShortOption(String option) {
        return "-" + option;
    }
}
