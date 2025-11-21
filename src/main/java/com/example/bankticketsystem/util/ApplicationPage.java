package com.example.bankticketsystem.util;

import com.example.bankticketsystem.dto.ApplicationDto;

import java.util.List;

public record ApplicationPage(List<ApplicationDto> items, String nextCursor) { }
