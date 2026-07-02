package com.tricv.backend.repository;

import com.tricv.backend.model.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    Optional<Evaluation> findByCandidatIdAndDomaineIgnoreCase(Long candidatId, String domaine);
    Optional<Evaluation> findByCandidatIdAndDomaineIgnoreCaseAndProfilIgnoreCase(Long candidatId, String domaine, String profil);
    void deleteByCandidatId(Long candidatId);
}