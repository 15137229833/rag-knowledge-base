-- favorite_topics：由 text[] 改为 TEXT 存 JSON 数组，避免 Hibernate/JDBC 对 PG 数组映射不一致导致 500
ALTER TABLE user_preference ADD COLUMN IF NOT EXISTS favorite_topics_json TEXT NOT NULL DEFAULT '[]';

UPDATE user_preference p
SET favorite_topics_json = COALESCE(
    (SELECT json_agg(x)::text
     FROM unnest(COALESCE(p.favorite_topics, ARRAY[]::text[])) AS t(x)),
    '[]'
);

ALTER TABLE user_preference DROP COLUMN IF EXISTS favorite_topics;
ALTER TABLE user_preference RENAME COLUMN favorite_topics_json TO favorite_topics;
ALTER TABLE user_preference ALTER COLUMN favorite_topics SET DEFAULT '[]';
ALTER TABLE user_preference ALTER COLUMN favorite_topics SET NOT NULL;
