"""Genere les fichiers de commandes de la demo (deterministes : meme graine, memes fichiers).

commandes-2026-11-24.csv : 10 000 commandes valides.
commandes-2026-11-25.csv : 10 000 commandes, dont 3 lignes defectueuses :
  - ligne 2 500 : montant negatif ;
  - ligne 5 000 : une colonne manquante ;
  - ligne 7 500 : une commande deja importee la veille (id 42).
commandes-2026-11-26.csv : 10 000 commandes, dont 20 montants negatifs (une toutes les 500 lignes).
"""
import random
from datetime import date, timedelta
from pathlib import Path

ICI = Path(__file__).parent
CLIENTS = ["Durand", "Martin", "Bernard", "Petit", "Robert", "Richard", "Moreau", "Simon", "Laurent", "Michel"]


def lignes(debut_id, jour, graine):
    r = random.Random(graine)
    for i in range(10_000):
        d = jour - timedelta(days=r.randint(0, 6))
        yield [str(debut_id + i), r.choice(CLIENTS), f"{r.randint(500, 50_000) / 100:.2f}", d.isoformat()]


def ecrire(nom, rangs):
    with open(ICI / nom, "w", encoding="utf-8", newline="\n") as f:
        f.write("id,client,montant,date\n")
        for r in rangs:
            f.write(",".join(r) + "\n")


premier = list(lignes(1, date(2026, 11, 24), 24))
ecrire("commandes-2026-11-24.csv", premier)

defauts = list(lignes(10_001, date(2026, 11, 25), 25))
defauts[2_499][2] = "-15.00"
defauts[4_999] = defauts[4_999][:3]
defauts[7_499] = list(premier[41])  # la commande 42 du 24/11, recopiee telle quelle
ecrire("commandes-2026-11-25.csv", defauts)

# Un fichier tres abime : un montant negatif toutes les 500 lignes (20 lignes fausses).
abime = list(lignes(20_001, date(2026, 11, 26), 26))
for i in range(499, 10_000, 500):
    abime[i][2] = "-" + abime[i][2]
ecrire("commandes-2026-11-26.csv", abime)
print("ok")
