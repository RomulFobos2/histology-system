# CODESTYLE.md — Правила написания кода для АИС ГИ

> Читать при **любом написании кода** вместе с `CLAUDE.md`.
> При создании новой сущности с нуля дополнительно читать `TEMPLATES.md`.

---

## Архитектура

```
Controller → Service → Repository → Entity (JPA)
                         ↕
                    DTO + Mapper (MapStruct)
```

- **Controller** — тонкий: приём `@RequestParam`, вызов сервиса, формирование `Model`, return шаблон или `redirect:`
- **Service** — ВСЯ логика: проверки уникальности, зависимостей, `@Transactional`, `log.*`, try-catch
- **Repository** — только данные: `JpaRepository`, `@Query` JPQL
- **Entity** — JPA-сущность, `@Transient` для вычисляемых полей
- **DTO** — плоское представление, класс `@Data` (не record)
- **Mapper** — MapStruct, `Mappers.getMapper()` (без Spring DI)

---

## Порядок добавления новой сущности

1. Entity → `models/`
2. Repository → `repo/`
3. DTO → `dto/`
4. Mapper → `mapper/`
5. Enum → `enumeration/` (если нужен)
6. Service → `service/employee/{role}/`
7. Controller → `controllers/employee/{role}/`
8. HTML → `templates/employee/{role}/{entities}/` (all, add, edit, details)
9. Навигация → `header.html`
10. SQL seed-данные при необходимости

---

## Правила по слоям

### Entity
- Аннотации: `@Data`, `@NoArgsConstructor`, `@Entity`, `@Table(name="t_...")`, `@EqualsAndHashCode(of="id")`
- `@ToString.Exclude` на ВСЕХ коллекциях и обратных ManyToOne-ссылках
- Javadoc на русском для каждого поля
- Секции: `// ========== ПОЛЯ ==========`, `// ========== КОНСТРУКТОРЫ ==========`, `// ========== МЕТОДЫ ==========`
- Таблица: префикс `t_` + camelCase: `t_product`, `t_testSession`

### Repository
- `extends JpaRepository<Entity, Long>`
- Методы уникальности: `existsByField(val)`, `existsByFieldAndIdNot(val, id)`

### DTO
- Класс с `@Data`, **не** record
- Все вложенные поля разворачивать: `category.id` → `categoryId`, `category.name` → `categoryName`

### Mapper
- Статический `INSTANCE = Mappers.getMapper(...)`, без `componentModel`
- `toDTO()` + `toDTOList()` обязательно
- `@Mapping(source="related.id", target="relatedId")` для вложенных полей
- `@Named` + `default`-метод для вычисляемых полей

### Service
- Аннотации: `@Service`, `@Getter`, `@Slf4j`, конструктор-инъекция
- Возврат: `Optional<Long>` для save/edit, `boolean` для delete
- **Никогда не бросать Exception** — только `return false` / `Optional.empty()`
- Каждый `@Transactional`-метод: try-catch + `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`
- Перед delete — проверять зависимости (`countBy...`)
- Одноимённые бины: `@Service("roleEntityNameService")`

### Controller
- `@Controller` (не `@RestController` для HTML), `@Slf4j`, конструктор-инъекция
- `@RequestParam` с префиксом `input`: `inputName`, `inputCategoryId`, `inputFileField`
- `@PathVariable(value = "id") long id` — всегда с `value = "id"`
- Redirect: → `detailsEntity/{id}` при успехе, → форма при ошибке, → `allEntities` при отсутствии id
- AJAX check-endpoint: `ResponseEntity<Map<String, Boolean>>` с ключом `"exists"`
- Одноимённые бины: `@Controller("roleEntityNameController")`

### Enum
- `@Getter`, `displayName` на русском, UPPER_SNAKE_CASE значения
- Пример: `NEW("Новый")`, `IN_PROGRESS("В работе")`

---

## Именование

| Элемент | Шаблон | Пример |
|---|---|---|
| Entity | PascalCase | `TestSession` |
| DTO | `{Entity}DTO` | `TestSessionDTO` |
| Mapper | `{Entity}Mapper` | `TestSessionMapper` |
| Repository | `{Entity}Repository` | `TestSessionRepository` |
| Service | `{Entity}Service` | `TestSessionService` |
| Controller | `{Entity}Controller` | `TestSessionController` |
| Таблица БД | `t_` + camelCase | `t_testSession` |
| URL | `/employee/{role}/{entities}/{action}{Entity}` | `/employee/chief/tests/addTest` |
| HTML-файлы | `all/add/edit/details + Entity` | `allTests.html` |
| Model списки | `all{Entities}` | `allTests` |
| Model запись | `{entity}DTO` | `testDTO` |
| Model ошибка | `{entity}Error`, `deleteError` | `testError` |

---

## HTML / Thymeleaf

