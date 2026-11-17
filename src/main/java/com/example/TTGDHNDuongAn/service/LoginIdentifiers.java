package com.example.TTGDHNDuongAn.service;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class LoginIdentifiers {
    private LoginIdentifiers() {}
    public static String email(String value) {
        if(value==null || value.isBlank()) return null;
        String result=value.strip().toLowerCase(Locale.ROOT);
        if(result.length()>254 || !result.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw invalid("Email đăng nhập không hợp lệ.");
        return result;
    }
    public static String phone(String value) {
        if(value==null || value.isBlank()) return null;
        if(value.length()>50) throw invalid("Số điện thoại không hợp lệ.");
        String result=value.strip().replaceAll("[\\s.()\\-]","");
        if(result.startsWith("+84")) result="0"+result.substring(3);
        else if(result.startsWith("84") && (result.length()==11 || result.length()==12)) result="0"+result.substring(2);
        if(!result.matches("\\+?[0-9]{8,15}")) throw invalid("Số điện thoại phải gồm 8–15 chữ số.");
        return result;
    }
    public static String login(String value) {
        if(value==null || value.isBlank()) return "";
        return value.contains("@")?email(value):phone(value);
    }
    private static ResponseStatusException invalid(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
