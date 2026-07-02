package com.tricv.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecrutementConfig {

    @Value("${recrutement.ecoles.publiques-ingenieur}")
    private String ecolesPubliquesIngenieur;

    @Value("${recrutement.ecoles.privees-ingenieur}")
    private String ecolesPriveesIngenieur;

    public String getEcolesPubliquesIngenieur() {
        return ecolesPubliquesIngenieur.replace(",", ", ");
    }

    public String getEcolesPriveesIngenieur() {
        return ecolesPriveesIngenieur.replace(",", ", ");
    }
}
