package io.ten1010.aipubbrewerybackend.dockerfile.dto;

import io.ten1010.aipubbrewerybackend.dockerfile.entity.BuildContextFile;
import io.ten1010.aipubbrewerybackend.dockerfile.entity.Dockerfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DockerfileMapper {

    DockerfileResponse toResponse(Dockerfile dockerfile);

    List<DockerfileResponse> toResponseList(List<Dockerfile> dockerfiles);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "contextFiles", ignore = true)
    Dockerfile toEntity(DockerfileCreateRequest request);

    BuildContextFileResponse toFileResponse(BuildContextFile file);

    List<BuildContextFileResponse> toFileResponseList(List<BuildContextFile> files);

}
