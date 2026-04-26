package com.example.springboot.models;

import java.time.LocalDate;

public record LengthCentile(LocalDate date, double lengthInCentimetres, double centileValue) {
}