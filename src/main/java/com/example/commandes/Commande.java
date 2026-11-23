package com.example.commandes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Une commande controlee, prete a etre ecrite en base. */
public record Commande(long id, String client, BigDecimal montant, LocalDate date) {
}
