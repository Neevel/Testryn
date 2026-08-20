package com.testryn.project.service;

import com.testryn.common.error.ConflictException;
import com.testryn.common.error.NotFoundException;
import com.testryn.project.domain.Project;
import com.testryn.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class ProjectService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9]{1,19}$");

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project create(String key, String name, String description) {
        String normalizedKey = normalizeKey(key);
        if (projectRepository.existsByKey(normalizedKey)) {
            throw new ConflictException("Project key already in use: " + normalizedKey);
        }
        Project project = Project.create(normalizedKey, name, description);
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Project getByKey(String key) {
        return projectRepository.findByKey(normalizeKey(key))
                .orElseThrow(() -> NotFoundException.of("Project", key));
    }

    @Transactional(readOnly = true)
    public Project getById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Project", id));
    }

    public Project update(String key, String name, String description) {
        Project project = getByKey(key);
        project.rename(name, description);
        return project;
    }

    private String normalizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Project key must not be null");
        }
        String normalized = key.trim().toUpperCase();
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Project key must match " + KEY_PATTERN.pattern() + " (e.g. BITLESS)");
        }
        return normalized;
    }
}
