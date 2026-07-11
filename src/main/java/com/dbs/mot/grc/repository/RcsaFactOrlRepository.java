package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.RcsaFactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JDBC repository for {@code rcsa_fact_orl}.
 * Plain derived query only — {@code biz_dt} filtered in the WHERE clause.
 */
@Repository
public interface RcsaFactOrlRepository extends CrudRepository<RcsaFactOrl, Long> {

    List<RcsaFactOrl> findByBizDt(LocalDate bizDt);
}
