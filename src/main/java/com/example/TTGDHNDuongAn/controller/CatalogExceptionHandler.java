package com.example.TTGDHNDuongAn.controller;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {GoalCatalogController.class, PlanGoalController.class, StudentGoalBankController.class, InterventionController.class, PlanProgressController.class})
public class CatalogExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "status", exception.getStatusCode().value(),
                "message", exception.getReason() == null ? "Catalog request failed" : exception.getReason()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,Object>> databaseError(DataAccessException exception) {
        for (Throwable cause=exception; cause!=null; cause=cause.getCause()) {
            if (cause instanceof SQLException sql && ("45000".equals(sql.getSQLState())
                    || (sql.getSQLState()!=null && sql.getSQLState().startsWith("23")))) {
                return ResponseEntity.status(409).body(Map.of("status",409,
                        "message","Catalog reference changed, goal is inactive or the record is already in use"));
            }
        }
        return ResponseEntity.status(500).body(Map.of("status",500,
                "message","Catalog database request failed; check schema version and server logs"));
    }
}
