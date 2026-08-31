package com.testryn.project.web;

import com.testryn.project.domain.Project;
import com.testryn.common.service.DeletionService;
import com.testryn.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

import static com.testryn.project.web.ProjectDtos.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final DeletionService deletionService;

    public ProjectController(ProjectService projectService, DeletionService deletionService) {
        this.projectService = projectService;
        this.deletionService = deletionService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.create(request.key(), request.name(), request.description());
        ProjectResponse body = ProjectResponse.from(project);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getKey())).body(body);
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{projectKey}")
    public ProjectResponse getByKey(@PathVariable String projectKey) {
        return ProjectResponse.from(projectService.getByKey(projectKey));
    }

    @PutMapping("/{projectKey}")
    public ProjectResponse update(@PathVariable String projectKey, @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(projectService.update(projectKey, request.name(), request.description()));
    }
    @DeleteMapping("/{projectKey}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String projectKey) { deletionService.deleteProject(projectKey); }
}
