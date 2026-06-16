package io.ten1010.imagekitbackend.dockerfile.dto;

import io.ten1010.imagekitbackend.dockerfile.entity.Dockerfile;
import io.ten1010.imagekitbackend.dockerfile.entity.DockerfileRevision;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DockerfileMapper {

    @Mapping(target = "latestVersion", expression = "java(dockerfile.getLatestRevision() != null ? dockerfile.getLatestRevision().getVersion() : null)")
    @Mapping(target = "latestRevisionId", expression = "java(dockerfile.getLatestRevision() != null ? dockerfile.getLatestRevision().getId() : null)")
    DockerfileResponse toResponse(Dockerfile dockerfile);

    List<DockerfileResponse> toResponseList(List<Dockerfile> dockerfiles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "latestRevision", ignore = true)
    Dockerfile toEntity(DockerfileCreateRequest request);

    @Mapping(target = "dockerfileId", source = "dockerfile.id")
    DockerfileRevisionResponse toRevisionResponse(DockerfileRevision revision);

    List<DockerfileRevisionResponse> toRevisionResponseList(List<DockerfileRevision> revisions);

}
