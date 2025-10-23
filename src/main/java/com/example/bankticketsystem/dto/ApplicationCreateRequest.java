package com.example.bankticketsystem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class ApplicationCreateRequest {
    @NotNull
    private UUID applicantId;

    @NotNull
    private UUID productId;

    @Size(max = 1000)
    private String comment;

    // optional documents metadata
    private List<DocumentCreateRequest> documents;

    // getters/setters
    public UUID getApplicantId() { return applicantId; }
    public void setApplicantId(UUID applicantId) { this.applicantId = applicantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public List<DocumentCreateRequest> getDocuments() { return documents; }
    public void setDocuments(List<DocumentCreateRequest> documents) { this.documents = documents; }
}
