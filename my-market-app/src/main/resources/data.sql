DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cart_items;
DELETE FROM carts;
DELETE FROM users;
DELETE FROM items;

INSERT INTO users (username, password, enabled) VALUES
('buyer', '$2a$10$e9NZHI4CB8ij9MBT96bJjuIFwAqx160JzcmsKXl4eCrDKswASVpem', TRUE),
('second', '$2a$10$qAFPjU37Cm0ztDTkIr7uMuZX5cbxYbw9gAqYBP8DIL.Jr9zDYMT2O', TRUE);


INSERT INTO items (title, description, img_path, price) VALUES
('Яблоки Гала', 'Сладкие красные яблоки, 1 кг', 'images/apples.png', 129),
('Молоко 3,2%', 'Пастеризованное молоко, 1 л', 'images/milk.png', 89),
('Хлеб бородинский', 'Ржаной хлеб на закваске, 400 г', 'images/bread.png', 64),
('Сыр Российский', 'Полутвёрдый сыр, 300 г', 'images/cheese.png', 349),
('Кофе в зёрнах', 'Арабика средней обжарки, 250 г', 'images/coffee.png', 590),
('Чай чёрный', 'Крупнолистовой цейлонский чай, 100 г', 'images/tea.png', 210),
('Макароны', 'Пшеничные спагетти, 450 г', 'images/pasta.png', 95),
('Масло оливковое', 'Extra virgin, 500 мл', 'images/oil.png', 540),
('Шоколад тёмный', 'Плитка с 70% какао, 90 г', 'images/chocolate.png', 149),
('Вода минеральная', 'Негазированная вода, 1,5 л', 'images/water.png', 55),
('Гречневая крупа', 'Ядрица, 800 г', 'images/buckwheat.png', 112),
('Йогурт натуральный', 'Густой йогурт без добавок, 300 г', 'images/yogurt.png', 78);
