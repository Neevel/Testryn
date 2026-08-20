package com.testryn.project.web;

import com.testryn.project.domain.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 20) String key,
            @NotBlank @Size(max = 255) String name,
            String description
    ) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 255) String name,
            String description
    ) {
    }

    public record ProjectResponse(
            UUID id,
            String key,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getKey(),
                    project.getName(),
                    project.getDescription(),
                    project.getCreatedAt(),
                    project.getUpdatedAt()
            );
        }
    }
}
