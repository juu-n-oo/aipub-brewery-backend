package io.ten1010.aipubbrewerybackend.dockerfile.service;

import io.ten1010.aipubbrewerybackend.common.exception.ForbiddenInstructionException;
import io.ten1010.aipubbrewerybackend.common.exception.ResourceNotFoundException;
import io.ten1010.aipubbrewerybackend.dockerfile.dto.*;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import io.ten1010.aipubbrewerybackend.dockerfile.repository.DockerfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DockerfileServiceTest {

    @Mock
    private DockerfileRepository repository;

    @Mock
    private DockerfileMapper mapper;

    @Mock
    private DockerfileValidator validator;

    @InjectMocks
    private DockerfileService service;

    private Dockerfile sampleEntity() {
        return Dockerfile.builder()
                .id(1L)
                .project("pjw")
                .username("joonwoo")
                .name("pytorch-cuda12")
                .description("PyTorch 2.1 기반 학습 환경")
                .content("FROM pytorch/pytorch:2.1.0\nRUN pip install transformers")
                .contextFiles(new ArrayList<>())
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    private DockerfileResponse sampleResponse() {
        return DockerfileResponse.builder()
                .id(1L)
                .project("pjw")
                .username("joonwoo")
                .name("pytorch-cuda12")
                .description("PyTorch 2.1 기반 학습 환경")
                .content("FROM pytorch/pytorch:2.1.0\nRUN pip install transformers")
                .contextFiles(List.of())
                .createdAt(Instant.parse("2026-04-18T00:00:00Z"))
                .updatedAt(Instant.parse("2026-04-18T00:00:00Z"))
                .build();
    }

    @Test
    void createValidDockerfile() {
        DockerfileCreateRequest request = new DockerfileCreateRequest();
        request.setProject("pjw");
        request.setUsername("joonwoo");
        request.setName("pytorch-cuda12");
        request.setContent("FROM pytorch/pytorch:2.1.0\nRUN pip install transformers");

        Dockerfile entity = sampleEntity();
        willDoNothing().given(validator).validate(any());
        given(mapper.toEntity(request)).willReturn(entity);
        given(repository.save(entity)).willReturn(entity);
        given(mapper.toResponse(entity)).willReturn(sampleResponse());

        DockerfileResponse result = service.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProject()).isEqualTo("pjw");
        assertThat(result.getContextFiles()).isEmpty();
        verify(validator).validate(request.getContent());
    }

    @Test
    void createDockerfileWithForbiddenInstructionThrows() {
        DockerfileCreateRequest request = new DockerfileCreateRequest();
        request.setContent("FROM ubuntu\nADD file.tar.gz /app/");

        willThrow(new ForbiddenInstructionException(List.of("ADD")))
                .given(validator).validate(any());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ForbiddenInstructionException.class);
    }

    @Test
    void getByIdReturnsDockerfile() {
        Dockerfile entity = sampleEntity();
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(mapper.toResponse(entity)).willReturn(sampleResponse());

        DockerfileResponse result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContextFiles()).isEmpty();
    }

    @Test
    void getByIdNotFoundThrows() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listByProjectReturnsResults() {
        List<Dockerfile> entities = List.of(sampleEntity());
        List<DockerfileResponse> responses = List.of(sampleResponse());
        given(repository.findByProject("pjw")).willReturn(entities);
        given(mapper.toResponseList(entities)).willReturn(responses);

        List<DockerfileResponse> result = service.listByProject("pjw");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getProject()).isEqualTo("pjw");
    }

    @Test
    void updateDockerfileContent() {
        Dockerfile entity = sampleEntity();
        DockerfileUpdateRequest request = new DockerfileUpdateRequest();
        request.setName("pytorch-cuda12-v2");
        request.setDescription("PyTorch 2.1 기반 학습 환경 (업데이트)");
        request.setContent("FROM pytorch/pytorch:2.1.0\nRUN pip install transformers datasets");

        willDoNothing().given(validator).validate(any());
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.save(entity)).willReturn(entity);
        given(mapper.toResponse(entity)).willReturn(sampleResponse());

        DockerfileResponse result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(entity.getName()).isEqualTo("pytorch-cuda12-v2");
        assertThat(entity.getDescription()).isEqualTo("PyTorch 2.1 기반 학습 환경 (업데이트)");
        verify(validator).validate(request.getContent());
    }

    @Test
    void deleteExistingDockerfile() {
        given(repository.existsById(1L)).willReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteNonExistentDockerfileThrows() {
        given(repository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

}
