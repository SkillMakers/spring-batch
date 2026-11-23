package com.example.commandes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ControleCommandeTest {

	private final ControleCommande controle = new ControleCommande(0);

	@Test
	@DisplayName("Une ligne valide devient une commande")
	void ligneValide() {
		// GIVEN
		LigneCsv ligne = new LigneCsv("7", "Durand", "145.27", "2026-11-22");

		// WHEN
		Commande commande = controle.process(ligne);

		// THEN
		assertThat(commande).isEqualTo(new Commande(7, "Durand", new BigDecimal("145.27"), LocalDate.of(2026, 11, 22)));
	}

	@Test
	@DisplayName("Un montant negatif est refuse")
	void montantNegatif() {
		// GIVEN
		LigneCsv ligne = new LigneCsv("12500", "Simon", "-15.00", "2026-11-20");

		// WHEN / THEN
		assertThatThrownBy(() -> controle.process(ligne))
				.isInstanceOf(MontantInvalideException.class)
				.hasMessage("ligne 12500 : montant -15.00");
	}

	@Test
	@DisplayName("Chaque appel est compte, y compris ceux qui echouent")
	void compteLesAppels() {
		// GIVEN
		LigneCsv valide = new LigneCsv("1", "Martin", "10.00", "2026-11-24");
		LigneCsv invalide = new LigneCsv("2", "Martin", "0", "2026-11-24");

		// WHEN
		controle.process(valide);
		assertThatThrownBy(() -> controle.process(invalide)).isInstanceOf(MontantInvalideException.class);

		// THEN
		assertThat(controle.appels()).isEqualTo(2);
	}
}
