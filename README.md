# Лабораторная работа №1 по дисциплине «Высокопроизводительные системы»

**Краков Кирилл Константинович P3431, Бабенко Даниил Александрович P3434**

## Bank Ticket System -- Система управления заявками на банковские продукты

### Общая концепция:

Система включает в себя несколько основных сущностей: пользователи, продукты, заявки, теги и связи между пользователями и продуктами. Пользователи делятся на обычных пользователей (клиентов), менеджеров и админов. Клиенты могут:

- регистрироваться и авторизовываться (пока через параметр запроса);
- создавать и просматривать продукты, к которым могут относиться как различные банковские услуги, например ипотечные и потребительские кредиты, так и какие-то небольшие товары при необходимости;
- добавлять новые заявки, в которых указан заявитель и продукт, который ему требуется (например, подача заявки определённым пользователем на определённый тип ипотечного кредита)
- добавлять и просматривать теги, которые нужны для классификации заявок («альфа-банк», «ипотека», «срочно» и т.д.)
- связываться с продуктами определённым образом (устанавлить роль относительно продукта) через сущность связи (самый наглядный пример -- определённый пользователь является владельцем определённого продукта (роль PRODUCT_OWNER), поэтому обладает большими правами на взаимодействие с ним).

Менеджеры могут изменять статус заявки (например, когда заявка начинает обрабатываться ставят ON_REVIEW, а когда заявка одобрена ставят APPROVED), админы же (изначально это 2 автора данной работы -- Краков Кирилл и Бабенко Даниил) могут свободно изменять, удалять, обновлять все сущности, а также изменять роли других пользователей (например, повышать пользователя до роли менеджера или понижать обратно).

У сущности заявки также есть дополнительные внутренние сущности:

- документы, прикреплённые к конкретным заявкам (например, PDF-файл с заполненным заявлением на получение ипотечного кредита), для которых указывается их название, формат и место хранения;
- история заявки, в которой подробно указано, как, когда и кем менялся статус заявки

### Основные сущности и связи между ними:

| **Сущность**           | **Поля**                                                                 | **Связи**                                                                  |
|:----------------------:|:------------------------------------------------------------------------:|:--------------------------------------------------------------------------:|
| User                   | id, username, email, password, role (UserRole), createdAt, updatedAt     | OneToMany → Application<br>ManyToMany → Product (userProductAssignment)    |
| Product                | id, name, description                                                    | OneToMany → Application<br>ManyToMany → User (userProductAssignment)       |
| Application            | id, applicantId, productId, status (ApplicationStatus), createdAt, updatedAt, documents, history, tags | ManyToOne → User (applicantId);<br>ManyToOne → Product (productId);<br>OneToMany → Document<br>OneToMany → ApplicationHistory<br>ManyToMany → Tag (tags) |
| Document               | id, fileName, contentType, storagePath, application                      | ManyToOne → Application (application)                                      |
| Application History    | id, application, oldStatus, newStatus (оба - ApplicationStatus), changedBy (UserRole), changedAt | ManyToOne → Application (application)                                      |
| Tag                    | id, name, applications                                                   | ManyToMany → Application (applications)                                    |
| UserProduct Assignment | id, user, product, roleOnProduct (AssignmentRole), assignedAt            | ManyToOne → User (user);<br>ManyToOne → Product (product);                 |

### Перечисления (Enums):

- **UserRole**: `ROLE_CLIENT`, `ROLE_MANAGER`, `ROLE_ADMIN`
- **AssignmentRole**: `PRODUCT_OWNER`, `SUPPORT`, `RESELLER`, `VIEWER`
- **ApplicationStatus**: `DRAFT`, `SUBMITTED`, `IN_REVIEW`, `APPROVED`, `REJECTED`

### Структура базы данных:

Вот как выглядит визуализация сущностей с полями и связами между ними, представленная в виде базы данных. Именно такая структура БД используется в системе, именно с ней взаимодействует разработанная база данных:

