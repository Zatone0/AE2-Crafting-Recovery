package io.github.zatone0.ae2craftingrecovery.diagnostic;

import java.util.List;

public record PatternBlockage(
        long remainingOperations,
        List<String> missingInputs,
        List<String> rootMissingInputs,
        String patternDescription) {
}
