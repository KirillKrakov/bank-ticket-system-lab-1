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

        when(repo.findAll()).thenReturn(List.of(t1, t2));

        List<TagDto> list = tagService.listAll();

        assertEquals(2, list.size());
        List<String> names = Arrays.asList(list.get(0).getName(), list.get(1).getName());
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));

        verify(repo, times(1)).findAll();
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
}
