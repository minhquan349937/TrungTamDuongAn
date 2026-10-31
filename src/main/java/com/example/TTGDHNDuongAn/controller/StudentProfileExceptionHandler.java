package com.example.TTGDHNDuongAn.controller;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {StudentProfileController.class, GuardianProfileController.class, StaffProfileController.class})
public class StudentProfileExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> businessError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
            error.getReason() == null ? "Không thể xử lý hồ sơ." : error.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> constraintError(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(Map.of("message","Mã hồ sơ đã tồn tại hoặc dữ liệu liên kết không hợp lệ."));
    }
}
