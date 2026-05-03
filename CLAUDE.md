# АИС ГИ — Автоматизированная информационная система гистологических исследований в судебно-медицинской экспертизе

Система предназначена для автоматизации регистрации, обработки и анализа гистологических исследований в государственном бюджетном учреждении «Бюро судебно-медицинской экспертизы города Байконур» (ГБУ «БСМЭ»). Реализует ведение базы образцов, электронный журнал, архив микроскопических изображений, улучшение качества изображений свёрточным автоэнкодером (Python-микросервис), формирование заключений и протоколов, статистические отчёты.

> **Мелкие правки / баг-фикс:** читай `CODESTYLE.md`
> **Новая сущность с нуля:** читай `CODESTYLE.md` + `TEMPLATES.md`
> **Планирование / статус этапов:** читай `PLAN.md`

---

## Стек технологий

- **Java 21**, **Spring Boot** (последняя стабильная)
- **Spring Security 6** — BCrypt, роли через GrantedAuthority
- **Spring Data JPA** / Hibernate — MySQL
- **Thymeleaf** — серверный рендеринг + Bootstrap 5 (без jQuery)
- **MapStruct** — маппинг Entity ↔ DTO (без Spring componentModel)
- **Lombok** — `@Data`, `@Slf4j`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@Getter`
- **Apache POI** — Excel-отчёты
- **Maven** (Maven Wrapper)
- **Python (Flask/FastAPI)** — микросервис свёрточного автоэнкодера для улучшения изображений

```bash
mvnw.cmd clean package
mvnw.cmd spring-boot:run
```

`application.properties` — в `.gitignore`, настройки БД задаются локально.

---

## Структура проекта

```
src/main/java/ru/mai/histology/
├── HistologySystemApplication.java
├── component/                    — DataInitializer (CommandLineRunner)
├── controllers/
│   ├── general/                  — MainController, CustomErrorController
│   └── employee/
│       ├── admin/                — ROLE_EMPLOYEE_ADMIN
│       ├── head/                 — ROLE_EMPLOYEE_HEAD
│       ├── histologist/          — ROLE_EMPLOYEE_HISTOLOGIST
│       ├── laborant/             — ROLE_EMPLOYEE_LABORANT
│       └── general/              — общие для нескольких ролей
├── dto/                          — все DTO (плоский пакет, без подпакетов)
├── enumeration/                  — все enum (плоский пакет)
├── mapper/                       — все MapStruct-маперы (плоский пакет)
├── models/                       — все JPA-сущности (плоский пакет)
├── repo/                         — все репозитории (плоский пакет)
├── security/                     — SecurityConfig, PasswordEncoderConfig
└── service/
    ├── employee/admin/, head/, histologist/, laborant/
    └── general/                  — FileStorageService, AutoencoderClientService

src/main/resources/
├── logback.xml
├── static/css/, static/js/
└── templates/
    ├── blocks/header.html, footer.html
    ├── general/                  — login, index, error
    └── employee/
        ├── admin/users/
        ├── head/conclusions/, head/reports/, head/oversight/
        ├── histologist/samples/, histologist/images/, histologist/conclusions/, histologist/protocols/
        └── laborant/cases/, laborant/samples/, laborant/images/, laborant/journal/

