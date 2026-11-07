package com.example.TTGDHNDuongAn.controller;

import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {WebsitePublicController.class, WebsiteAdminController.class})
public class WebsiteExceptionHandler {
    private static ResponseEntity<Map<String, Object>> reply(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> business(ResponseStatusException error) {
        return reply(error.getStatusCode().value(), error.getReason() == null ? "Không thể xử lý yêu cầu." : error.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException error) {
        return reply(400, "Dữ liệu gửi lên không hợp lệ.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException error) {
        return reply(413, "Tệp vượt quá dung lượng cho phép (ảnh 5 MB, video 50 MB).");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> database(DataAccessException error) {
        return reply(500, "Không thể lưu dữ liệu website. Kiểm tra đã chạy migration V14 và xem nhật ký máy chủ.");
    }
}
