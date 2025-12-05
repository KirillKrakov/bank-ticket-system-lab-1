package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.DocumentDto;
import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Document;
import com.example.bankticketsystem.model.entity.Tag;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TagServiceTest {

    @Mock
    private TagRepository repo;

    @InjectMocks
    private TagService tagService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tagService = new TagService(repo);
    }

    // -----------------------
    // createTag tests
    // -----------------------
    @Test
    public void createIfNotExists_createsWhenMissing() {
        when(repo.findByName("urgent")).thenReturn(Optional.empty());
        when(repo.save(any(Tag.class))).thenAnswer(inv -> {
            Tag t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        Tag t = tagService.createIfNotExists("urgent");

        assertNotNull(t);
        assertNotNull(t.getId());
        assertEquals("urgent", t.getName());
        verify(repo, times(1)).findByName("urgent");
        verify(repo, times(1)).save(any(Tag.class));
    }

    @Test
    public void createIfNotExists_returnsExisting() {
        Tag existing = new Tag();
        existing.setId(UUID.randomUUID());
        existing.setName("urgent");
        when(repo.findByName("urgent")).thenReturn(Optional.of(existing));

        Tag t = tagService.createIfNotExists("urgent");

        assertNotNull(t);
        assertEquals(existing.getId(), t.getId());
        assertEquals("urgent", t.getName());
        verify(repo, times(1)).findByName("urgent");
        verify(repo, never()).save(any());
    }

    @Test
    public void createTag_callsCreateIfNotExists() {
        Tag existing = new Tag();
        existing.setId(UUID.randomUUID());
        existing.setName("test");

        when(repo.findByName("test")).thenReturn(Optional.of(existing));

        Tag result = tagService.createTag("test");

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        verify(repo, times(1)).findByName("test");
    }

    // -----------------------
    // ReadAllTags tests
    // -----------------------
    @Test
    public void listAll_mapsToDto() {
        Tag t1 = new Tag();
        t1.setId(UUID.randomUUID());
        t1.setName("a");

        Tag t2 = new Tag();
        t2.setId(UUID.randomUUID());
        t2.setName("b");

        Page<Tag> page = new PageImpl<>(List.of(t1, t2));
        when(repo.findAll(any(PageRequest.class))).thenReturn(page);

        Page<TagDto> result = tagService.listAll(0, 20);

        assertEquals(2, result.getNumberOfElements());
        List<String> names = result.getContent().stream().map(TagDto::getName).toList();
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));

        verify(repo, times(1)).findAll(any(PageRequest.class));
    }

    // -----------------------
    // ReadTag tests
    // -----------------------
    @Test
    public void getTagWithApplications_tagNotFound_throws() {
        when(repo.findByNameWithApplications("nonexistent")).thenReturn(Optional.empty());
        NotFoundException ex = assertThrows(NotFoundException.class, () -> tagService.getTagWithApplications("nonexistent"));
        assertTrue(ex.getMessage().contains("Tag not found"));
        verify(repo, times(1)).findByNameWithApplications("nonexistent");
    }

    @Test
    public void getTagWithApplications_mapsApplicationsToDtos() {
        // Prepare Tag with two applications (each with documents and tags)
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.setName("payments");

        // Application 1
        Application a1 = new Application();
        UUID a1Id = UUID.randomUUID();
        a1.setId(a1Id);
        User applicant1 = new User();
        applicant1.setId(UUID.randomUUID());
        a1.setApplicant(applicant1);
        Product product1 = new Product();
        product1.setId(UUID.randomUUID());
        a1.setProduct(product1);
        a1.setStatus(null);
        a1.setCreatedAt(Instant.now());
        Document d1 = new Document();
        d1.setId(UUID.randomUUID());
        d1.setFileName("doc1.pdf");
        d1.setContentType("application/pdf");
        d1.setStoragePath("/tmp/doc1");
        d1.setApplication(a1);
        a1.setDocuments(List.of(d1));
        a1.setTags(new HashSet<>(List.of(tag)));

        // Application 2
        Application a2 = new Application();
        UUID a2Id = UUID.randomUUID();
        a2.setId(a2Id);
        User applicant2 = new User();
        applicant2.setId(UUID.randomUUID());
        a2.setApplicant(applicant2);
        Product product2 = new Product();
        product2.setId(UUID.randomUUID());
        a2.setProduct(product2);
        a2.setStatus(null);
        a2.setCreatedAt(Instant.now());
        Document d2 = new Document();
        d2.setId(UUID.randomUUID());
        d2.setFileName("doc2.txt");
        d2.setContentType("text/plain");
        d2.setStoragePath("/tmp/doc2");
        d2.setApplication(a2);
        a2.setDocuments(List.of(d2));
        a2.setTags(new HashSet<>(List.of(tag)));

        // attach applications to tag
        tag.setApplications(new HashSet<>(List.of(a1, a2)));

        when(repo.findByNameWithApplications("payments")).thenReturn(Optional.of(tag));

        TagDto dto = tagService.getTagWithApplications("payments");

        assertNotNull(dto);
        assertEquals("payments", dto.getName());
        // applications list should be present and contain two entries
        assertNotNull(dto.getApplications());
        assertEquals(2, dto.getApplications().size());

        // find DTO for application a1 and check its documents and tags
        Optional<ApplicationDto> found = dto.getApplications().stream()
                .filter(adto -> a1Id.equals(adto.getId()))
                .findFirst();
        assertTrue(found.isPresent());
        ApplicationDto adto = found.get();
        assertEquals(a1.getApplicant().getId(), adto.getApplicantId());
        assertEquals(a1.getProduct().getId(), adto.getProductId());
        assertNotNull(adto.getDocuments());
        assertEquals(1, adto.getDocuments().size());
        DocumentDto dd = adto.getDocuments().get(0);
        assertEquals("doc1.pdf", dd.getFileName());
        assertTrue(adto.getTags().contains("payments"));

        verify(repo, times(1)).findByNameWithApplications("payments");
    }

    // -----------------------
    // toDto tests
    // -----------------------

    @Test
    public void toApplicationDto_mapsAllFieldsCorrectly() {
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.setName("test");

        Application app = new Application();
        UUID appId = UUID.randomUUID();
        app.setId(appId);

        User applicant = new User();
        UUID applicantId = UUID.randomUUID();
        applicant.setId(applicantId);
        app.setApplicant(applicant);

        Product product = new Product();
        UUID productId = UUID.randomUUID();
        product.setId(productId);
        app.setProduct(product);

        app.setStatus(null);
        Instant createdAt = Instant.now();
        app.setCreatedAt(createdAt);

        Document doc = new Document();
        UUID docId = UUID.randomUUID();
        doc.setId(docId);
        doc.setFileName("test.pdf");
        doc.setContentType("application/pdf");
        doc.setStoragePath("/tmp/test");
        doc.setApplication(app);
        app.setDocuments(List.of(doc));

        app.setTags(new HashSet<>(List.of(tag)));

        // Since toApplicationDto is private, we need to test it indirectly
        // by calling getTagWithApplications which uses it
        tag.setApplications(new HashSet<>(List.of(app)));

        when(repo.findByNameWithApplications("test")).thenReturn(Optional.of(tag));

        TagDto tagDto = tagService.getTagWithApplications("test");

        assertNotNull(tagDto);
        assertNotNull(tagDto.getApplications());
        assertEquals(1, tagDto.getApplications().size());

        ApplicationDto appDto = tagDto.getApplications().get(0);
        assertEquals(appId, appDto.getId());
        assertEquals(applicantId, appDto.getApplicantId());
        assertEquals(productId, appDto.getProductId());
        assertEquals(createdAt, appDto.getCreatedAt());
        assertNotNull(appDto.getDocuments());
        assertEquals(1, appDto.getDocuments().size());
        assertEquals("test.pdf", appDto.getDocuments().get(0).getFileName());
        assertTrue(appDto.getTags().contains("test"));
    }
}