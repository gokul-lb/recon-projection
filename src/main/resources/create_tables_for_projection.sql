CREATE TABLE IF NOT EXISTS total_order_events(
                                                 id int PRIMARY KEY,
                                                 name varchar(20),
    total_events BIGINT NOT NULL
    );

INSERT INTO total_order_events (id, name, total_events) VALUES (1, 'TotalEventCounter', 0) ON CONFLICT DO NOTHING;