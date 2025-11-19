package com.example.bankticketsystem.dto;

import java.util.List;
import java.util.UUID;

//public class TagDto {
//    private UUID id;
//    private String name;
//
//    public UUID getId() { return id; }
//    public void setId(UUID id) { this.id = id; }
//
//    public String getName() { return name; }
//    public void setName(String name) { this.name = name; }
//}

import jakarta.validation.constraints.NotBlank;

public class TagDto {
    private UUID id;

    @NotBlank(message = "Tag name is required")
    private String name;

    // Геттеры
    public UUID getId() { return id; }
    public String getName() { return name; }

    // Сеттер только для name (id устанавливается на стороне сервиса)
    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
}