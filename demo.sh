#!/usr/bin/env bash
# Rejoue chaque scenario de la video et ecrit la sortie dans preuves/.
# Prerequis : Docker, un JDK 25 (JAVA_HOME), et rien d'autre sur le port 5433.
set -u
cd "$(dirname "$0")"
JAVA="${JAVA_HOME:-}/bin/java"; [ -x "$JAVA" ] || JAVA=java
JAR=target/import-commandes-0.0.1-SNAPSHOT.jar
mkdir -p preuves

# --- PostgreSQL ---
docker rm -f batch-pg >/dev/null 2>&1
docker run -d --name batch-pg -e POSTGRES_USER=batch -e POSTGRES_PASSWORD=batch -e POSTGRES_DB=commandes \
  -p 5433:5432 postgres:15 >/dev/null
for i in $(seq 60); do docker exec batch-pg pg_isready -U batch -d commandes >/dev/null 2>&1 && break; sleep 1; done
docker exec batch-pg pg_isready -U batch -d commandes >/dev/null 2>&1 || { echo "PostgreSQL ne repond pas"; exit 1; }
sleep 2

sql()   { docker exec batch-pg psql -U batch -d commandes -c "$1" 2>&1 | grep -vE "^\(|^$"; }
raz()   { docker exec batch-pg psql -q -U batch -d commandes -c "drop schema public cascade; create schema public;" >/dev/null 2>&1; }
# Garde les lignes utiles de la sortie de l'appli (pas les traces de pile ni l'horodatage des logs).
lancer() {
  echo "\$ java -jar import-commandes.jar $*"
  "$JAVA" -jar "$JAR_COURANT" "$@" 2>&1 \
    | grep -vE "^\s+at |^\s*$|^\s+\.\.\. [0-9]+ more|Call getNextException" \
    | sed -E 's/^[0-9T:.+-]+ +([A-Z]+) [0-9]+ --- \[ *[a-z]+\] [a-z.]*\.([A-Za-z]+) +: /\1 \2 : /' \
    | grep -vE "^(INFO|WARN) (HikariDataSource|Version|Dialect|DataSourceInitializer)" \
    | sed -E 's#file \[[^]]*[\/]donnees[\/]#file [donnees/#' \
    | cut -c1-220
  echo "(code de sortie : ${PIPESTATUS[0]})"
}
bilan_base() {
  sql "select count(*) as commandes_en_base from commande"
  sql "select job_execution_id as execution, status, exit_code from batch_job_execution order by 1" 2>/dev/null
  sql "select step_execution_id as etape, status, read_count as lues, write_count as ecrites, commit_count as commits from batch_step_execution order by 1" 2>/dev/null
}

"$JAVA" -version 2>&1 | grep -q 'version "25' || { echo "il faut un JDK 25 (JAVA_HOME)"; exit 1; }
PYTHON=$(command -v python3 || command -v python)
./mvnw -q -DskipTests -Pmemoire package && cp $JAR target/memoire.jar || { echo "echec de la compilation"; exit 1; }
./mvnw -q -DskipTests package || { echo "echec de la compilation"; exit 1; }
"$PYTHON" donnees/generer.py >/dev/null || { echo "echec de generer.py"; exit 1; }
JAR_COURANT=$JAR

# 01 - L'import normal
raz
{ lancer fichier=donnees/commandes-2026-11-24.csv; bilan_base; } > preuves/01-import-normal.txt

# 02 - Arret brutal a la ligne 6 543, puis relance : refusee
raz
{ lancer --import.crash-a-la-ligne=6543 fichier=donnees/commandes-2026-11-24.csv; bilan_base
  echo; echo "=== relance"; lancer fichier=donnees/commandes-2026-11-24.csv; bilan_base; } > preuves/02-crash-relance-refusee.txt

# 03 - Recuperation (JobOperator.recover) puis reprise a la ligne 6 501
{ lancer --import.recuperer-apres-crash=true fichier=donnees/commandes-2026-11-24.csv; bilan_base; } > preuves/03-recover-reprise.txt

# 04 - Relancer un fichier deja importe
{ lancer fichier=donnees/commandes-2026-11-24.csv; bilan_base; } > preuves/04-deja-complete.txt

