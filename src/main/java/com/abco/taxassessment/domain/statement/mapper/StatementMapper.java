package com.abco.taxassessment.domain.statement.mapper;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import com.abco.taxassessment.domain.statement.dto.StatementResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for StatementEntity ↔ StatementResponse.
 * No logic in mappers (§3.2) — all field names match directly.
 */
@Mapper
public interface StatementMapper {

    StatementResponse toResponse(StatementEntity entity);

    List<StatementResponse> toResponseList(List<StatementEntity> entities);
}
