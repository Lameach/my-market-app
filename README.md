# Витрина магазина

Витрина: каталог, корзина и заказ из корзины. Заказ оплачивается в отдельном сервисе платежей. Список товаров и карточки читаются из Redis, при промахе загружаются из базы и кладутся в кеш на 2 минуты.

Покупатель входит по логину и паролю. Корзина и заказы хранятся за ним. Витрина ходит в платежи как OAuth2-клиент: токен берёт у своего сервера авторизации и передаёт его в `Authorization: Bearer`.

Проект собирается как мультимодуль Maven:

- `my-market-app` — витрина, порт 8080
- `payment-service` — баланс и списание, порт 8081
- `auth-server` — сервер авторизации, порт 9000

## Стек

- Java 21
- Spring Boot
- Spring WebFlux
- Spring Security
- Spring Authorization Server
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

Тесты витрины ходят в Redis на `localhost:6379`, база с номером 1. Сервис платежей и сервер авторизации для этих тестов не нужны: оба заменяет `MockWebServer`. Свои тесты платежей и сервера авторизации поднимают только свой контекст.

## Локальный запуск

Нужен Redis на `localhost:6379`. В трёх терминалах:

```bash
./mvnw -pl auth-server spring-boot:run
./mvnw -pl payment-service spring-boot:run
./mvnw -pl my-market-app spring-boot:run
```

- Витрина: http://localhost:8080
- Платежи: http://localhost:8081
- Сервер авторизации: http://localhost:9000

Покупатели заведены заранее, регистрации нет: `buyer` / `buyer` и `second` / `second`. Без входа открыты каталог (`GET /`, `GET /items`), карточка товара (`GET /items/{id}`) и картинки. Корзина, заказы и изменение количества закрыты и ведут на `/login`.

У каждого покупателя свой счёт. Первый запрос создаёт его со стартовым балансом 100000. Списание одного не трогает другого.

Кнопка «Купить» активна, пока баланс этого покупателя не меньше суммы корзины и сервис платежей принимает токен клиента. Иначе кнопка недоступна и на странице корзины написано, чего не хватает: средств, авторизации клиента или самого сервиса.

Витрина — OAuth2-клиент `storefront`, секрет `storefront-secret`, grant `client_credentials`, scope `payment`. Токен запрашивается у `http://localhost:9000/oauth2/token`. `GET /balance` и `POST /payments` без заголовка `Authorization: Bearer` с этим scope не отвечают.

## Docker

Образ витрины собирается из корня:

```bash
docker build -t my-market-app .
docker run --rm -p 8080:8080 -e SPRING_DATA_REDIS_HOST=host.docker.internal -e PAYMENT_BASE_URL=http://host.docker.internal:8081 -e SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_AUTH_TOKEN_URI=http://host.docker.internal:9000/oauth2/token my-market-app
```

Образ платежей:

```bash
docker build -t payment-service -f payment-service/Dockerfile .
docker run --rm -p 8081:8081 -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://host.docker.internal:9000 payment-service
```

Образ сервера авторизации:

```bash
docker build -t auth-server -f auth-server/Dockerfile .
docker run --rm -p 9000:9000 auth-server
```

Compose поднимает Redis, сервер авторизации, платежи и витрину. Внутри сети issuer токена — `http://auth-server:9000`, его же проверяет сервис платежей. Redis остаётся внутри сети Compose и не занимает порт 6379 на хосте, так что уже запущенный локальный Redis не мешает.

```bash
docker compose up --build
```

Витрина: http://localhost:8080. Платежи: http://localhost:8081. Сервер авторизации: http://localhost:9000.

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

Оба запроса требуют `Authorization: Bearer` со scope `payment`. Без токена ответ `401`, с другим scope — `403`.

| Метод и путь | Что делает |
| --- | --- |
| `GET /balance?userId=` | Баланс покупателя: `{ "amount": 100000 }`. Счёт создаётся при первом обращении. |
| `POST /payments` | Списать `{ "amount": 129, "userId": 1 }`. `200` и новый баланс этого покупателя, либо `402`, если списание ушло бы в минус. |