autoencoder/                      — Python-микросервис (Flask/FastAPI)
├── app.py                        — REST API (обучение, инференс)
├── model/                        — архитектура автоэнкодера
├── data/                         — датасеты для обучения
├── weights/                      — сохранённые веса моделей
└── requirements.txt
```

---

## Роли и доступ

| Роль в ТЗ | Spring Security | URL-префикс | Доступ |
|---|---|---|---|
| Администратор системы | `ROLE_EMPLOYEE_ADMIN` | `/employee/admin/` | Управление пользователями |
| Начальник БСМЭ | `ROLE_EMPLOYEE_HEAD` | `/employee/head/` | Отчёты, надзор, судебно-медицинские заключения |
| Врач-гистолог | `ROLE_EMPLOYEE_HISTOLOGIST` | `/employee/histologist/` | Анализ образцов, заключения гистолога, протоколы, изображения, автоэнкодер |
| Лаборант | `ROLE_EMPLOYEE_LABORANT` | `/employee/laborant/` | Регистрация дел и образцов, загрузка изображений, электронный журнал |

`Employee` реализует `UserDetails`. `Role` реализует `GrantedAuthority` (JPA-сущность, таблица `t_role`). Начальные роли и admin создаются при старте через `DataInitializer`. Деактивированный пользователь (`isActive=false`) не может войти.

---

## Доменная модель

| Блок | Сущности |
|---|---|
| Пользователи | `Employee`, `Role` (JPA-сущность, GrantedAuthority), `Department` |
| Дела и образцы | `ForensicCase`, `Sample`, `TissueType` (enum), `StainingMethod` (enum), `SampleStatus` (enum), `CaseStatus` (enum) |
| Изображения | `MicroscopeImage` |
| Заключения | `HistologistConclusion`, `ForensicConclusion` |
| Протоколы | `ResearchProtocol` |
| Автоэнкодер | `AutoencoderModel`, `ImageProcessingLog` |

### Ключевые поля сущностей

**Employee** `t_employee` — lastName, firstName, middleName, birthDate, position, username *(unique)*, password *(BCrypt)*, isActive, needChangePassword → ManyToOne Role, ManyToOne Department

**Role** `t_role` — реализует GrantedAuthority. name *(unique, например "ROLE_EMPLOYEE_ADMIN")*, description *(например "Администратор")*

**Department** `t_department` — name *(unique)*, description

**ForensicCase** `t_forensicCase` — caseNumber *(unique)*, receiptDate, description, status *(CaseStatus)* → ManyToOne Employee (responsibleExpert) → OneToMany Sample

**Sample** `t_sample` — sampleNumber, receiptDate, tissueType *(TissueType)*, stainingMethod *(StainingMethod)*, status *(SampleStatus)*, notes → ManyToOne ForensicCase, ManyToOne Employee (registeredBy), ManyToOne Employee (assignedHistologist) → OneToMany MicroscopeImage. Unique constraint: (forensicCase + sampleNumber)

**MicroscopeImage** `t_microscopeImage` — originalFilename, storedFilename, filePath, fileSize, contentType, uploadDate, description, isEnhanced, magnification → ManyToOne Sample, ManyToOne Employee (uploadedBy), ManyToOne MicroscopeImage (originalImage, nullable — ссылка на оригинал для улучшенных копий)

**HistologistConclusion** `t_histologistConclusion` — microscopicDescription *(TEXT)*, diagnosis, conclusionText *(TEXT)*, conclusionDate → ManyToOne Sample, ManyToOne Employee (histologist)

**ForensicConclusion** `t_forensicConclusion` — conclusionText *(TEXT)*, conclusionDate, isFinal → ManyToOne Sample, ManyToOne Employee (head)

**ResearchProtocol** `t_researchProtocol` — protocolNumber *(unique)*, createdDate, protocolText *(TEXT)* → ManyToOne Sample, ManyToOne Employee (createdBy)

**AutoencoderModel** `t_autoencoderModel` — modelName, description, trainedDate, epochs, loss, validationLoss, isActive

**ImageProcessingLog** `t_imageProcessingLog` — processedDate, processingTimeMs → ManyToOne MicroscopeImage (original), ManyToOne MicroscopeImage (enhanced), ManyToOne AutoencoderModel, ManyToOne Employee (processedBy)

### Enum-ы

**TissueType** — `LIVER("Печень")`, `KIDNEY("Почка")`, `HEART("Сердце")`, `LUNG("Лёгкое")`, `BRAIN("Головной мозг")`, `SKIN("Кожа")`, `MUSCLE("Мышечная ткань")`, `BONE("Костная ткань")`, `SPLEEN("Селезёнка")`, `INTESTINE("Кишечник")`, `STOMACH("Желудок")`, `OTHER("Другое")`

**StainingMethod** — `HEMATOXYLIN_EOSIN("Гематоксилин-эозин")`, `VAN_GIESON("Ван Гизон")`, `PAS("ШИК-реакция")`, `MASSON_TRICHROME("Трихром Массона")`, `SUDAN("Судан III/IV")`, `NISSL("Ниссль")`, `GRAM("Грам")`, `ZIEHL_NEELSEN("Циль-Нильсен")`, `IMMUNOHISTOCHEMISTRY("Иммуногистохимия")`, `OTHER("Другое")`

**SampleStatus** — `NEW("Новый")`, `IN_PROGRESS("В работе")`, `AWAITING_ANALYSIS("Ожидает анализа")`, `ANALYZED("Проанализирован")`, `CONCLUDED("Заключение выдано")`, `ARCHIVED("Архивирован")`

**CaseStatus** — `OPEN("Открыто")`, `IN_PROGRESS("В работе")`, `CONCLUDED("Заключение выдано")`, `CLOSED("Закрыто")`

---

## URL-карта проекта

```
# Admin — пользователи
GET/POST /employee/admin/users/allUsers
GET/POST /employee/admin/users/addUser
GET      /employee/admin/users/detailsUser/{id}
GET/POST /employee/admin/users/editUser/{id}
GET      /employee/admin/users/deactivateUser/{id}
GET/POST /employee/admin/users/resetPassword/{id}
GET      /employee/admin/users/check-username

