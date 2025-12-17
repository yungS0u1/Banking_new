package org.example.repo;

import org.example.domain.LeasedAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeasedAssetRepository extends JpaRepository<LeasedAsset, Long> {}
