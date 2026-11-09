# Витрина магазина

Витрина: каталог, корзина и заказ из корзины.

## Стек

- Java 21
- Spring Boot
- Spring WebFlux
- Thymeleaf
- Spring Data R2DBC
- H2 в памяти через r2dbc-h2
- Maven
- JUnit 5
- Spring Boot Test и WebTestClient
- встроенный Netty
- исполняемый JAR
- Docker

## IDE

Откройте проект как Maven-проект и запустите `MyMarketAppApplication`.

## Тесты

```bash
./mvnw test
```

## JAR

```bash
./mvnw -DskipTests package
java -jar target/my-market-app-0.0.1-SNAPSHOT.jar
```

## Docker

Образ сам собирает JAR.

```bash
docker build -t my-market-app .
docker run --rm -p 8080:8080 my-market-app
```

Приложение: http://localhost:8080

## Эндпоинты

| Метод и путь | Что делает |
| --- | --- |
| `GET /`, `GET /items` | Каталог: `search`, `sort` (`NO`, `ALPHA`, `PRICE`), `pageNumber`, `pageSize`. |
| `POST /items` | Количество в каталоге: `PLUS`, `MINUS`. |
| `GET /items/{id}` | Карточка товара. |
| `POST /items/{id}` | Количество на карточке: `PLUS`, `MINUS`. |
| `GET /cart/items` | Корзина. |
| `POST /cart/items` | Строка корзины: `PLUS`, `MINUS`, `DELETE`. |
| `GET /orders` | Список заказов. |
| `GET /orders/{id}` | Один заказ. |
| `POST /buy` | Оформить заказ из корзины. |
