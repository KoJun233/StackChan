package com.kj.stackchan.agent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

final class FilteringSkillRegistry implements SkillRegistry {

    private final SkillRegistry delegate;
    private final Set<String> allowedSkillNames;

    FilteringSkillRegistry(SkillRegistry delegate, Set<String> allowedSkillNames) {
        this.delegate = delegate;
        this.allowedSkillNames = Set.copyOf(allowedSkillNames);
    }

    @Override
    public Optional<SkillMetadata> get(String name) {
        return allowedSkillNames.contains(name) ? delegate.get(name) : Optional.empty();
    }

    @Override
    public List<SkillMetadata> listAll() {
        return delegate.listAll().stream()
                .filter(skill -> allowedSkillNames.contains(skill.getName()))
                .toList();
    }

    @Override
    public boolean contains(String name) {
        return allowedSkillNames.contains(name) && delegate.contains(name);
    }

    @Override
    public int size() {
        return listAll().size();
    }

    @Override
    public void reload() {
        delegate.reload();
    }

    @Override
    public String readSkillContent(String name) throws java.io.IOException {
        if (!allowedSkillNames.contains(name)) {
            throw new IllegalStateException("Skill not found: " + name);
        }
        return delegate.readSkillContent(name);
    }

    @Override
    public String getSkillLoadInstructions() {
        return "Only the skills listed in Available Skills are authorized for this request.";
    }

    @Override
    public String getRegistryType() {
        return "filtered-managed-filesystem";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        return delegate.getSystemPromptTemplate();
    }
}
