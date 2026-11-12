package com.example.TTGDHNDuongAn.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiSummaryQuota {
    private final int perUser, global;
    private final Clock clock;
    private long hour=-1;
    private int total;
    private final Map<String,Integer> calls=new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    public AiSummaryQuota(@Value("${gemini.requests-per-user-hour:10}") int perUser,
                          @Value("${gemini.requests-per-hour:100}") int global) {
        this(perUser,global,Clock.systemUTC());
    }
    AiSummaryQuota(int perUser,int global,Clock clock) { this.perUser=perUser;this.global=global;this.clock=clock; }
    public synchronized void acquire(String user) {
        long current=clock.millis()/3600000;
        if(current!=hour) { hour=current;total=0;calls.clear(); }
        int count=calls.getOrDefault(user,0);
        if(total>=global || count>=perUser)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đã hết lượt AI trong giờ này. Vui lòng thử lại sau.");
        total++;calls.put(user,count+1);
    }
}
