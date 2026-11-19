package com.medicore.service;

import com.medicore.exception.DomainException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReportFileStorage {
    private final Path root;

    public ReportFileStorage(@Value("${medicore.reports.storage-directory:./storage/reports}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(byte[] pdf) {
        String key = UUID.randomUUID() + ".pdf";
        storeWithKey(key, pdf);
        return key;
    }

    public void storeWithKey(String key, byte[] pdf) {
        try {
            Files.createDirectories(root);
            Files.write(fileFor(key), pdf, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new IllegalStateException("The report could not be stored in protected file storage.");
        }
    }

    public byte[] load(String key) {
        try { return Files.readAllBytes(fileFor(key)); }
        catch (IOException exception) { throw DomainException.notFound("Report file is unavailable."); }
    }

    public void delete(String key) {
        try { Files.deleteIfExists(fileFor(key)); }
        catch (IOException ignored) { /* Failed cleanup is handled by retention/backup reconciliation. */ }
    }

    private Path fileFor(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.pdf")) throw DomainException.notFound("Report file is unavailable.");
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw DomainException.notFound("Report file is unavailable.");
        return target;
    }
}