![Диаграмма структуры базы данных](https://github.com/KirillKrakov/bank-ticket-system-lab-1/blob/master/src/main/resources/static/database_structure.png)

### Описание всех запросов к системе (endpoints):

Ниже представлено описание каждого HTTP-запроса у каждой сущности, с краткими пояснениями и указанием связанного с этим запросом метода в контроллере той или иной сущности. Для более подробной документации, в том числе с указанием возможных возвращаемых состояний, можно обратиться к интерактивной документации OpenApi 3 и пользовательскому интерфейсу Swagger.

#### 1. Пользователь: User (id, username, email, password, role, createdAt, updatedAt) + UserDto (id, username, email, password, role, createdAt)

- **Create**: `POST "/api/v1/users"` + UserDto (username, email, password) (в теле запроса) -- создание нового пользователя, соответствует методу register
- **ReadAll**: `GET "api/v1/users?page=0&size=20"` -- чтение всех пользователей с пагинацией (0 и 20 -- по умолчанию), соответствует методу list
- **Read**: `GET "/api/v1/users/{id}"` -- чтение конкретного пользователя, соответствует методу showUser
- **Update**: `PUT "/api/v1/users/{id}?actorId={adminId}"` + UserDto (username, email, password) (в теле запроса) -- обновление данных о конкретном пользователе (права только у админов), соответствует методу updateUser
- **Delete**: `DELETE "/api/v1/users/{id}?actorId={adminId}"` -- удаление конкретного пользователя вместе со связанными с ним объектами (права только у админов), соответствует методу deleteUser
- **PromoteToManager**: `PUT "/api/v1/users/{id}/promote-manager?actorId={adminId}"` -- повышение клиента до менеджера (права только у админов), соответствует методу promoteManager
- **DemoteFromManager**: `PUT "/api/v1/users/{id}/demote-manager?actorId={adminId}"` -- понижение менеджера обратно до клиента (права только у админов), соответствует методу demoteManager

#### 2. Продукт: Product (id, name, description) + ProductDto (id, name, description)

- **Create**: `POST "/api/v1/products"` + ProductDto (name, description) (в теле запроса) -- создание нового продукта, соответствует методу create
- **ReadAll**: `GET "api/v1/products?page=0&size=20"` -- чтение всех продуктов с пагинацией (0 и 20 -- по умолчанию), соответствует методу list
- **Read**: `GET "/api/v1/products/{id}"` -- чтение конкретного продукта, соответствует методу get
- **Update**: `PUT "/api/v1/products/{id}?actorId={adminOrOwnerId}"` + ProductDto (name, description) (в теле запроса) -- обновление данных о конкретном продукте (права только у админов и владельцев продукта), соответствует методу updateProduct
- **Delete**: `DELETE "/api/v1/products/{id}?actorId={adminOrOwnerId}"` -- удаление конкретного продукта вместе со связанными с ним объектами (права только у админов и владельцев продукта), соответствует методу deleteProduct

#### 3. Заявка: Application (id, applicantId, productId, status, createdAt, updatedAt, documents, history, tags) + ApplicationDto (id, applicantId, productId, status, createdAt, documents, history, tags)

**Документ заявки**: Document (id, fileName, contentType, storagePath, application) + DocumentDto (id, fileName, contentType, storagePath)

**История заявки**: ApplicationHistory (id, application, oldStatus, newStatus, changedBy, changedAt) + ApplicationHistoryDto (id, application, oldStatus, newStatus, changedBy, changedAt)

- **Create**: `POST "/api/v1/applications"` + ApplicationDto (applicantId, productId, documents (fileName, contentType, storagePath), tags ([name,..])) (в теле запроса) -- создание новой заявки с указанием документа, истории и тегов, связанных с этой заявкой, соответствует методу create
- **ReadAll**: `GET "/api/v1/applications?page=0&size=20"` -- чтение всех заявок с пагинацией (0 и 20 -- по умолчанию), соответствует методу list
- **Read**: `GET "/api/v1/applications/{id}"` -- чтение конкретной заявки, соответствует методу get
- **ReadAllByStream**: `GET "/api/v1/applications/stream?cursor=<base64>&limit=20"` -- чтение всех заявок в виде бесконечной прокрутки, соответствует методу stream
- **Update(addTags)**: `PUT "/api/v1/applications/{id}/tags?actorId={applicantOrManagerId}"` + List\<String\> tags (в теле запроса) -- добавление определённых тегов, связанных с конкретной заявкой (права только у заявителя, админов и менеджеров), соответствует методу addTags
- **Delete(deleteTags)**: `DELETE "/api/v1/applications/{id}/tags?actorId={applicantOrManagerId}"` + List\<String\> tags (в теле запроса) -- удаление определённых тегов, связанных с конкретной (права только у заявителя, админов и менеджеров), соответствует методу removeTags
- **Update(changeStatus)**: `PUT "/api/v1/applications/{id}/status?actorId={actorId}"` + String status (в теле запроса) -- обновление статуса конкретной заявки (права только у админов и менеджеров), соответствует методу changeStatus
- **Delete**: `DELETE "/api/v1/applications/{id}?actorId={actorId}"` -- удаление конкретной заявки (права только у админов), соответствует методу deleteApplication
- **ReadHistory**: `GET "/api/v1/applications/{id}/history?actorId={actorId}"` -- чтение истории изменений статуса конкретной заявки (права только у заявителя, админов и менеджеров), соответствует методу getHistory

#### 4. Тег: Tag (id, name, applications) + TagDto (id, name, applications)

- **Create**: `POST "/api/v1/tags"` + TagDto (name) (в теле запроса) -- создание нового уникального тега, соответствует методу create
- **ReadAll**: `GET "/api/v1/tags"` -- чтение всех тегов с пагинацией (0 и 20 -- по умолчанию), соответствует методу list
- **Read**: `GET "/api/v1/tags/{name}/applications"` -- чтение конкретного тега с выводом всех заявок с таким тегом, соответствует методу getTagWithApplications
- **Update/Delete**: смотри attachTags и removeTags в ApplicationController

#### 5. UserProductAssignment (id, user, product, roleOnProduct, assignedAt) + UserProductAssignmentDto (id, user, product, role, assignedAt)

- **Create**: `POST "/api/v1/assignments"` + UserProductAssignmentDto (userId, productId, role) (в теле запроса) -- создание новой связи между пользователем и продуктом с указанием того, какую роль занимает пользователь относительно этого продукта (дополнительное поле в ManyToMany-связи, права только у админов и владельца продукта), соответствует методу assign
- **Read**: `GET "/api/v1/assignments?userId={?}&productId={?}"` -- чтение всех связей между указанными пользователем и продуктом, соответствует методу list
- **Delete**: `DELETE "/api/v1/assignments?actorId={?}&userId={?}&productId={?}"` -- удаление всех связей между указанными пользователем и продуктом, соответствует методу deleteAssignments

### Полезные команды терминала и ссылки для тестирования и анализа системы:

```bash
docker-compose down -v  # очистка всех томов docker-контейнера, т.е. удаление всех записей из БД
mvn -DskipTests package  # сборка jar-файла
docker-compose up --build  # запуск приложения
mvn -Dtest=*ServiceTest test  # запуск всех unit-тестов
mvn -Dtest=*IntegrationTest test  # запуск всех интеграционных тестов
mvn clean test jacoco:report  # запуск всех тестов для формирования отчёта о покрытии кода
```

### Ссылки для тестирования:

- http://localhost:8080/v3/api-docs — OpenAPI спецификация (JSON)
- http://localhost:8080/swagger-ui/index.html — Swagger UI (интерактивный интерфейс)

Для ручной проверки запросов использовался Postman (в разделе Headers добавлен заголовок: `Content-Type = application/json`. Также можно создавать переменные в разделе Variables, например `adminId`, `userId` и т. д.)

## Проверка системы на соответствие требованиям лабораторной работы №1

- ✅ Система представляет собой монолит с использованием Spring Boot  
- ✅ Написана на разрешённом языке Java  
- ✅ Использована разрешённая система сборки Maven  
- ✅ Использовались стабильные версии фреймворков, библиотек и языков  
- ✅ Следовали принципам разработки ПО: **SOLID**, **DRY**, **KISS**  
- ✅ При разработке использовали **git feature branching strategy**, имена коммитов — **conventional commits**  
- ✅ Код покрыт модульными и интеграционными тестами с помощью **testcontainers** и **junit-jupiter-api**  
  - Общий процент покрытия кода тестами: **86%**  
- ✅ Все основные сущности имеют осмысленный **CRUD интерфейс** с использованием **REST API**  
- ✅ Использованы правильные HTTP статусы: `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`  
- ✅ Для запросов к БД используется **Spring Data JPA**  
- ✅ Реализована валидация полей на уровне **контроллера** и на уровне **Entity**  
- ✅ Структура БД создаётся через **Liquibase миграции**  
- ✅ Для проверки бизнес-логики написаны интеграционные тесты на **testcontainers** + **junit-jupiter-api**  
- ✅ Используются переменные среды для конфигурации приложения  
- ✅ Используется **PostgreSQL**, запущенная через **Docker**  
- ✅ Каждый `findAll` имеет пагинацию — нельзя отдавать более **50 записей** за один запрос  
- ✅ Реализован запрос, который возвращает `findAll` в виде **бесконечной прокрутки** (без total count)  
- ✅ Реализован запрос, который возвращает `findAll` с **пагинацией** и указанием **общего количества записей в HTTP header**  
- ✅ На сложных запросах используются **транзакции** (минимум 2+ примеров)  
- ✅ Разделены модели **Entity** и **DTO**  
- ✅ Приложение имеет чистый код и архитектуру с разделением по:
  - сервисам  
  - репозиториям  
  - контроллерам  
  - моделям  
- ✅ Все `enum` значения в БД сериализуются как **строки**  
- ✅ Исключения (Exception) из контроллеров обрабатываются — отдаётся человеко-читаемая ошибка в теле ответа  
- ✅ Архитектура БД согласована с преподавателем  
- ✅ Реализованы связи между сущностями каждого типа:
  - **Many-to-Many**
  - **One-to-Many / Many-to-One**
  - **Many-to-Many с дополнительным полем**
- ✅ Добавить интерактивную документацию с помощью OpenApi 3, развернуть Swagger и дальше его поддерживать (также выполнено, ссылки для взаимодействия указаны в прошлом разделе)
