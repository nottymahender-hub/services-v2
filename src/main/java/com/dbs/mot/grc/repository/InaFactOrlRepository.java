package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.InaFactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring Data JDBC repository for {@code ina_fact_orl}.
 * Plain derived query only — {@code biz_dt} filtered in the WHERE clause.
 */
@Repository
public interface InaFactOrlRepository extends CrudRepository<InaFactOrl, Long> {

    List<InaFactOrl> findByBizDt(LocalDate bizDt);
}
