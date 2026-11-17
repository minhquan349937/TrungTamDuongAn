package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GuardianProfileService {
    public record GuardianInput(String fullName, String phone, String email, String address) {}
    public record Guardian(String id, String code, String fullName, String phone, String email, String address) {}

    private final JdbcTemplate jdbc;
    private final AccountContactService accounts;
    public GuardianProfileService(JdbcTemplate jdbc,AccountContactService accounts) {this.jdbc=jdbc;this.accounts=accounts;}
    private static final String SELECT="SELECT id,code,full_name,phone,email,address FROM guardian ";
    private Guardian map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new Guardian(rs.getString("id"),rs.getString("code"),rs.getString("full_name"),rs.getString("phone"),rs.getString("email"),rs.getString("address"));
    }
    public List<Guardian> list() {return jdbc.query(SELECT+"ORDER BY created_at DESC,code",this::map);}
    private Guardian get(String id) {
        return jdbc.query(SELECT+"WHERE id=?",this::map,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ phụ huynh."));
    }
    private void validate(GuardianInput input) {
        if(input==null || input.fullName()==null || input.fullName().isBlank() || LoginIdentifiers.phone(input.phone())==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần nhập tên và số điện thoại phụ huynh.");
        if(input.fullName().strip().length()>150 || input.address()!=null && input.address().length()>65535)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thông tin phụ huynh vượt quá độ dài cho phép.");
        LoginIdentifiers.email(input.email());
    }
    private String nextCode() {
        Long next=jdbc.queryForObject("SELECT next_value FROM profile_code_sequences WHERE entity_name='guardian' FOR UPDATE",Long.class);
        if(next==null) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Chưa khởi tạo bộ đếm mã phụ huynh.");
        Long maximum=jdbc.queryForObject("SELECT COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0) FROM guardian WHERE code REGEXP '^PH[0-9]+$'",Long.class);
        long value=Math.max(next,maximum==null?1:maximum+1);
        if(value>999999999L) throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết dải mã phụ huynh.");
        jdbc.update("UPDATE profile_code_sequences SET next_value=? WHERE entity_name='guardian'",value+1);
        return "PH"+String.format(Locale.ROOT,"%03d",value);
    }
    @Transactional
    public Guardian create(GuardianInput input) {
        validate(input);
        String phone=LoginIdentifiers.phone(input.phone()),email=LoginIdentifiers.email(input.email());
        // Reuse the same named parent for siblings, never an account belonging to a different role/person.
        var existing=jdbc.queryForList("SELECT g.id FROM guardian g JOIN app_user u ON u.id=g.user_id AND u.role='PARENT' WHERE u.phone=? FOR UPDATE",String.class,phone);
        if(!existing.isEmpty()) {
            Guardian guardian=get(existing.getFirst());
            if(!guardian.fullName().equalsIgnoreCase(input.fullName().strip()) || email!=null && !email.equals(guardian.email()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Số điện thoại đã thuộc hồ sơ phụ huynh khác. Vui lòng kiểm tra thông tin.");
            return guardian;
        }
        accounts.available(email,phone,null);
        String code=nextCode(),id=ShortIds.next(jdbc,"guardian");
        String userId=accounts.create(input.fullName(),email,phone,"PARENT");
        jdbc.update("INSERT INTO guardian(id,code,user_id,account_role,full_name,phone,email,address) VALUES(?,?,?,'PARENT',?,?,?,?)",
            id,code,userId,input.fullName().strip(),phone,email,emptyToNull(input.address()));
        return get(id);
    }

    @Transactional
    public Guardian update(String id, GuardianInput input) {
        validate(input);
        var rows=jdbc.query("SELECT user_id FROM guardian WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ phụ huynh.");
        String email=LoginIdentifiers.email(input.email()),phone=LoginIdentifiers.phone(input.phone()),userId=rows.getFirst();
        if(userId==null) userId=accounts.create(input.fullName(),email,phone,"PARENT");
        else accounts.update(userId,input.fullName(),email,phone);
        jdbc.update("UPDATE guardian SET full_name=?,phone=?,email=?,address=?,user_id=? WHERE id=?",
            input.fullName().strip(),phone,email,emptyToNull(input.address()),userId,id);
        return get(id);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
