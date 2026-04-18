package io.ten1010.aipubbrewerybackend.dockerfile.service;

import io.ten1010.aipubbrewerybackend.common.config.BreweryProperties;
import io.ten1010.aipubbrewerybackend.common.exception.ForbiddenInstructionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DockerfileValidator {

    private final BreweryProperties properties;

    public void validate(String content) {
        List<String> forbidden = properties.getDockerfile().getForbiddenInstructions();
        List<String> found = forbidden.stream()
                .filter(instruction -> containsInstruction(content, instruction))
                .toList();
        if (!found.isEmpty()) {
            throw new ForbiddenInstructionException(found);
        }
    }

    private boolean containsInstruction(String content, String instruction) {
        Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(instruction) + "\\s", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        return pattern.matcher(content).find();
    }

}
