package com.example.springboot.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springboot.models.JournalBackupHealthStatus;
import com.example.springboot.models.JournalBackupStatus;
import com.example.springboot.models.JournalBackupBootstrapPollResponse;
import com.example.springboot.models.JournalBackupBootstrapStartRequest;
import com.example.springboot.models.JournalBackupBootstrapStartResponse;
import com.example.springboot.models.OneDriveBackupCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JournalOneDriveBackupService {

    private static final TypeReference<Map<String, String>> CHECKSUM_TYPE = new TypeReference<>() {
    };
    private static final String DEFAULT_TENANT = "consumers";
    private static final String AUTH_SCOPE = "offline_access Files.ReadWrite";
    private static final Duration BOOTSTRAP_TTL = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Path markdownRoot;
    private final Path imageRoot;
    private final Path credentialsFile;
    private final Path stateFile;
    private final boolean enabled;

    private final AtomicReference<JournalBackupStatus> statusRef = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, PendingDeviceBootstrap> pendingBootstraps = new ConcurrentHashMap<>();

    public JournalOneDriveBackupService(
        @Value("${journal.storage.markdown-root:./data/journal/entries}") String markdownRoot,
        @Value("${journal.storage.image-root:./data/journal/images}") String imageRoot,
        @Value("${journal.backup.onedrive.enabled:false}") boolean enabled,
        @Value("${journal.backup.onedrive.credentials-file:./data/journal/onedrive/credentials.json}") String credentialsFile,
        @Value("${journal.backup.onedrive.state-file:./data/journal/onedrive/backup-state.json}") String stateFile
    ) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.markdownRoot = Paths.get(markdownRoot).toAbsolutePath().normalize();
        this.imageRoot = Paths.get(imageRoot).toAbsolutePath().normalize();
        this.enabled = enabled;
        this.credentialsFile = Paths.get(credentialsFile).toAbsolutePath().normalize();
        this.stateFile = Paths.get(stateFile).toAbsolutePath().normalize();
        initializeStatus();
    }

    private void initializeStatus() {
        try {
            Files.createDirectories(stateFile.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize OneDrive backup state directory", ex);
        }

        if (!enabled) {
            statusRef.set(new JournalBackupStatus("DISABLED", null, null, false, 0, 0, "OneDrive backup is disabled"));
            return;
        }

        statusRef.set(new JournalBackupStatus("IDLE", null, null, false, 0, 0, "Waiting for scheduled run"));
    }

    public JournalBackupStatus getStatus() {
        return statusRef.get();
    }

    public JournalBackupHealthStatus checkConfigurationHealth() {
        Instant checkedAt = Instant.now();

        if (!enabled) {
            return new JournalBackupHealthStatus(false, checkedAt, false, false, false, null, "OneDrive backup is disabled");
        }

        OneDriveBackupCredentials credentials;
        try {
            credentials = loadCredentials();
        } catch (Exception ex) {
            return new JournalBackupHealthStatus(true, checkedAt, false, false, false, null, ex.getMessage());
        }

        String accessToken;
        try {
            accessToken = requestAccessTokenFromRefreshToken(credentials);
        } catch (Exception ex) {
            return new JournalBackupHealthStatus(true, checkedAt, true, false, false, null, ex.getMessage());
        }

        DriveCheckResult driveCheck = checkDriveReachability(accessToken);
        return new JournalBackupHealthStatus(
            true,
            checkedAt,
            true,
            true,
            driveCheck.reachable(),
            driveCheck.driveName(),
            driveCheck.message()
        );
    }

    public JournalBackupBootstrapStartResponse startDeviceCodeBootstrap(JournalBackupBootstrapStartRequest request) {
        String clientId = request.clientId();
        if (isBlank(clientId)) {
            throw new IllegalArgumentException("clientId is required");
        }

        String tenantId = isBlank(request.tenantId()) ? DEFAULT_TENANT : request.tenantId().trim();
        String remoteRootFolder = normalizeRemoteRootFolder(request.remoteRootFolder());
        String deviceCodeUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/devicecode";

        String body = "client_id=" + urlEncode(clientId)
            + "&scope=" + urlEncode(AUTH_SCOPE);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(deviceCodeUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(buildOauthErrorMessage("Device bootstrap start failed", response));
            }

            JsonNode json = objectMapper.readTree(response.body());
            String deviceCode = getRequiredText(json, "device_code");
            String userCode = getRequiredText(json, "user_code");
            String verificationUri = getRequiredText(json, "verification_uri");
            String verificationUriComplete = json.hasNonNull("verification_uri_complete")
                ? json.get("verification_uri_complete").asText()
                : verificationUri;
            String message = getRequiredText(json, "message");
            int expiresIn = json.hasNonNull("expires_in") ? json.get("expires_in").asInt(900) : 900;
            int interval = json.hasNonNull("interval") ? json.get("interval").asInt(5) : 5;

            String requestId = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);
            pendingBootstraps.put(requestId, new PendingDeviceBootstrap(
                requestId,
                tenantId,
                clientId,
                remoteRootFolder,
                deviceCode,
                expiresAt,
                interval,
                Instant.now()
            ));

            return new JournalBackupBootstrapStartResponse(
                requestId,
                userCode,
                verificationUri,
                verificationUriComplete,
                message,
                expiresAt,
                interval
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Device bootstrap start interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Device bootstrap start failed", ex);
        }
    }

    public JournalBackupBootstrapPollResponse pollDeviceCodeBootstrap(String requestId) {
        PendingDeviceBootstrap pending = pendingBootstraps.get(requestId);
        if (pending == null) {
            return new JournalBackupBootstrapPollResponse("NOT_FOUND", false, "Bootstrap request not found");
        }

        if (Instant.now().isAfter(pending.expiresAt()) || Instant.now().isAfter(pending.createdAt().plus(BOOTSTRAP_TTL))) {
            pendingBootstraps.remove(requestId);
            return new JournalBackupBootstrapPollResponse("EXPIRED", false, "Bootstrap request expired");
        }

        String tokenUrl = "https://login.microsoftonline.com/" + pending.tenantId() + "/oauth2/v2.0/token";
        String body = "grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:device_code")
            + "&client_id=" + urlEncode(pending.clientId())
            + "&device_code=" + urlEncode(pending.deviceCode());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());

            if (response.statusCode() / 100 == 2) {
                String refreshToken = getRequiredText(json, "refresh_token");
                OneDriveBackupCredentials credentials = new OneDriveBackupCredentials(
                    pending.tenantId(),
                    pending.clientId(),
                    refreshToken,
                    pending.remoteRootFolder()
                );
                saveCredentials(credentials);
                pendingBootstraps.remove(requestId);
                return new JournalBackupBootstrapPollResponse("COMPLETED", true, "Refresh token stored successfully");
            }

            String error = json.hasNonNull("error") ? json.get("error").asText() : "unknown_error";
            String errorDescription = json.hasNonNull("error_description") ? json.get("error_description").asText() : "";
            if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                return new JournalBackupBootstrapPollResponse("PENDING", false, "Waiting for user authorization");
            }

            if ("expired_token".equals(error) || "authorization_declined".equals(error) || "bad_verification_code".equals(error)) {
                pendingBootstraps.remove(requestId);
                return new JournalBackupBootstrapPollResponse("FAILED", false, "Authorization failed: " + error);
            }

            return new JournalBackupBootstrapPollResponse("FAILED", false, "Token exchange failed: " + (errorDescription.isBlank() ? error : errorDescription));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new JournalBackupBootstrapPollResponse("FAILED", false, "Token polling interrupted");
        } catch (IOException ex) {
            return new JournalBackupBootstrapPollResponse("FAILED", false, "Token polling failed: " + ex.getMessage());
        }
    }

    public void triggerBackupAsync() {
        CompletableFuture.runAsync(this::runScheduledBackup);
    }

    public void runScheduledBackup() {
        if (!enabled) {
            statusRef.set(new JournalBackupStatus("DISABLED", null, null, false, 0, 0, "OneDrive backup is disabled"));
            return;
        }

        if (!running.compareAndSet(false, true)) {
            return;
        }

        Instant now = Instant.now();
        JournalBackupStatus previous = statusRef.get();
        statusRef.set(new JournalBackupStatus(
            "RUNNING",
            now,
            previous != null ? previous.lastSuccessAt() : null,
            true,
            0,
            0,
            "Backup in progress"
        ));

        try {
            OneDriveBackupCredentials credentials = loadCredentials();
            String accessToken = requestAccessTokenFromRefreshToken(credentials);

            Map<String, String> checksums = loadState();
            List<Path> files = collectJournalFiles();

            int scanned = 0;
            int uploaded = 0;
            for (Path file : files) {
                scanned++;
                String relativePath = mapRelativeBackupPath(file);
                String checksum = computeSha256(file);

                if (Objects.equals(checksums.get(relativePath), checksum)) {
                    continue;
                }

                uploadFile(credentials, accessToken, relativePath, file);
                checksums.put(relativePath, checksum);
                uploaded++;
            }

            saveState(checksums);
            Instant finishedAt = Instant.now();
            statusRef.set(new JournalBackupStatus(
                "SUCCESS",
                finishedAt,
                finishedAt,
                false,
                scanned,
                uploaded,
                uploaded == 0 ? "No changes to upload" : "Uploaded " + uploaded + " changed file(s)"
            ));
        } catch (Exception ex) {
            Instant failedAt = Instant.now();
            Instant lastSuccess = previous != null ? previous.lastSuccessAt() : null;
            String message = ex.getMessage() == null ? "Backup failed" : ex.getMessage();
            log.warn("OneDrive backup failed: {}", message, ex);
            statusRef.set(new JournalBackupStatus(
                "FAILED",
                failedAt,
                lastSuccess,
                false,
                0,
                0,
                message
            ));
        } finally {
            running.set(false);
        }
    }

    private OneDriveBackupCredentials loadCredentials() {
        if (!Files.exists(credentialsFile)) {
            throw new IllegalStateException("OneDrive credentials file not found: " + credentialsFile);
        }

        try {
            OneDriveBackupCredentials credentials = objectMapper.readValue(credentialsFile.toFile(), OneDriveBackupCredentials.class);
            if (isBlank(credentials.tenantId())
                || isBlank(credentials.clientId())
                || isBlank(credentials.refreshToken())) {
                throw new IllegalStateException("OneDrive credentials file is missing required fields");
            }
            return credentials;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read OneDrive credentials file", ex);
        }
    }

    private String requestAccessTokenFromRefreshToken(OneDriveBackupCredentials credentials) {
        String tokenUrl = "https://login.microsoftonline.com/" + credentials.tenantId() + "/oauth2/v2.0/token";
        String formBody = buildTokenRequestBody(credentials);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to get OneDrive access token (HTTP " + response.statusCode() + ")");
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode accessToken = json.get("access_token");
            if (accessToken == null || accessToken.asText().isBlank()) {
                throw new IllegalStateException("OneDrive access token missing from token response");
            }

            JsonNode rotatedRefreshToken = json.get("refresh_token");
            if (rotatedRefreshToken != null && !rotatedRefreshToken.asText().isBlank()
                && !rotatedRefreshToken.asText().equals(credentials.refreshToken())) {
                saveCredentials(new OneDriveBackupCredentials(
                    credentials.tenantId(),
                    credentials.clientId(),
                    rotatedRefreshToken.asText(),
                    credentials.remoteRootFolder()
                ));
            }
            return accessToken.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to request OneDrive access token", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to request OneDrive access token", ex);
        }
    }

    private String buildTokenRequestBody(OneDriveBackupCredentials credentials) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", credentials.clientId());
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", credentials.refreshToken());
        params.put("scope", AUTH_SCOPE);

        List<String> encoded = new ArrayList<>();
        params.forEach((key, value) -> encoded.add(urlEncode(key) + "=" + urlEncode(value)));
        return String.join("&", encoded);
    }

    private Map<String, String> loadState() {
        if (!Files.exists(stateFile)) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(stateFile.toFile(), CHECKSUM_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read OneDrive backup state file", ex);
        }
    }

    private void saveState(Map<String, String> checksums) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), checksums);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write OneDrive backup state file", ex);
        }
    }

    private synchronized void saveCredentials(OneDriveBackupCredentials credentials) {
        try {
            Files.createDirectories(credentialsFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(credentialsFile.toFile(), credentials);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write OneDrive credentials file", ex);
        }
    }

    private List<Path> collectJournalFiles() {
        List<Path> files = new ArrayList<>();

        collectRegularFiles(markdownRoot, files);
        collectRegularFiles(imageRoot, files);

        files.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
        return files;
    }

    private void collectRegularFiles(Path root, List<Path> files) {
        if (!Files.exists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(files::add);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan files in " + root, ex);
        }
    }

    private String mapRelativeBackupPath(Path file) {
        if (file.startsWith(markdownRoot)) {
            return "entries/" + toRemotePath(markdownRoot.relativize(file).toString());
        }

        if (file.startsWith(imageRoot)) {
            return "images/" + toRemotePath(imageRoot.relativize(file).toString());
        }

        throw new IllegalArgumentException("File is outside known journal roots: " + file);
    }

    private String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            return toHex(hash);
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash file " + file, ex);
        }
    }

    private DriveCheckResult checkDriveReachability(String accessToken) {
        URI driveUri = URI.create("https://graph.microsoft.com/v1.0/me/drive?$select=id,name,driveType");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(driveUri)
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return new DriveCheckResult(false, null, "Drive check failed (HTTP " + response.statusCode() + ")");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String driveName = json.hasNonNull("name") ? json.get("name").asText() : null;
            String driveType = json.hasNonNull("driveType") ? json.get("driveType").asText() : "unknown";
            return new DriveCheckResult(true, driveName, "Drive reachable (" + driveType + ")");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DriveCheckResult(false, null, "Drive check interrupted");
        } catch (IOException ex) {
            return new DriveCheckResult(false, null, "Drive check failed: " + ex.getMessage());
        }
    }

    private void uploadFile(OneDriveBackupCredentials credentials, String accessToken, String relativePath, Path file) {
        String remoteRoot = credentials.remoteRootFolder();
        String remotePath = isBlank(remoteRoot) ? relativePath : trimSlashes(remoteRoot) + "/" + relativePath;
        String encodedPath = encodeRemotePath(remotePath);

        URI uploadUri = URI.create("https://graph.microsoft.com/v1.0/me/drive/root:/" + encodedPath + ":/content");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                .uri(uploadUri)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(Files.readAllBytes(file)))
                .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file bytes for upload: " + file, ex);
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("OneDrive upload failed for " + relativePath + " (HTTP " + response.statusCode() + ")");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OneDrive upload request failed for " + relativePath, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("OneDrive upload request failed for " + relativePath, ex);
        }
    }

    private String encodeRemotePath(String path) {
        String[] segments = path.split("/");
        List<String> encoded = new ArrayList<>(segments.length);
        for (String segment : segments) {
            encoded.add(urlEncode(segment));
        }
        return String.join("/", encoded);
    }

    private String toRemotePath(String localPath) {
        return localPath.replace('\\', '/');
    }

    private String trimSlashes(String value) {
        String trimmed = value;
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String getRequiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalStateException("Expected field in response: " + fieldName);
        }
        return value.asText();
    }

    private String buildOauthErrorMessage(String prefix, HttpResponse<String> response) {
        String base = prefix + " (HTTP " + response.statusCode() + ")";
        String body = response.body();
        if (isBlank(body)) {
            return base;
        }

        String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
        String snippet = normalized.length() > 300 ? normalized.substring(0, 300) + "..." : normalized;
        return base + ": " + snippet;
    }

    private String normalizeRemoteRootFolder(String value) {
        if (isBlank(value)) {
            return null;
        }
        return trimSlashes(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record DriveCheckResult(boolean reachable, String driveName, String message) {
    }

    private record PendingDeviceBootstrap(
        String requestId,
        String tenantId,
        String clientId,
        String remoteRootFolder,
        String deviceCode,
        Instant expiresAt,
        int intervalSeconds,
        Instant createdAt
    ) {
    }
}