# Laborant — судебные дела
GET/POST /employee/laborant/cases/allCases
GET/POST /employee/laborant/cases/addCase
GET      /employee/laborant/cases/detailsCase/{id}
GET/POST /employee/laborant/cases/editCase/{id}
GET      /employee/laborant/cases/deleteCase/{id}
GET      /employee/laborant/cases/check-caseNumber

# Laborant — образцы
GET/POST /employee/laborant/samples/allSamples
GET/POST /employee/laborant/samples/addSample/{caseId}
GET      /employee/laborant/samples/detailsSample/{id}
GET/POST /employee/laborant/samples/editSample/{id}
GET      /employee/laborant/samples/deleteSample/{id}

# Laborant — изображения
GET      /employee/laborant/images/allImages/{sampleId}
GET/POST /employee/laborant/images/uploadImage/{sampleId}
GET      /employee/laborant/images/detailsImage/{id}
GET      /employee/laborant/images/deleteImage/{id}
GET      /employee/laborant/images/viewImage/{id}

# Laborant — электронный журнал
GET      /employee/laborant/journal/view
POST     /employee/laborant/journal/export

# Histologist — образцы (read-only)
GET      /employee/histologist/samples/allSamples
GET      /employee/histologist/samples/detailsSample/{id}

# Histologist — изображения
GET      /employee/histologist/images/allImages/{sampleId}
GET      /employee/histologist/images/detailsImage/{id}
GET      /employee/histologist/images/viewImage/{id}
POST     /employee/histologist/images/enhance/{id}
GET      /employee/histologist/images/compare/{originalId}/{enhancedId}

# Histologist — заключения гистолога
GET/POST /employee/histologist/conclusions/allConclusions
GET/POST /employee/histologist/conclusions/addConclusion/{sampleId}
GET      /employee/histologist/conclusions/detailsConclusion/{id}
GET/POST /employee/histologist/conclusions/editConclusion/{id}
GET      /employee/histologist/conclusions/deleteConclusion/{id}

# Histologist — протоколы исследования
GET      /employee/histologist/protocols/allProtocols
GET/POST /employee/histologist/protocols/generateProtocol/{sampleId}
GET      /employee/histologist/protocols/detailsProtocol/{id}
GET/POST /employee/histologist/protocols/editProtocol/{id}
POST     /employee/histologist/protocols/exportProtocol/{id}

# Head — судебно-медицинские заключения
GET/POST /employee/head/conclusions/allConclusions
GET/POST /employee/head/conclusions/addConclusion/{sampleId}
GET      /employee/head/conclusions/detailsConclusion/{id}
GET/POST /employee/head/conclusions/editConclusion/{id}

# Head — надзор (read-only)
GET      /employee/head/oversight/allCases
GET      /employee/head/oversight/detailsCase/{id}
GET      /employee/head/oversight/allSamples
GET      /employee/head/oversight/detailsSample/{id}

# Head — отчёты и аналитика
GET      /employee/head/reports/dashboard
GET/POST /employee/head/reports/samplesReceived
GET/POST /employee/head/reports/completedStudies
GET/POST /employee/head/reports/stainingMethods
GET/POST /employee/head/reports/researchResults
GET/POST /employee/head/reports/imageProcessingStats

