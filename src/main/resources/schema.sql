CREATE TABLE IF NOT EXISTS commande (
    id            BIGINT PRIMARY KEY,
    client        VARCHAR(100) NOT NULL,
    montant       NUMERIC(10, 2) NOT NULL,
    date_commande DATE NOT NULL
);