### Структура каждой страницы
```
header (th:insert) → container.my-5 → profile-card → footer (th:insert)
```

### Список (allEntities)
- Панель: `profile-card mb-4` → `profile-actions` → кнопки «Главная» и «Добавить»
- Таблица: `profile-card p-4` → `table table-modern`
- Нумерация: `th:each="item, index"` → `${index.index + 1}`
- Ключевое поле — ссылка на details

### Форма add/edit
- Layout: `container my-5` → `row justify-content-center` → `col-12 col-lg-10` → `profile-card p-4`
- Форма: `class="auth-form"`, `method="post"`
- Заголовок: `h1.page-title` + `p.page-subtitle`
- Секции: `h6.form-section-title` + `div.row.g-3`
- Инпуты: `form-control`, `name="inputField"`, `required`, `placeholder`, `maxlength`
- Кнопка: `btn btn-success btn-profile`
- Ошибка: `div.alert.alert-danger` с `th:if="${entityError}"`
- Ссылка назад: `div.link-section.mt-3`
- edit: `th:action` с id, поля с `th:value`, кнопка «Сохранить изменения»

### Просмотр (details)
- Read-only поля: `input.form-control.info-display` + `disabled`
- Labels: `label.form-label.text-muted`
- Связанные таблицы: `table table-sm table-striped` с `th:if="${list != null and !list.isEmpty()}"`
- Кнопки: `action-buttons mb-4` → «Редактировать» (primary) + «Удалить» (danger) с `confirmDelete()`

### Фрагменты
- Дата: `th:text="${#temporals.format(date, 'dd.MM.yyyy')}"`
- Enum: `th:text="${entity.getStatus().getDisplayName()}"`
- AJAX-валидация уникальных полей — обязательна (шаблон в `TEMPLATES.md`)
- `confirmDelete()` — обязателен перед удалением (шаблон в `TEMPLATES.md`)

---

## CSS-классы (не менять схему)

| Класс | Назначение |
|---|---|
| `profile-card` | Белая карточка, border-radius 15px, тень |
| `page-title`, `page-subtitle` | Заголовок страницы |
| `auth-form` | Форма добавления/редактирования |
| `form-section-title` | Заголовок секции формы |
| `table-modern` | Таблица с градиентным заголовком |
| `info-display` | Read-only поле на details |
| `profile-actions` | Панель кнопок на списке |
| `action-buttons` | Кнопки действий на details |
| `link-section` | Ссылка «← К списку» |
| `btn-profile` | Модификатор кнопок Bootstrap |

Градиент: `linear-gradient(135deg, #667eea 0%, #764ba2 100%)`

---

## Статические ресурсы — только локально, никаких CDN

**Запрещено** в HTML-шаблонах любые внешние URL: `cdn.`, `unpkg.`, `jsdelivr.`, `cdnjs.`, `stackpath.` и т.п.

**Правильная структура static:**
```
static/
├── css/bootstrap.min.css
├── css/bootstrap-icons.min.css
├── css/style.css
├── js/bootstrap.bundle.min.js
└── vendor/{lib}/{lib}.min.css|js    ← для новых библиотек
```

**Подключение только так:**
```html
<link href="/css/bootstrap.min.css" rel="stylesheet">
<script src="/js/bootstrap.bundle.min.js"></script>
```

**Нужна новая библиотека?** → сначала укажи файлы для скачивания и папку, потом пиши HTML с локальным путём. CDN не использовать даже временно.

---

## Логирование

- `@Slf4j` в каждом сервисе
- `log.info()` — успешные операции: `log.info("Сохранено: id={}", entity.getId())`
- `log.error()` — все ошибки: `log.error("Не найдено: id={}", id)`
- `log.debug()` — детали выполнения

---

## Жёсткие ограничения

### Запрещено
1. `@Autowired` — только конструктор
2. `record` для DTO
3. Бросать Exception из Service
4. `@RestController` для HTML-страниц
5. Spring `componentModel` в MapStruct
6. jQuery — только vanilla JS
7. Подпакеты внутри `dto/`, `mapper/`, `models/`, `repo/`
8. `th:object` + `th:field`
9. Менять цветовую схему CSS
10. Внешние CDN-ссылки в HTML

### Обязательно
1. `@ToString.Exclude` на коллекциях и обратных ссылках в Entity
2. `@EqualsAndHashCode(of = "id")` в Entity
3. Javadoc на русском в Entity
4. Префикс `t_` у всех таблиц
5. `@PathVariable(value = "id") long id`
6. Redirect после POST: details при успехе, форма при ошибке
7. AJAX-проверка уникальных полей
8. `confirmDelete()` перед удалением
9. `try-catch` + `setRollbackOnly()` в `@Transactional`-методах
10. Все CSS/JS — локально, самопроверка перед финальным ответом