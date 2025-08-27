CREATE TABLE articles (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    version INT NOT NULL,
    updated_at VARCHAR(40) NOT NULL,
    word_count INT NOT NULL
);

