package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.IncFactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JDBC repository for {@code inc_fact_orl}.
 * Plain derived query only — the {@code biz_dt} filter is applied in the WHERE clause,
 * with all row matching/derivation done in the service layer.
 */
@Repository
public interface IncFactOrlRepository extends CrudRepository<IncFactOrl, Long> {

    List<IncFactOrl> findByBizDt(LocalDate bizDt);
}
