package com.example.TTGDHNDuongAn.service;

import java.util.List;
import java.util.function.Supplier;

import com.example.TTGDHNDuongAn.dto.GoalCatalogDtos;
import com.example.TTGDHNDuongAn.repository.GoalCatalogRepository;
import com.example.TTGDHNDuongAn.repository.CatalogIdGenerator.Kind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class GoalCatalogService {
    private final GoalCatalogRepository repository;
    private final GoalSelectionPolicy selectionPolicy;

    public GoalCatalogService(GoalCatalogRepository repository, GoalSelectionPolicy selectionPolicy) {
        this.repository = repository;
        this.selectionPolicy = selectionPolicy;
    }

    public List<GoalCatalogDtos.Program> programs() {
        return repository.programs();
    }

    public List<GoalCatalogDtos.ProgramLevel> levels(String id) {
        Kind.PROGRAM.validate(id);
        if (!repository.activeProgramExists(id)) throw notFound();
        return repository.levels(id);
    }

    public List<GoalCatalogDtos.Domain> domains(String id) {
        Kind.LEVEL.validate(id);
        if (!repository.levelExists(id)) throw notFound();
        return repository.domains(id);
    }

    public List<GoalCatalogDtos.ProgramDomain> programDomains(String id) {
        Kind.PROGRAM.validate(id);
        if(!repository.activeProgramExists(id)) throw notFound();
        return repository.programDomains(id);
    }
    @Transactional public GoalCatalogDtos.ProgramDomain createProgramDomain(String id,GoalCatalogDtos.CreateDomain request) {
        programDomains(id);
        if(request==null) throw badData();
        text(request.code(),30);text(request.name(),150);order(request.displayOrder());
        return write(()-> { String domain=repository.createProgramDomain(id,request);
            return repository.programDomains(id).stream().filter(d->d.id().equals(domain)).findFirst().orElseThrow(); });
    }
    @Transactional public GoalCatalogDtos.Domain linkDomain(String levelId,GoalCatalogDtos.LinkDomain request) {
        Kind.LEVEL.validate(levelId);
        if(!repository.levelExists(levelId)) throw notFound();
        if(request==null) throw badData();
        Kind.DOMAIN.validate(request.domainId());order(request.displayOrder());
        return write(()-> {String id=repository.linkDomain(levelId,request);
            return repository.domains(levelId).stream().filter(d->id.equals(d.levelDomainId())).findFirst().orElseThrow(this::notFound); });
    }

    public GoalCatalogDtos.Page<GoalCatalogDtos.Goal> goals(String id, String query, int page, int size) {
        Kind.LEVEL_DOMAIN.validate(id);
        if (page < 0 || size < 1 || size > 100 || query.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paging or search parameters");
        if (!repository.activeDomainExists(id)) throw notFound();
        return repository.goals(id, query.trim(), page, size);
    }

    public GoalCatalogDtos.GoalDetail goal(String id) {
        Kind.GOAL.validate(id);
        var parent = repository.goalHeader(id).orElseThrow(this::notFound);
        boolean supportsSpecific = selectionPolicy.supportsSpecificGoals(parent.programCode());
        var children = supportsSpecific ? repository.specificGoals(id) : List.<GoalCatalogDtos.SpecificGoal>of();
        return new GoalCatalogDtos.GoalDetail(parent.id(),parent.code(),parent.name(),parent.description(),children,
                supportsSpecific,selectionPolicy.canSelectGeneralGoal(parent.programCode(),!children.isEmpty()),parent.location());
    }

    public java.util.Map<String,GoalCatalogDtos.GoalDetail> details(java.util.Collection<String> ids) {
        ids.forEach(Kind.GOAL::validate);
        var headers=repository.headers(ids);
        if(headers.size()!=new java.util.HashSet<>(ids).size()) throw notFound();
        var withChildren=headers.values().stream().filter(h->selectionPolicy.supportsSpecificGoals(h.programCode())).map(GoalCatalogDtos.GoalHeader::id).toList();
        var children=repository.children(withChildren);
        return headers.values().stream().collect(java.util.stream.Collectors.toMap(GoalCatalogDtos.GoalHeader::id,h->{
            var rows=children.getOrDefault(h.id(),List.of());
            return new GoalCatalogDtos.GoalDetail(h.id(),h.code(),h.name(),h.description(),rows,
                selectionPolicy.supportsSpecificGoals(h.programCode()),selectionPolicy.canSelectGeneralGoal(h.programCode(),!rows.isEmpty()),h.location());
        },(a,b)->a,java.util.LinkedHashMap::new));
    }

    @Transactional
    public GoalCatalogDtos.GoalDetail createGoal(String domainId, GoalCatalogDtos.CreateGoal request) {
        Kind.LEVEL_DOMAIN.validate(domainId);
        if (!repository.activeDomainExists(domainId)) throw notFound();
        validateGoal(request);
        return write(() -> goal(repository.insertGoal(domainId, request)));
    }

    @Transactional
    public GoalCatalogDtos.Program createProgram(GoalCatalogDtos.CreateProgram request) {
        if (request == null) throw badData();
        text(request.code(), 30); text(request.name(), 255); optionalText(request.description());
        return write(() -> {
            String id = repository.insertProgram(request);
            return new GoalCatalogDtos.Program(id, request.code(), request.name(), request.description());
        });
    }

    @Transactional
    public GoalCatalogDtos.ProgramLevel createLevel(String programId, GoalCatalogDtos.CreateLevel request) {
        Kind.PROGRAM.validate(programId);
        if (!repository.activeProgramExists(programId)) throw notFound();
        if (request == null || request.levelNumber() == null || request.levelNumber() < 1
                || request.ageMinMonths() == null || request.ageMaxMonths() == null) throw badData();
        text(request.name(), 100); order(request.displayOrder());
        ages(request.ageMinMonths(), request.ageMaxMonths());
        return write(() -> new GoalCatalogDtos.ProgramLevel(repository.insertLevel(programId, request), programId,
                request.levelNumber(), request.name(), request.ageMinMonths(), request.ageMaxMonths(), request.displayOrder()));
    }

    @Transactional
    public GoalCatalogDtos.Domain createDomain(String levelId, GoalCatalogDtos.CreateDomain request) {
        Kind.LEVEL.validate(levelId);
        if (!repository.levelExists(levelId)) throw notFound();
        if (request == null) throw badData();
        text(request.code(), 30); text(request.name(), 150); order(request.displayOrder());
        return write(() -> { String id=repository.insertDomain(levelId,request); return repository.domains(levelId).stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(); });
    }

    @Transactional
    public GoalCatalogDtos.GoalDetail createSpecific(String goalId, GoalCatalogDtos.WriteSpecificGoal request) {
        if (!goal(goalId).supportsSpecificGoals())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This program uses main goals only");
        validateSpecific(goalId, request);
        return write(() -> { repository.insertSpecific(goalId, request); return goal(goalId); });
    }

    @Transactional
    public GoalCatalogDtos.GoalDetail updateGoal(String id, GoalCatalogDtos.CreateGoal request) {
        goal(id); validateGoal(request);
        return write(() -> { repository.updateGoal(id, request); return goal(id); });
    }

    @Transactional
    public GoalCatalogDtos.GoalDetail updateSpecific(String goalId, String id, GoalCatalogDtos.WriteSpecificGoal request) {
        requireSpecific(goalId, id); validateSpecific(goalId, request);
        return write(() -> { repository.updateSpecific(goalId, id, request); return goal(goalId); });
    }

    @Transactional
    public void archiveGoal(String id) { goal(id); repository.archiveGoal(id); }

    @Transactional
    public void archiveSpecific(String goalId, String id) {
        requireSpecific(goalId, id); repository.archiveSpecific(id);
    }

    private void requireSpecific(String goalId, String id) {
        Kind.SPECIFIC.validate(id);
        if (goal(goalId).specificGoals().stream().noneMatch(child -> child.id().equals(id))) throw notFound();
    }

    private void validateGoal(GoalCatalogDtos.CreateGoal request) {
        if (request == null || request.code() == null || request.name() == null
                || request.code().isBlank() || request.name().isBlank()
                || request.code().length() > 50
                || (request.displayOrder() != null && request.displayOrder() < 0)) throw badData();
        optionalText(request.name()); optionalText(request.description());
    }

    private void validateSpecific(String goalId, GoalCatalogDtos.WriteSpecificGoal request) {
        if (request == null) throw badData();
        text(request.code(), 50); text(request.description(), 65535); order(request.displayOrder());
        ages(request.ageMinMonths(), request.ageMaxMonths());
        var range = repository.goalAgeRange(goalId);
        int min = request.ageMinMonths() == null ? range[0] : request.ageMinMonths();
        int max = request.ageMaxMonths() == null ? range[1] : request.ageMaxMonths();
        if (min > max) throw badData();
    }

    private void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw badData();
        optionalText(value);
    }
    private void optionalText(String value) {
        if (value != null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535) throw badData();
    }
    private void order(Integer value) { if (value == null || value < 0) throw badData(); }
    private void ages(Integer min, Integer max) {
        if ((min != null && min < 0) || (max != null && max < 0)
                || (min != null && max != null && min > max)) throw badData();
    }
    private ResponseStatusException badData() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog data, age range or display order");
    }
    private <T> T write(Supplier<T> action) {
        try { return action.get(); }
        catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate code or referenced catalog data has changed", ex);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog item not found");
    }
}
