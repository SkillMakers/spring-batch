# Spring Batch : tuto complet et 6 pièges (Spring Batch 6, Spring Boot 4)

Code de la vidéo de la chaîne [IT Skills Maker](https://www.youtube.com/@ItSkillsMaker).

Un job Spring Batch qui importe chaque soir un fichier CSV de 10 000 commandes dans PostgreSQL, par paquets (chunks) de
100 lignes, et six pièges provoqués pour de vrai : arrêt brutal de la JVM au milieu du fichier, relances, lignes
défectueuses. Chaque sortie de la vidéo vient de [`preuves/`](preuves/).

Versions : Spring Boot 4.1.2, Spring Batch 6.0.6, Java 25, PostgreSQL 15 (Docker).

## Lancer

Il faut Docker et un JDK 25.

```
bash demo.sh
```

`demo.sh` démarre PostgreSQL sur le port 5433, construit les deux variantes de l'application, génère les fichiers de
`donnees/` (`donnees/generer.py`, Python 3), joue chaque scénario et réécrit `preuves/`. Comptez ~6 minutes, dont 4
pour les paquets d'une ligne.

Pour lancer l'import à la main (PostgreSQL démarré) :

```
./mvnw package
java -jar target/import-commandes-0.0.1-SNAPSHOT.jar fichier=donnees/commandes-2026-11-24.csv
```

Options : `--import.taille-chunk=1000`, `--import.tolerant=true` (skip des lignes défectueuses, 10 au plus),
`--import.crash-a-la-ligne=6543` (arrêt brutal par `Runtime.halt`), `--import.recuperer-apres-crash=true`
(`JobOperator.recover` sur les exécutions restées `STARTED`).

## Le code

| Fichier | Rôle |
|---|---|
| [`ImportConfig.java`](src/main/java/com/example/commandes/ImportConfig.java) | le job, l'étape en chunks, le reader CSV, le writer JDBC, le mode tolérant |
| [`ControleCommande.java`](src/main/java/com/example/commandes/ControleCommande.java) | le processor : contrôle et transforme une ligne, compte ses appels, simule l'arrêt brutal |
| [`RecuperationApresCrash.java`](src/main/java/com/example/commandes/RecuperationApresCrash.java) | déclare échouée l'exécution fantôme avant la relance (Spring Batch 6) |
| [`pom.xml`](pom.xml) | profil `jdbc` (par défaut) : `spring-boot-starter-batch-jdbc` ; profil `memoire` : `spring-boot-starter-batch` |

## Les six pièges

| Piège | Ce qui se passe | Correction | Preuve |
|---|---|---|---|
| Le starter seul | Spring Boot 4 : `spring-boot-starter-batch` garde l'historique en mémoire. Après un crash, la relance repart de la ligne 1 | `spring-boot-starter-batch-jdbc` + `spring.batch.jdbc.initialize-schema=always` | [07](preuves/07-starter-memoire.txt) |
| L'exécution fantôme | Après un arrêt brutal, l'exécution reste `STARTED` : `JobExecutionAlreadyRunningException` à chaque relance | `jobOperator.recover(execution)`, seulement si rien d'autre ne tourne | [02](preuves/02-crash-relance-refusee.txt), [03](preuves/03-recover-reprise.txt) |
| Le paramètre horodaté | Une instance par lancement : après un crash, la relance repart de zéro (doublons sans clé primaire) | Des paramètres qui identifient les données (le fichier), jamais l'heure | [04](preuves/04-deja-complete.txt), [05](preuves/05-parametre-horodate.txt), [06](preuves/06-sans-cle-primaire.txt) |
| Une ligne fausse arrête tout | Un montant négatif fait échouer le job | `faultTolerant().skip(…).skipLimit(10).skipListener(…)`, et des rejets que quelqu'un relit | [08](preuves/08-lignes-defectueuses-strict.txt), [09](preuves/09-lignes-defectueuses-skip.txt), [12](preuves/12-limite-de-rejets.txt) |
| Le processor rejoué | Quand l'écriture d'un paquet échoue, il est rejoué ligne par ligne, processor compris | Un processor sans effet de bord (pas d'e-mail, pas d'appel facturé) | [09](preuves/09-lignes-defectueuses-skip.txt), [11](preuves/11-rejeu-selon-chunk.txt) |
| La taille des paquets | 1 ligne : 2 min ; 100 : 2,3 s ; 1 000 : 1,4 s ; au-delà, plus rien à gagner, et plus à perdre | 100 à 1 000, mesuré sur vos données | [10](preuves/10-taille-chunk.txt), [13](preuves/13-gros-paquet-crash.txt) |

## Tests

```
./mvnw test
```

Tests unitaires du processor (sans base).
