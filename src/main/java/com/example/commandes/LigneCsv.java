package com.example.commandes;

/** Une ligne du fichier, telle qu'elle est lue, avant tout controle. */
public record LigneCsv(String id, String client, String montant, String date) {
}
