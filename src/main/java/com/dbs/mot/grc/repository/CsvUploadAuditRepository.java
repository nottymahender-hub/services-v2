package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.CsvUploadAudit;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CsvUploadAuditRepository extends CrudRepository<CsvUploadAudit, Long> {
}