# General — профиль и смена пароля
GET      /employee/general/profile
GET/POST /employee/general/change-password
```

---

## Специфика проекта

### Управление файлами изображений
- Загрузка: multipart upload (форматы TIF, JPG), ограничение 50 МБ
- Хранение на диске: `uploads/images/{caseNumber}/{sampleNumber}/`
- `storedFilename` = UUID + расширение (избежание коллизий)
- Просмотр: полноэкранный viewer с зумом (vanilla JS)
- При удалении образца — удаление файлов с диска

### Свёрточный автоэнкодер (Python-микросервис)
- Отдельный Flask/FastAPI сервис в папке `autoencoder/`
- REST API: `POST /enhance` (принимает изображение, возвращает улучшенное)
- REST API: `POST /train` (запуск обучения на датасете)
- REST API: `GET /models` (список обученных моделей)
- Spring Boot вызывает через `RestTemplate` / `WebClient` в `AutoencoderClientService`
- Улучшенное изображение сохраняется как новый `MicroscopeImage` с `isEnhanced=true` и ссылкой на оригинал
- Каждая операция логируется в `ImageProcessingLog`
- Режим сравнения: side-by-side оригинал vs улучшенное (vanilla JS)

### Workflow жизненного цикла образца
- Образец движется по статусам `SampleStatus`: NEW → IN_PROGRESS → AWAITING_ANALYSIS → ANALYZED → CONCLUDED → ARCHIVED.
- При создании заключения гистологом → статус ANALYZED → CONCLUDED.

### Заключения с разграничением доступа
- **Заключение гистолога** (`HistologistConclusion`): создаёт врач-гистолог, привязано к образцу. Содержит микроскопическое описание, диагноз, текст заключения
- **Судебно-медицинское заключение** (`ForensicConclusion`): создаёт начальник БСМЭ, привязано к образцу. Поле `isFinal` — отметка финальности
- Каждая роль видит и редактирует только свои заключения

### Общие бизнес-правила (для всех этапов)
- **Отчество** не является обязательным полем. В БД хранить пустую строку `""` вместо `null`. В контроллере учитывать, что поле может прийти незаполненным.
- **Сброс пароля** администратором устанавливает флаг `needChangePassword=true`. При первом входе пользователь перенаправляется на страницу смены пароля.
- **Деактивация**: свою учётную запись нельзя деактивировать. Учётные записи с ролью `ROLE_EMPLOYEE_ADMIN` нельзя деактивировать.
- **Авторизация**: все пользователи перенаправляются на главную страницу `/` (без перенаправления по роли).
- **Department** не используется в этом проекте (удалён).

### Деактивация вместо удаления пользователей
- Не удалять, только `isActive = false`
- Кнопка: «Деактивировать», не «Удалить»

### Excel-отчёты (Apache POI)
- **Отчёт о поступивших образцах** — количество образцов за выбранный период, группировка по типу ткани
- **Отчёт о выполненных исследованиях** — завершённые исследования за период, статистика по экспертам
- **Отчёт о методах окрашивания** — частота использования каждого метода
- **Отчёт о результатах исследований** — диагнозы и заключения за период
- **Отчёт об обработке изображений** — статистика автоэнкодера: количество обработанных, среднее время, модели

### Электронный журнал
- Сводная таблица: строка = образец, столбцы = номер дела, номер образца, дата поступления, тип ткани, метод окрашивания, стадия, эксперт, статус
- Фильтрация по всем полям
- Экспорт в Excel

### Глоссарий
| Термин | Значение |
|---|---|
| БСМЭ | Бюро судебно-медицинской экспертизы |
| ГБУ | Государственное бюджетное учреждение |
| СМЭ | Судебно-медицинская экспертиза |
| Гистология | Наука о тканях организма |
| Образец (проба) | Фрагмент ткани для гистологического исследования |
| Фиксация | Сохранение структуры ткани химическими средствами (формалин) |
| Заливка | Пропитка ткани парафином для изготовления срезов |
| Микротомия | Изготовление тонких срезов ткани на микротоме |
| Окрашивание | Обработка срезов красителями для визуализации структур |
| Гематоксилин-эозин | Основной метод окрашивания в гистологии |
| Автоэнкодер | Нейросеть для сжатия и восстановления данных |
| Свёрточный автоэнкодер | Автоэнкодер на основе свёрточных слоёв для обработки изображений |
| Протокол исследования | Документ с описанием методов и результатов гистологического исследования |
| Заключение гистолога | Результат микроскопического анализа ткани врачом-гистологом |
| Судебно-медицинское заключение | Итоговый документ начальника БСМЭ по результатам экспертизы |

---

## Git

- **Никогда** не работай в `main`/`master`/`develop`
- Ветка: `git checkout -b claude/{описание}`
- Коммиты: `feat: ...` / `fix: ...`
- Каждый запрос от пользователя с изменением кода делается в новом PR
- **Не пушить** без явного разрешения пользователя
