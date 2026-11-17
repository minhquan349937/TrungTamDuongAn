package com.example.TTGDHNDuongAn.service;

import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountManagementService {
    public record Account(String id,String displayName,String email,String phone,String role,String status,List<String> allowedRoles) {}
    public record Contact(String email,String phone) {}
    public record Update(String displayName,String email,String phone,String role,String status,String password) {}
    private final JdbcTemplate jdbc;
    private final AccountContactService contacts;
    private final PasswordEncoder encoder;
    public AccountManagementService(JdbcTemplate jdbc,AccountContactService contacts,PasswordEncoder encoder) {this.jdbc=jdbc;this.contacts=contacts;this.encoder=encoder;}
    private static final String SELECT="SELECT id,display_name,email,phone,role,status,"+
        "EXISTS(SELECT 1 FROM staff s WHERE s.user_id=app_user.id) AS has_staff,"+
        "EXISTS(SELECT 1 FROM guardian g WHERE g.user_id=app_user.id) AS has_guardian FROM app_user ";
    private Account map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        var allowed=rs.getBoolean("has_guardian") ? List.of("PARENT") : rs.getBoolean("has_staff") ? List.of("ADMIN","TEACHER") : List.of("ADMIN","TEACHER","PARENT");
        return new Account(rs.getString("id"),rs.getString("display_name"),rs.getString("email"),rs.getString("phone"),rs.getString("role"),rs.getString("status"),allowed);
    }
    public List<Account> list() {return jdbc.query(SELECT+"ORDER BY created_at DESC,id",this::map);}
    @Transactional
    public Account update(String id,Contact input) {
        if(input==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiếu thông tin liên hệ.");
        // Lock profiles before accounts, consistent with staff/guardian edit flows.
        jdbc.queryForList("SELECT id FROM staff WHERE user_id=? FOR UPDATE",String.class,id);
        jdbc.queryForList("SELECT id FROM guardian WHERE user_id=? FOR UPDATE",String.class,id);
        var rows=jdbc.query(SELECT+"WHERE id=? FOR UPDATE",this::map,id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản.");
        String email=LoginIdentifiers.email(input.email()),phone=LoginIdentifiers.phone(input.phone());
        if(rows.getFirst().role().equals("PARENT") && phone==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tài khoản phụ huynh cần số điện thoại đăng nhập.");
        contacts.update(id,rows.getFirst().displayName(),email,phone);
        jdbc.update("UPDATE staff SET email=?,phone=? WHERE user_id=?",email,phone,id);
        jdbc.update("UPDATE guardian SET email=?,phone=? WHERE user_id=?",email,phone,id);
        return jdbc.query(SELECT+"WHERE id=?",this::map,id).getFirst();
    }

    @Transactional
    public Account updateAccount(String id,Update input) {
        if(input==null || input.displayName()==null || input.displayName().isBlank() || input.displayName().strip().length()>150)
            throw badRequest("Họ tên phải có từ 1 đến 150 ký tự.");
        if(input.role()==null || !List.of("ADMIN","TEACHER","PARENT").contains(input.role()))
            throw badRequest("Vai trò tài khoản không hợp lệ.");
        if(input.status()==null || !List.of("ACTIVE","LOCKED","DISABLED").contains(input.status()))
            throw badRequest("Trạng thái tài khoản không hợp lệ.");
        String password=input.password();
        if(password!=null && !password.isEmpty() && (password.isBlank() || password.length()<6 || password.getBytes(StandardCharsets.UTF_8).length>72))
            throw badRequest("Mật khẩu phải có ít nhất 6 ký tự và không vượt quá 72 byte.");
        // Lock profiles before the account, matching the other profile edit flows.
        var staffIds=jdbc.queryForList("SELECT id FROM staff WHERE user_id=? FOR UPDATE",String.class,id);
        jdbc.queryForList("SELECT id FROM guardian WHERE user_id=? FOR UPDATE",String.class,id);
        var rows=jdbc.query(SELECT+"WHERE id=? FOR UPDATE",this::map,id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản.");
        Account current=rows.getFirst();
        if(!current.allowedRoles().contains(input.role()))
            throw badRequest("Vai trò phải phù hợp với hồ sơ nhân viên hoặc phụ huynh đã liên kết.");
        String email=LoginIdentifiers.email(input.email()),phone=LoginIdentifiers.phone(input.phone());
        if(input.role().equals("PARENT") && phone==null) throw badRequest("Tài khoản phụ huynh cần số điện thoại đăng nhập.");
        contacts.update(id,input.displayName(),email,phone);
        if(!current.role().equals(input.role())) {
            // Temporarily release the composite FK; restore every profile within this transaction.
            for(String staffId:staffIds) jdbc.update("UPDATE staff SET user_id=NULL WHERE id=?",staffId);
            jdbc.update("UPDATE app_user SET role=? WHERE id=?",input.role(),id);
            for(String staffId:staffIds) jdbc.update("UPDATE staff SET user_id=?,account_role=?,position=? WHERE id=?",
                id,input.role(),input.role().equals("ADMIN")?"Quản trị viên":"Giáo viên",staffId);
        }
        jdbc.update("UPDATE app_user SET status=? WHERE id=?",input.status(),id);
        if(password!=null && !password.isEmpty()) jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",encoder.encode(password),id);
        jdbc.update("UPDATE staff SET full_name=?,email=?,phone=? WHERE user_id=?",input.displayName().strip(),email,phone,id);
        jdbc.update("UPDATE guardian SET full_name=?,email=?,phone=? WHERE user_id=?",input.displayName().strip(),email,phone,id);
        return jdbc.query(SELECT+"WHERE id=?",this::map,id).getFirst();
    }
    private ResponseStatusException badRequest(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
