package com.example.TTGDHNDuongAn.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LoginIdentifiersTests {
    @ParameterizedTest @ValueSource(strings={"0912345678","+84 912 345 678","84 912 345 678","(0912) 345-678","0912.345.678"})
    void normalizesVietnamPhoneAliases(String input) {assertEquals("0912345678",LoginIdentifiers.phone(input));}
    @ParameterizedTest @ValueSource(strings={"123","phone@example.com","090abc5678","+84912345678 or 1=1","000000000000000000000000000000000000000000000000000"})
    void rejectsInvalidPhones(String input) {assertThrows(ResponseStatusException.class,()->LoginIdentifiers.phone(input));}
    @Test void distinguishesEmailFromPhoneAndKeepsOptionalContactsOptional() {
        assertEquals("parent@example.com",LoginIdentifiers.login(" Parent@Example.COM "));
        assertEquals("0912345678",LoginIdentifiers.login("+84 912 345 678"));
        assertNull(LoginIdentifiers.email(" "));assertNull(LoginIdentifiers.phone(null));
    }
}