# 05 - Un parametre horodate : nouvelle instance a chaque lancement
raz
{ lancer --import.crash-a-la-ligne=6543 fichier=donnees/commandes-2026-11-24.csv lancement=1764000000; bilan_base
  echo; echo "=== relance, nouvel horodatage"; lancer fichier=donnees/commandes-2026-11-24.csv lancement=1764003600; bilan_base; } > preuves/05-parametre-horodate.txt

# 06 - Meme chose sans cle primaire : les doublons passent en silence
raz
docker exec batch-pg psql -q -U batch -d commandes -c "create table commande (id bigint, client varchar(100) not null, montant numeric(10,2) not null, date_commande date not null);" >/dev/null
{ lancer --import.crash-a-la-ligne=6543 fichier=donnees/commandes-2026-11-24.csv lancement=1764000000
  echo; echo "=== relance, nouvel horodatage"; lancer fichier=donnees/commandes-2026-11-24.csv lancement=1764003600
  sql "select count(*) as lignes, count(distinct id) as commandes_distinctes from commande"; } > preuves/06-sans-cle-primaire.txt

# 07 - Le starter seul : historique en memoire
JAR_COURANT=target/memoire.jar
raz
{ lancer --import.crash-a-la-ligne=6543 fichier=donnees/commandes-2026-11-24.csv
  sql "select count(*) as commandes_en_base from commande"; sql "\dt"
  echo; echo "=== relance"; lancer fichier=donnees/commandes-2026-11-24.csv
  sql "select count(*) as commandes_en_base from commande"; } > preuves/07-starter-memoire.txt
JAR_COURANT=$JAR

# 08 - Fichier du lendemain avec 3 lignes defectueuses, sans tolerance
raz
"$JAVA" -jar $JAR fichier=donnees/commandes-2026-11-24.csv >/dev/null 2>&1
{ lancer fichier=donnees/commandes-2026-11-25.csv; bilan_base; } > preuves/08-lignes-defectueuses-strict.txt

# 09 - Meme fichier, avec skip (tolerance de 10 lignes)
raz
"$JAVA" -jar $JAR fichier=donnees/commandes-2026-11-24.csv >/dev/null 2>&1
{ lancer --import.tolerant=true fichier=donnees/commandes-2026-11-25.csv; bilan_base; } > preuves/09-lignes-defectueuses-skip.txt

# 10 - Taille des paquets : duree de l'etape (2 essais chacun)
{ for n in 1 10 100 1000 10000; do for essai in 1 2; do
    raz
    echo -n "chunk $n, essai $essai : "
    "$JAVA" -jar $JAR --import.taille-chunk=$n fichier=donnees/commandes-2026-11-24.csv 2>&1 \
      | grep -oE "executed in [0-9a-z]+|commits=[0-9]+" | tr '\n' ' '; echo
  done; done; } > preuves/10-taille-chunk.txt

# 11 - Rejeu ligne par ligne selon la taille du paquet (appels au controle pour 9 999 lignes lues)
{ for n in 100 1000 10000; do
    raz
    "$JAVA" -jar $JAR fichier=donnees/commandes-2026-11-24.csv >/dev/null 2>&1
    echo -n "chunk $n : "
    "$JAVA" -jar $JAR --import.tolerant=true --import.taille-chunk=$n fichier=donnees/commandes-2026-11-25.csv 2>&1 | grep -oE "BILAN.*|executed in [0-9a-z]+" | tr '\n' ' '; echo
  done; } > preuves/11-rejeu-selon-chunk.txt

# 12 - Un fichier tres abime : la limite de 10 rejets fait echouer le job
raz
{ lancer --import.tolerant=true fichier=donnees/commandes-2026-11-26.csv; bilan_base; } > preuves/12-limite-de-rejets.txt

# 13 - Un seul paquet de 10 000 lignes, arret brutal a la ligne 9 999 : rien n'est valide
raz
{ lancer --import.taille-chunk=10000 --import.crash-a-la-ligne=9999 fichier=donnees/commandes-2026-11-24.csv; bilan_base; } > preuves/13-gros-paquet-crash.txt

docker rm -f batch-pg >/dev/null
echo "Preuves ecrites dans preuves/"
