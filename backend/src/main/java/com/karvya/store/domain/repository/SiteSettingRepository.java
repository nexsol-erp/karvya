package com.karvya.store.domain.repository;

import com.karvya.store.domain.model.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
    List<SiteSetting> findAllByOrderByKeyAsc();
}
