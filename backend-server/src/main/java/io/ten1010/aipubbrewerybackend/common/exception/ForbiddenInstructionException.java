package io.ten1010.aipubbrewerybackend.common.exception;

import java.util.List;

public class ForbiddenInstructionException extends RuntimeException {

    private final List<String> forbiddenInstructions;

    public ForbiddenInstructionException(List<String> forbiddenInstructions) {
        super("Dockerfile contains forbidden instructions: " + forbiddenInstructions);
        this.forbiddenInstructions = forbiddenInstructions;
    }

    public List<String> getForbiddenInstructions() {
        return forbiddenInstructions;
    }

}
