# Витрина магазина

Витрина: каталог, корзина и заказ из корзины. Заказ оплачивается в отдельном сервисе платежей. Список товаров и карточки читаются из Redis, при промахе загружаются из базы и кладутся в кеш на 2 минуты.

Проект собирается как мультимодуль Maven:

- `my-market-app` — витрина, порт 8080
- `payment-service` — баланс и списание, порт 8081

## Стек

- Java 21
- Spring Boot
- Spring WebFlux
- Thymeleaf
- Spring Data R2DBC и H2 в памяти
- Spring Data Redis, реактивный Lettuce
- OpenAPI Generator: реактивный сервер платежей и WebClient витрины
- Maven
- JUnit 5, Spring Boot Test и WebTestClient
- встроенный Netty
- исполняемые JAR
- Docker Compose

## Сборка и тесты

Из корня репозитория:

```bash
./mvnw test
./mvnw package
```

Тесты витрины ходят в Redis на `localhost:6379`, база с номером 1. Сервис платежей для этих тестов не нужен: его заменяет `MockWebServer`. Свои тесты сервиса платежей поднимают только его контекст.

## Локальный запуск

Нужен Redis на `localhost:6379`. В двух терминалах:

```bash
./mvnw -pl payment-service spring-boot:run
./mvnw -pl my-market-app spring-boot:run
```

- Витрина: http://localhost:8080
- Платежи: http://localhost:8081

Стартовый баланс — 100000. Кнопка «Купить» активна, пока баланс не меньше суммы корзины и сервис платежей отвечает. Иначе кнопка недоступна и на странице корзины написано, чего не хватает: средств или самого сервиса.

## Docker

Образ витрины собирается из корня:

```bash
docker build -t my-market-app .
docker run --rm -p 8080:8080 -e SPRING_DATA_REDIS_HOST=host.docker.internal -e PAYMENT_BASE_URL=http://host.docker.internal:8081 my-market-app
```

Образ платежей:

```bash
docker build -t payment-service -f payment-service/Dockerfile .
docker run --rm -p 8081:8081 payment-service
```

Compose поднимает Redis, платежи и витрину. Redis остаётся внутри сети Compose и не занимает порт 6379 на хосте, так что уже запущенный локальный Redis не мешает.

```bash
docker compose up --build
```

Витрина: http://localhost:8080. Платежи: http://localhost:8081.

## Эндпоинты витрины

| Метод и путь | Что делает |
| --- | --- |
| `GET /`, `GET /items` | Каталог: `search`, `sort` (`NO`, `ALPHA`, `PRICE`), `pageNumber`, `pageSize`. |
| `POST /items` | Количество в каталоге: `PLUS`, `MINUS`. |
| `GET /items/{id}` | Карточка товара. |
| `POST /items/{id}` | Количество на карточке: `PLUS`, `MINUS`. |
| `GET /cart/items` | Корзина и проверка баланса. |
| `POST /cart/items` | Строка корзины: `PLUS`, `MINUS`, `DELETE`. |
| `GET /orders` | Список заказов. |
| `GET /orders/{id}` | Один заказ. |
| `POST /buy` | Списать сумму корзины и оформить заказ. |

## Эндпоинты платежей

Обмен в JSON, контракт в `openapi/payment-api.yaml`.

| Метод и путь | Что делает |
| --- | --- |
| `GET /balance` | Текущий баланс: `{ "amount": 100000 }`. |
| `POST /payments` | Списать `{ "amount": 129 }`. `200` и новый баланс, либо `402`, если списание ушло бы в минус. |
