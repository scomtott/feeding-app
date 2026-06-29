package com.example.springboot.config;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class JournalResourceConfig implements WebMvcConfigurer {

    private final Path imageRoot;

    public JournalResourceConfig(@Value("${journal.storage.image-root:./data/journal/images}") String imageRoot) {
        this.imageRoot = Paths.get(imageRoot).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.imageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create journal image directory", ex);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resourceLocation = this.imageRoot.toUri().toString();
        registry.addResourceHandler("/journal-media/**")
            .addResourceLocations(resourceLocation);
        log.info("Configured journal media resource location: {}", resourceLocation);
    }
}
