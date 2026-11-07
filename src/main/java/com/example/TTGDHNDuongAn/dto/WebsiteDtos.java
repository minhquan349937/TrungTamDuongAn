package com.example.TTGDHNDuongAn.dto;

import java.util.List;
import java.util.Map;

/** Public records list exactly the fields a visitor may see; they are never built from internal entities. */
public final class WebsiteDtos {
    private WebsiteDtos() {}

    public record Content(Settings settings, Map<String, Section> sections, List<Item> items, List<ServiceItem> services,
                          List<Member> team, List<Album> albums, List<Faq> faqs) {}
    public record Settings(String centerName, String logoUrl, String address, String hotline, String workingHours,
                           String zaloUrl, String facebookUrl, String mapEmbedUrl) {}
    public record Section(String title, String summary, String body, String imageUrl, String buttonLabel) {}
    public record Item(String sectionKey, String label, String title, String description) {}
    public record ServiceItem(String id, String title, String summary, String details, String audience, String format, String imageUrl) {}
    public record Member(String fullName, String jobTitle, String qualifications, String experience, String bio, String photoUrl) {}
    public record Album(String id, String title, String category, String description, List<Media> media) {}
    public record Media(String type, String url, String title, String altText) {}
    public record Faq(String question, String answer) {}

    public record ConsultationInput(String parentName, String phone, String childAge, String message, Boolean consent, String website) {}
    public record ConsultationUpdate(String status, String assignedUserId, String internalNote) {}
    public record Consultation(String id, String parentName, String phone, String childAge, String message, String status,
                               String assignedUserId, String assignedName, String internalNote, String updatedByName,
                               String consentedAt, String createdAt, String updatedAt) {}
    public record Assignee(String id, String name, String role) {}
    public record PageOf<T>(List<T> items, long total, int page, int size) {}

    public record Order(List<String> ids) {}
    public record MediaMeta(String title, String altText, Boolean visible, Boolean publicConsent) {}
    public record AdminMedia(String id, String kind, String contentType, long sizeBytes, String originalName, String albumId,
                             String title, String altText, int sortOrder, boolean visible, boolean publicConsent, String createdAt) {}
    public record Status(boolean published, String publishedAt, String publishedBy, boolean hasChanges, int sampleCount) {}
}
