package com.abco.taxassessment.domain.statement.application;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import com.abco.taxassessment.domain.statement.domain.service.StatementService;
import com.abco.taxassessment.domain.statement.dto.StatementResponse;
import com.abco.taxassessment.domain.statement.dto.StatementUploadRequest;
import com.abco.taxassessment.domain.statement.mapper.StatementMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Statement application service — orchestrates use case UC-07 through UC-14.
 *
 * Responsibilities at this layer (§3.2):
 * - HTTP concerns (file handling, multipart extraction)
 * - Coordinating with domain service
 * - Response mapping
 *
 * Does not contain domain rules. All domain logic lives in StatementService.
 */
@Service
public class StatementApplicationService {

    private final StatementService statementService;
    private final StatementMapper statementMapper;

    public StatementApplicationService(StatementService statementService,
                                        StatementMapper statementMapper) {
        this.statementService = statementService;
        this.statementMapper = statementMapper;
    }

    public StatementResponse uploadStatement(StatementUploadRequest request,
                                              MultipartFile file) throws IOException {
        String fileHash = computeSha256(file.getBytes());
        // In production, file is encrypted and stored in S3/GCS with tenant-scoped key
        // For this implementation, the encrypted path is a placeholder
        String encryptedPath = "encrypted://" + UUID.randomUUID() + "/" + file.getOriginalFilename();

        StatementEntity entity = statementService.ingestStatement(
                request.accountId(),
                request.periodStart(),
                request.periodEnd(),
                request.sourceFormat(),
                fileHash,
                encryptedPath,
                file.getOriginalFilename(),
                file.getSize(),
                request.wasPartial()
        );

        return statementMapper.toResponse(entity);
    }

    public StatementResponse uploadAmendment(UUID originalStatementId,
                                              StatementUploadRequest request,
                                              MultipartFile file) throws IOException {
        String fileHash = computeSha256(file.getBytes());
        String encryptedPath = "encrypted://" + UUID.randomUUID() + "/" + file.getOriginalFilename();

        StatementEntity entity = statementService.ingestAmendment(
                originalStatementId,
                request.accountId(),
                request.periodStart(),
                request.periodEnd(),
                request.sourceFormat(),
                fileHash,
                encryptedPath,
                file.getOriginalFilename(),
                file.getSize()
        );

        return statementMapper.toResponse(entity);
    }

    public List<StatementResponse> listStatements() {
        return statementMapper.toResponseList(statementService.findAllForTenant());
    }

    public StatementResponse getStatement(UUID statementId) {
        return statementMapper.toResponse(statementService.findById(statementId));
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
