package com.abco.taxassessment.event.domain;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;

import java.util.UUID;

/** Published when a statement is successfully ingested and ready for the agent pipeline (UC-07, UC-08). */
public class StatementIngestedEvent extends BaseAgentEvent {

    private UUID statementId;
    private UUID accountId;
    private String sourceFormat;
    private String filePathEncrypted;
    private boolean amendment;

    public StatementIngestedEvent() {}

    public StatementIngestedEvent(UUID tenantId, UUID statementId, UUID accountId,
                                   StatementEntity.SourceFormat format, String filePathEncrypted,
                                   boolean amendment) {
        super("statement.ingested", 1, tenantId, "Statement", statementId);
        this.statementId = statementId;
        this.accountId = accountId;
        this.sourceFormat = format.name();
        this.filePathEncrypted = filePathEncrypted;
        this.amendment = amendment;
    }

    public UUID getStatementId() { return statementId; }
    public UUID getAccountId() { return accountId; }
    public String getSourceFormat() { return sourceFormat; }
    public String getFilePathEncrypted() { return filePathEncrypted; }
    public boolean isAmendment() { return amendment; }
    public void setStatementId(UUID statementId) { this.statementId = statementId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }
    public void setFilePathEncrypted(String filePathEncrypted) { this.filePathEncrypted = filePathEncrypted; }
    public void setAmendment(boolean amendment) { this.amendment = amendment; }
}
