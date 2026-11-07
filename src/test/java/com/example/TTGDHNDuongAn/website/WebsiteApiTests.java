package com.example.TTGDHNDuongAn.website;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import com.example.TTGDHNDuongAn.service.ConsultationService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.datasource.url=${WEBSITE_TEST_DB_URL}", "SETUP_ADMIN_EMAIL=", "SETUP_ADMIN_PASSWORD=",
        "spring.docker.compose.enabled=false", "website.upload-dir=target/website-test-uploads", "logging.level.root=WARN", "debug=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "WEBSITE_TEST_DB_URL", matches = ".+")
@Transactional
class WebsiteApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private AppUserPrincipal admin, teacher, parent;
    private int ipCounter;

    private AppUserPrincipal account(String role) {
        String id = UUID.randomUUID().toString(), email = id + "@test.invalid";
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(?,?,'unused',?,?)", id, email, "Tên " + role, role);
        return new AppUserPrincipal(id, email, "Tên " + role, role, "unused");
    }

    @BeforeEach void setup() {
        admin = account("ADMIN");
        teacher = account("TEACHER");
        parent = account("PARENT");
        // Everything runs in one rolled-back transaction, so clearing makes the tests independent of real content.
        for (String table : new String[] {"website_publications", "website_media", "website_albums", "website_team_members", "website_services"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE website_sections SET is_visible=1");
    }

    private ResultActions send(String method, String url, String body) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> delete(url);
        };
        return mvc.perform(builder.with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String createService(String title, boolean visible) throws Exception {
        return json(send("POST", "/api/admin/website/services", "{\"title\":\"" + title + "\",\"visible\":" + visible + "}")
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode publicSite() throws Exception {
        return json(mvc.perform(get("/api/public/website")).andExpect(status().isOk()));
    }

    private void publish() throws Exception {
        mvc.perform(post("/api/admin/website/publish").with(user(admin)).with(csrf())).andExpect(status().isOk());
    }

    private byte[] png(int size) {
        byte[] data = new byte[size];
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    private JsonNode upload(byte[] bytes, String name, String albumId, boolean consent) throws Exception {
        var request = multipart("/api/admin/website/media").file(new MockMultipartFile("file", name, "image/png", bytes))
                .param("publicConsent", String.valueOf(consent)).with(user(admin)).with(csrf());
        if (albumId != null) request.param("albumId", albumId);
        return json(mvc.perform(request).andExpect(status().isCreated()));
    }

    private String createAlbum(String title) throws Exception {
        return json(send("POST", "/api/admin/website/albums", "{\"title\":\"" + title + "\",\"category\":\"SPACE\",\"visible\":true}")
                .andExpect(status().isCreated())).get("id").asText();
    }

    @Test void adminApiRejectsAnonymousTeacherAndParent() throws Exception {
        for (String url : new String[] {"/api/admin/website/status", "/api/admin/website/preview", "/api/admin/website/consultations", "/api/admin/website/services"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).with(user(teacher))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user(parent))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/admin/website/publish").with(user(teacher)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/website/services").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()); // admin writes still need a CSRF token
    }

    @Test void publicSiteIsNotFoundUntilPublishedAndNeverExposesConsultations() throws Exception {
        mvc.perform(get("/api/public/website")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/consultations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/consultations/anything")).andExpect(status().isUnauthorized());
    }

    @Test void draftEditsDoNotChangeThePublishedSiteUntilPublished() throws Exception {
        String id = createService("Dịch vụ A", true);
        publish();
        assertEquals("Dịch vụ A", publicSite().get("services").get(0).get("title").asText());
        send("PUT", "/api/admin/website/services/" + id, "{\"title\":\"Dịch vụ B\",\"visible\":true}").andExpect(status().isOk());
        assertEquals("Dịch vụ A", publicSite().get("services").get(0).get("title").asText(), "draft must not leak");
        var preview = json(mvc.perform(get("/api/admin/website/preview").with(user(admin))).andExpect(status().isOk()));
        assertEquals("Dịch vụ B", preview.get("services").get(0).get("title").asText());
        assertTrue(json(mvc.perform(get("/api/admin/website/status").with(user(admin)))).get("hasChanges").asBoolean());
        publish();
        assertEquals("Dịch vụ B", publicSite().get("services").get(0).get("title").asText());
        assertFalse(json(mvc.perform(get("/api/admin/website/status").with(user(admin)))).get("hasChanges").asBoolean());
    }

    @Test void hiddenItemsAndSectionsAreNotPublished() throws Exception {
        String hidden = createService("Ẩn", false);
        createService("Hiện", true);
        send("PUT", "/api/admin/website/faqs/" + json(mvc.perform(get("/api/admin/website/faqs").with(user(admin)))).get(0).get("id").asText(),
                "{\"question\":\"Q\",\"answer\":\"A\",\"visible\":true}").andExpect(status().isOk());
        send("PUT", "/api/admin/website/sections/FAQ", "{\"title\":\"FAQ\",\"visible\":false}").andExpect(status().isOk());
        publish();
        var site = publicSite();
        assertEquals(1, site.get("services").size());
        assertEquals("Hiện", site.get("services").get(0).get("title").asText());
        assertEquals(0, site.get("faqs").size(), "FAQs of a hidden section are not published");
        assertFalse(site.get("sections").has("FAQ"));
        assertNotNull(hidden);
    }

    @Test void teamMemberNeedsConsentAndOnlyWhitelistedFieldsArePublic() throws Exception {
        send("POST", "/api/admin/website/team", "{\"fullName\":\"Người A\",\"visible\":true,\"publicConsent\":false}").andExpect(status().isCreated());
        send("POST", "/api/admin/website/team", "{\"fullName\":\"Người B\",\"jobTitle\":\"Chuyên viên\",\"visible\":true,\"publicConsent\":true}").andExpect(status().isCreated());
        publish();
        var team = publicSite().get("team");
        assertEquals(1, team.size());
        assertEquals("Người B", team.get(0).get("fullName").asText());
        assertFalse(team.get(0).has("id"));
        assertFalse(team.get(0).has("sample"));
        assertFalse(publicSite().toString().contains("updated"), "no internal columns in the public payload");
    }

    @Test void mediaWithoutConsentIsNeverPublishedAndUnpublishedFilesAreNotServed() throws Exception {
        String album = createAlbum("Góc học");
        JsonNode blocked = upload(png(64), "a.png", album, false);
        publish();
        assertEquals(0, publicSite().get("albums").size(), "album without consented media is not shown");
        mvc.perform(get("/api/public/website/media/" + blocked.get("id").asText())).andExpect(status().isNotFound());

        send("PUT", "/api/admin/website/media/" + blocked.get("id").asText(), "{\"publicConsent\":true,\"title\":\"Chú thích\"}").andExpect(status().isOk());
        mvc.perform(get("/api/public/website/media/" + blocked.get("id").asText())).andExpect(status().isNotFound()); // consented but not yet published
        publish();
        var media = publicSite().get("albums").get(0).get("media").get(0);
        assertEquals("image", media.get("type").asText());
        assertEquals("Chú thích", media.get("title").asText());
        mvc.perform(get(media.get("url").asText())).andExpect(status().isOk()).andExpect(r -> assertEquals("image/png", r.getResponse().getContentType()));

        JsonNode late = upload(png(64), "late.png", album, true); // consented, uploaded after publishing
        mvc.perform(get("/api/public/website/media/" + late.get("id").asText())).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/website/media/" + late.get("id").asText() + "/file").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/website/media/" + late.get("id").asText() + "/file")).andExpect(status().isUnauthorized());

        send("PUT", "/api/admin/website/media/" + blocked.get("id").asText(), "{\"publicConsent\":false}").andExpect(status().isOk());
        mvc.perform(get(media.get("url").asText())).andExpect(status().isNotFound()); // revoking consent takes effect immediately
    }

    @Test void uploadChecksRealFileTypeAndSize() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "x.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/admin/website/media").file(fake).with(user(admin)).with(csrf())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/admin/website/media").file(new MockMultipartFile("file", "big.png", "image/png", png(5 * 1024 * 1024 + 1)))
                .with(user(admin)).with(csrf())).andExpect(status().isPayloadTooLarge());
        byte[] mp4 = new byte[32];
        System.arraycopy("ftypisom".getBytes(StandardCharsets.US_ASCII), 0, mp4, 4, 8);
        mvc.perform(multipart("/api/admin/website/media").file(new MockMultipartFile("file", "v.mp4", "video/mp4", mp4)).with(user(admin)).with(csrf()))
                .andExpect(status().isBadRequest()); // standalone video is not allowed
        JsonNode stored = upload(png(32), "../../evil\\name.png", null, false);
        assertEquals("name.png", stored.get("originalName").asText());
    }

    @Test void settingsRejectUnsafeLinks() throws Exception {
        String ok = "{\"centerName\":\"Trung tâm\",\"hotline\":\"0912 345 678\",\"zaloUrl\":\"https://zalo.me/123\",\"facebookUrl\":\"https://www.facebook.com/x\"," +
                "\"mapEmbedUrl\":\"https://www.google.com/maps/embed?pb=1\"}";
        send("PUT", "/api/admin/website/settings/WCFG001", ok).andExpect(status().isOk());
        for (String bad : new String[] {"\"zaloUrl\":\"javascript:alert(1)\"", "\"facebookUrl\":\"https://evil.example/facebook.com\"",
                "\"mapEmbedUrl\":\"https://evil.example/maps/embed\"", "\"mapEmbedUrl\":\"http://www.google.com/maps/embed\"", "\"hotline\":\"<b>1</b>\""})
            send("PUT", "/api/admin/website/settings/WCFG001", "{\"centerName\":\"Trung tâm\"," + bad + "}").andExpect(status().isBadRequest());
        send("PUT", "/api/admin/website/settings/WCFG001", "{\"centerName\":\"  \"}").andExpect(status().isBadRequest());
        send("POST", "/api/admin/website/settings", ok).andExpect(status().isMethodNotAllowed());
        send("DELETE", "/api/admin/website/sections/HERO", null).andExpect(status().isMethodNotAllowed());
    }

    @Test void reorderingChangesPublishedOrder() throws Exception {
        String a = createService("A", true), b = createService("B", true);
        send("PUT", "/api/admin/website/services/order", "{\"ids\":[\"" + b + "\",\"" + a + "\"]}").andExpect(status().isNoContent());
        publish();
        assertEquals("B", publicSite().get("services").get(0).get("title").asText());
        send("PUT", "/api/admin/website/services/order", "{\"ids\":[\"nope\"]}").andExpect(status().isNotFound());
    }

    @Test void deletingRecordsAndUnknownResourcesAreHandled() throws Exception {
        String id = createService("Xóa", true);
        send("DELETE", "/api/admin/website/services/" + id, null).andExpect(status().isNoContent());
        send("DELETE", "/api/admin/website/services/" + id, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/website/unknown").with(user(admin))).andExpect(status().isNotFound());
        send("POST", "/api/admin/website/services", "{\"title\":\"" + "x".repeat(201) + "\"}").andExpect(status().isBadRequest());
        send("POST", "/api/admin/website/services", "not json").andExpect(status().isBadRequest());
    }

    @Test void phoneNumbersAreNormalised() {
        assertEquals("0912345678", ConsultationService.normalizePhone("0912 345 678"));
        assertEquals("0912345678", ConsultationService.normalizePhone("+84 912.345.678"));
        assertEquals("0912345678", ConsultationService.normalizePhone("84912345678"));
        assertEquals("0287654321", ConsultationService.normalizePhone("(028) 7654 321"));
        for (String bad : new String[] {null, "", "12345", "0112345678", "09123456789", "abc", "+1 912345678"})
            assertNull(ConsultationService.normalizePhone(bad), String.valueOf(bad));
    }

    private ResultActions consult(String body) throws Exception {
        return mvc.perform(post("/api/public/consultations").header("X-Forwarded-For", "203.0.113." + (++ipCounter % 250) + "." + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void consultationFormValidatesStoresAndIsManagedByAdmin() throws Exception {
        String name = "Phụ huynh " + UUID.randomUUID();
        consult("{\"parentName\":\"" + name + "\",\"phone\":\"+84 912 345 678\",\"childAge\":\"3 tuổi\",\"message\":\"Cần tư vấn\",\"consent\":true}")
                .andExpect(status().isCreated());
        assertEquals("0912345678", jdbc.queryForObject("SELECT phone FROM consultation_requests WHERE parent_name=?", String.class, name));
        consult("{\"parentName\":\"A\",\"phone\":\"0912345679\",\"consent\":true}").andExpect(status().isBadRequest());
        consult("{\"parentName\":\"Hợp lệ\",\"phone\":\"123\",\"consent\":true}").andExpect(status().isBadRequest());
        consult("{\"parentName\":\"Hợp lệ\",\"phone\":\"0912345679\",\"consent\":false}").andExpect(status().isBadRequest());
        consult("{\"parentName\":\"Hợp lệ\",\"phone\":\"0912345679\"}").andExpect(status().isBadRequest());
        consult("{\"parentName\":\"Bot\",\"phone\":\"0912345679\",\"consent\":true,\"website\":\"http://spam\"}").andExpect(status().isBadRequest());
        consult("{\"parentName\":\"Hợp lệ\",\"phone\":\"0912345679\",\"consent\":true,\"message\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM consultation_requests WHERE phone='0912345679'", Integer.class), "rejected requests are not stored");

        var page = json(mvc.perform(get("/api/admin/website/consultations").param("q", name).with(user(admin))).andExpect(status().isOk()));
        assertEquals(1, page.get("total").asInt());
        String id = page.get("items").get(0).get("id").asText();
        assertEquals("NEW", page.get("items").get(0).get("status").asText());
        assertEquals(1, json(mvc.perform(get("/api/admin/website/consultations").param("q", "912 345 678").param("status", "NEW").with(user(admin)))).get("total").asInt());
        assertEquals(0, json(mvc.perform(get("/api/admin/website/consultations").param("q", name).param("status", "CLOSED").with(user(admin)))).get("total").asInt());
        assertEquals(0, json(mvc.perform(get("/api/admin/website/consultations").param("q", name).param("from", "2020-01-01").param("to", "2020-01-02").with(user(admin)))).get("total").asInt());
        assertEquals(1, json(mvc.perform(get("/api/admin/website/consultations").param("q", name).param("from", "2020-01-01").with(user(admin)))).get("total").asInt());
        mvc.perform(get("/api/admin/website/consultations").param("status", "BOGUS").with(user(admin))).andExpect(status().isBadRequest());

        send("PUT", "/api/admin/website/consultations/" + id, "{\"status\":\"CONTACTING\",\"assignedUserId\":\"" + teacher.id() + "\",\"internalNote\":\"Gọi lại chiều\"}")
                .andExpect(status().isOk());
        var detail = json(mvc.perform(get("/api/admin/website/consultations/" + id).with(user(admin))).andExpect(status().isOk()));
        assertEquals("CONTACTING", detail.get("status").asText());
        assertEquals("Tên TEACHER", detail.get("assignedName").asText());
        assertEquals("Tên ADMIN", detail.get("updatedByName").asText());
        assertEquals("Gọi lại chiều", detail.get("internalNote").asText());
        send("PUT", "/api/admin/website/consultations/" + id, "{\"status\":\"CLOSED\",\"assignedUserId\":\"" + parent.id() + "\"}").andExpect(status().isBadRequest());
        send("PUT", "/api/admin/website/consultations/" + id, "{\"status\":\"WHATEVER\"}").andExpect(status().isBadRequest());
        var assignees = json(mvc.perform(get("/api/admin/website/consultations/assignees").with(user(admin))));
        assertTrue(Arrays.stream(assignees.toString().split("\"id\"")).anyMatch(s -> s.contains(teacher.id())));
        assertFalse(assignees.toString().contains(parent.id()));
    }

    @Test void samePhoneIsLimitedPerDay() throws Exception {
        String phone = "0987" + String.format("%06d", (int) (Math.random() * 1_000_000));
        for (int i = 0; i < 3; i++)
            consult("{\"parentName\":\"Phụ huynh\",\"phone\":\"" + phone + "\",\"consent\":true}").andExpect(status().isCreated());
        consult("{\"parentName\":\"Phụ huynh\",\"phone\":\"" + phone + "\",\"consent\":true}").andExpect(status().isTooManyRequests());
    }

    @Test void sameAddressIsLimitedInShortWindow() throws Exception {
        String ip = "198.51.100." + (int) (Math.random() * 200) + "." + System.nanoTime();
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/api/public/consultations").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"parentName\":\"Phụ huynh\",\"phone\":\"09" + String.format("%08d", (int) (Math.random() * 100_000_000)) + "\",\"consent\":true}"))
                    .andExpect(status().isCreated());
        mvc.perform(post("/api/public/consultations").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentName\":\"Phụ huynh\",\"phone\":\"0911111111\",\"consent\":true}")).andExpect(status().isTooManyRequests());
    }
}
