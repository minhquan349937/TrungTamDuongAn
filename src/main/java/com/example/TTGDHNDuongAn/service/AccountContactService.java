package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountContactService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AccountContactService(JdbcTemplate jdbc,PasswordEncoder encoder) {this.jdbc=jdbc;this.encoder=encoder;}
    public void available(String email,String phone,String exceptUser) {
        email=LoginIdentifiers.email(email);phone=LoginIdentifiers.phone(phone);
        if(email==null && phone==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần có số điện thoại hoặc email đăng nhập.");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE (email=? OR phone=?) AND (? IS NULL OR id<>?)",Integer.class,email,phone,exceptUser,exceptUser);
        if(count!=null && count>0) throw duplicate();
    }
    @Transactional
    public String create(String name,String email,String phone,String role) {
        email=LoginIdentifiers.email(email);phone=LoginIdentifiers.phone(phone);available(email,phone,null);
        String id=ShortIds.next(jdbc,"app_user");
        try {
            jdbc.update("INSERT INTO app_user(id,email,phone,password_hash,display_name,role,status) VALUES(?,?,?,?,?,?,'ACTIVE')",
                id,email,phone,encoder.encode("123456"),name.strip(),role);
        } catch(DuplicateKeyException ex) {throw duplicate();}
        return id;
    }
    @Transactional
    public void update(String id,String name,String email,String phone) {
        email=LoginIdentifiers.email(email);phone=LoginIdentifiers.phone(phone);available(email,phone,id);
        try {
            if(jdbc.update("UPDATE app_user SET email=?,phone=?,display_name=? WHERE id=?",email,phone,name.strip(),id)==0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản.");
        } catch(DuplicateKeyException ex) {throw duplicate();}
    }
    private ResponseStatusException duplicate() {return new ResponseStatusException(HttpStatus.CONFLICT,"Số điện thoại hoặc email đã được tài khoản khác sử dụng.");}
}
