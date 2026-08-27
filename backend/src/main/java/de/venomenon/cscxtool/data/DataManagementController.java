package de.venomenon.cscxtool.data;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/data")
class DataManagementController {

    private final BackupService backups;
    private final RestoreService restores;
    private final ExportService exports;
    private final CsvExportService csv;

    DataManagementController(BackupService backups, RestoreService restores, ExportService exports, CsvExportService csv) {
        this.backups = backups;
        this.restores = restores;
        this.exports = exports;
        this.csv = csv;
    }

    @GetMapping
    BackupOverview overview() { return backups.overview(); }

    @PostMapping("/backups")
    BackupSummary createBackup() { return backups.create(BackupReason.MANUAL); }

    @GetMapping("/backups/{id}/download")
    ResponseEntity<org.springframework.core.io.Resource> downloadBackup(@PathVariable String id) {
        java.nio.file.Path file = backups.resolveKnownArtifact(id);
        org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.getFileName().toString())).body(resource);
    }

    @PostMapping("/restore/preview/backups/{id}")
    RestorePreview previewBackup(@PathVariable String id) { return restores.previewKnownBackup(id); }

    @PostMapping(value = "/restore/preview/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RestorePreview previewUpload(@RequestPart("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".cscbackup")) {
            return restores.previewUploadedBackup(file.getInputStream(), name);
        }
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            return restores.previewUploadedJson(file.getInputStream(), name);
        }
        throw new BackupFileException("RESTORE_FILE_TYPE_UNSUPPORTED", "Bitte w\u00e4hlen Sie eine .cscbackup- oder .json-Datei.");
    }

    @PostMapping("/restore")
    RestoreResult restore(@Valid @RequestBody ConfirmRestoreRequest request) { return restores.restore(request.token()); }

    @GetMapping(value = "/export/full", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> fullExport() { return download("csc-x-tool-export.json", MediaType.APPLICATION_JSON, exports.exportJson()); }

    @GetMapping("/export/candidates.csv")
    ResponseEntity<byte[]> candidates() { return csv("kandidaten.csv", this.csv.candidates()); }
    @GetMapping("/export/contest-entries.csv")
    ResponseEntity<byte[]> contestEntries() { return csv("wettbewerbsbeitraege.csv", this.csv.contestEntries()); }
    @GetMapping("/export/participants.csv")
    ResponseEntity<byte[]> participants() { return csv("teilnehmer.csv", this.csv.participants()); }
    @GetMapping("/export/results.csv")
    ResponseEntity<byte[]> results() { return csv("ergebnisse.csv", this.csv.results()); }

    private static ResponseEntity<byte[]> csv(String name, byte[] data) {
        return download(name, new MediaType("text", "csv", StandardCharsets.UTF_8), data);
    }
    private static ResponseEntity<byte[]> download(String name, MediaType contentType, byte[] data) {
        return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION, attachment(name)).body(data);
    }
    private static String attachment(String name) {
        return ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString();
    }
}
