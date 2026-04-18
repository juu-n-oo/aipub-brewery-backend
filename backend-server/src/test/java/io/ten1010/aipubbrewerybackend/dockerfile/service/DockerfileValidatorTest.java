package io.ten1010.aipubbrewerybackend.dockerfile.service;

import io.ten1010.aipubbrewerybackend.common.config.BreweryProperties;
import io.ten1010.aipubbrewerybackend.common.exception.ForbiddenInstructionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerfileValidatorTest {

    private DockerfileValidator validator;

    @BeforeEach
    void setUp() {
        BreweryProperties properties = new BreweryProperties();
        BreweryProperties.DockerfileConfig config = new BreweryProperties.DockerfileConfig();
        config.setForbiddenInstructions(List.of("ADD"));
        properties.setDockerfile(config);
        validator = new DockerfileValidator(properties);
    }

    @Test
    void allowsDockerfileWithoutCopyOrAdd() {
        String content = """
                FROM pytorch/pytorch:2.1.0
                RUN pip install transformers
                ENV APP_HOME=/app
                WORKDIR /app
                CMD ["python", "main.py"]
                """;

        assertThatNoException().isThrownBy(() -> validator.validate(content));
    }

    @Test
    void allowsCopyInstruction() {
        String content = """
                FROM pytorch/pytorch:2.1.0
                COPY requirements.txt /app/
                RUN pip install -r /app/requirements.txt
                """;

        assertThatNoException().isThrownBy(() -> validator.validate(content));
    }

    @Test
    void rejectsAddInstruction() {
        String content = """
                FROM ubuntu:22.04
                ADD https://example.com/file.tar.gz /app/
                """;

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ForbiddenInstructionException.class);
    }

    @Test
    void allowsCaseInsensitiveCopy() {
        String content = """
                FROM ubuntu:22.04
                copy localfile.txt /app/
                """;

        assertThatNoException().isThrownBy(() -> validator.validate(content));
    }

    @Test
    void rejectsAddButAllowsCopy() {
        String content = """
                FROM ubuntu:22.04
                COPY file1.txt /app/
                ADD file2.tar.gz /app/
                """;

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ForbiddenInstructionException.class)
                .satisfies(ex -> {
                    ForbiddenInstructionException fie = (ForbiddenInstructionException) ex;
                    assertThatList(fie.getForbiddenInstructions()).containsExactly("ADD");
                });
    }

    @Test
    void allowsCopyInComment() {
        String content = """
                FROM pytorch/pytorch:2.1.0
                # COPY requirements.txt /app/
                RUN pip install transformers
                """;

        assertThatNoException().isThrownBy(() -> validator.validate(content));
    }

    @Test
    void allowsCopyInRunCommand() {
        String content = """
                FROM pytorch/pytorch:2.1.0
                RUN cp /tmp/file /app/COPY_THIS
                """;

        assertThatNoException().isThrownBy(() -> validator.validate(content));
    }

    private static org.assertj.core.api.ListAssert<String> assertThatList(List<String> actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }

}
