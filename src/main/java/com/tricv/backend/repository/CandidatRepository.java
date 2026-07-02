package com.tricv.backend.repository;

import com.tricv.backend.model.Candidat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CandidatRepository extends JpaRepository<Candidat, Long> {
    List<Candidat> findByNomContainingIgnoreCase(String nom);
}