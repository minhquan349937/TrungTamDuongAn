package com.example.TTGDHNDuongAn.controller;

import com.example.TTGDHNDuongAn.service.AccountManagementService;
import com.example.TTGDHNDuongAn.service.AccountManagementService.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountManagementController {
    private final AccountManagementService service;
    public AccountManagementController(AccountManagementService service) {this.service=service;}
    @GetMapping public List<Account> list() {return service.list();}
    @PutMapping("/{id}") public Account updateAccount(@PathVariable String id,@RequestBody Update input) {return service.updateAccount(id,input);}
    @PutMapping("/{id}/contact") public Account update(@PathVariable String id,@RequestBody Contact input) {return service.update(id,input);}
}
