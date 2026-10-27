package com.example.TTGDHNDuongAn.service;

import java.util.List;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.StudentPlanDtos;
import com.example.TTGDHNDuongAn.repository.StudentPlanRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentPlanService {
    private final StudentPlanRepository repository;

    public StudentPlanService(StudentPlanRepository repository) {
        this.repository = repository;
    }

    public List<StudentPlanDtos.Student> students(String search) {
        if (search == null || search.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search");
        return repository.students(search.trim());
    }

    public StudentPlanDtos.Student createStudent(StudentPlanDtos.CreateStudent request) {
        if (request == null || request.code() == null || request.fullName() == null
                || request.birthDate() == null || request.admittedOn() == null
                || request.code().isBlank() || request.code().length() > 30
                || request.fullName().isBlank() || request.fullName().length() > 150
                || request.birthDate().isAfter(request.admittedOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid student data");
        try {
            return repository.insertStudent(request);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student code already exists", ex);
        }
    }

    public List<StudentPlanDtos.Plan> plans(String studentId) {
        requireStudent(studentId);
        return repository.plans(studentId);
    }

    public StudentPlanDtos.Plan createPlan(String studentId, StudentPlanDtos.CreatePlan request, String actorId) {
        requireStudent(studentId);
        if (request == null || request.name() == null || request.startDate() == null
                || request.name().isBlank() || request.name().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid plan data");
        return repository.insertPlan(studentId, request, actorId);
    }

    private void requireStudent(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID", ex);
        }
        if (repository.student(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found");
    }
}
