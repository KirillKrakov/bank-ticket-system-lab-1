//package com.example.bankticketsystem.service;
//
//import com.example.bankticketsystem.model.entity.Tag;
//import com.example.bankticketsystem.repository.TagRepository;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.any;
//
//public class TagServiceTest {
//
//    @Test
//    void createIfNotExists_createsWhenMissing() {
//        TagRepository repo = Mockito.mock(TagRepository.class);
//        Mockito.when(repo.findByName("urgent")).thenReturn(Optional.empty());
//        Mockito.when(repo.save(any(Tag.class))).thenAnswer(inv -> {
//            Tag t = inv.getArgument(0);
//            t.setId(UUID.randomUUID());
//            return t;
//        });
//
//        TagService svc = new TagService(repo);
//        Tag t = svc.createIfNotExists("urgent");
//        assertNotNull(t.getId());
//        assertEquals("urgent", t.getName());
//    }
//
//    @Test
//    void createIfNotExists_returnsExisting() {
//        TagRepository repo = Mockito.mock(TagRepository.class);
//        Tag existing = new Tag();
//        existing.setId(UUID.randomUUID());
//        existing.setName("urgent");
//        Mockito.when(repo.findByName("urgent")).thenReturn(Optional.of(existing));
//
//        TagService svc = new TagService(repo);
//        Tag t = svc.createIfNotExists("urgent");
//        assertEquals(existing.getId(), t.getId());
//    }
//}
