# PLAN.md — План разработки АИС ГИ

> Читай вместе с `CLAUDE.md`. При написании кода дополнительно читай `CODESTYLE.md`.

---

## Статус этапов

| Этап | Название | Статус |
|---|---|---|
| 0 | Фундамент (Security, Employee, UI-блоки) | 🔲 Не начат |
| 1 | Управление пользователями (Admin) | 🔲 Не начат |
| 2 | Судебные дела и образцы (Laborant) | 🔲 Не начат |
| 3 | Управление изображениями (Laborant + Histologist) | 🔲 Не начат |
| 4 | Заключения гистолога и протоколы (Histologist) | 🔲 Не начат |
| 5 | Судебно-медицинские заключения (Head) | 🔲 Не начат |
| 6 | Автоэнкодер — Python-микросервис + интеграция | 🔲 Не начат |
| 7 | Отчёты и аналитический дашборд (Head) | 🔲 Не начат |
| 8 | Электронный журнал и финальная полировка | 🔲 Не начат |

---

## ЭТАП 0: Фундамент

**Цель:** скелет приложения — безопасность, начальные данные, UI-блоки.

### 0.1 Инициализация Maven-проекта
- [ ] Добавить зависимости в pom.xml: Spring Web, Security, JPA, Thymeleaf, MySQL, Lombok, MapStruct, POI
- [ ] Настроить `application.properties`: datasource, JPA DDL, Thymeleaf, multipart upload
- [ ] Создать структуру пакетов (см. CLAUDE.md → Структура проекта)

### 0.2 Сущность Employee + Role + Department + Security
```
models/Employee.java          — реализует UserDetails, поля: lastName, firstName, middleName, birthDate, position, username, password, isActive, needChangePassword → ManyToOne Role, ManyToOne Department
models/Role.java              — реализует GrantedAuthority, поля: name, description
models/Department.java        — поля: name, description
repo/EmployeeRepository.java, repo/RoleRepository.java, repo/DepartmentRepository.java
dto/EmployeeDTO.java, dto/RoleDTO.java, dto/DepartmentDTO.java
mapper/EmployeeMapper.java, mapper/RoleMapper.java, mapper/DepartmentMapper.java
security/SecurityConfigEmployee.java    — настройка URL и ролей (4 роли)
security/PasswordEncoderConfig.java     — BCrypt
security/AuthenticationLoggingSuccessHandler.java, AuthenticationLoggingFailureHandler.java
component/DataInitializer.java          — создаёт 4 роли + admin + дефолтный отдел при первом старте
```

Роли в DataInitializer:
- `ROLE_EMPLOYEE_ADMIN` — «Администратор»
- `ROLE_EMPLOYEE_HEAD` — «Начальник БСМЭ»
- `ROLE_EMPLOYEE_HISTOLOGIST` — «Врач-гистолог»
- `ROLE_EMPLOYEE_LABORANT` — «Лаборант»

### 0.3 UI-блоки
- [ ] `templates/blocks/header.html` — боковая навигация с `sec:authorize` по ролям
- [ ] `templates/blocks/footer.html`
- [ ] `static/css/style.css` — полный файл стилей (profile-card, table-modern, page-title и т.д.)
- [ ] `static/css/bootstrap.min.css`, `static/css/bootstrap-icons.min.css` — локальные файлы
- [ ] `static/js/bootstrap.bundle.min.js`, `static/js/javascript.js`
- [ ] `templates/employee/general/login.html`
- [ ] `templates/general/home.html` — главная страница
- [ ] `templates/errors/` — 403, 404, 405, error
- [ ] `controllers/general/MainController.java`, `CustomErrorController.java`

### Проверка этапа 0
- [ ] Логин/логаут работают
- [ ] Каждая роль видит только свои разделы в меню
- [ ] 403 при попытке зайти не в свой раздел
- [ ] Admin создаётся при первом запуске

---

## ЭТАП 1: Управление пользователями (Admin)

**Цель:** администратор управляет учётными записями и отделами.

### Файлы
```
service/employee/EmployeeService.java   — implements UserDetailsService
service/employee/admin/DepartmentService.java
controllers/employee/admin/UserController.java
controllers/employee/admin/DepartmentController.java
controllers/employee/general/MainEmployeeController.java
templates/employee/admin/users/
    allUsers.html           — таблица + фильтр по роли
    addUser.html            — ФИО, дата рождения, должность, отдел, роль, логин
    editUser.html           — без смены пароля
    detailsUser.html        — карточка + кнопки Редактировать / Деактивировать / Сбросить пароль
    resetPassword.html      — форма сброса пароля
templates/employee/admin/departments/
    allDepartments.html
    addDepartment.html, editDepartment.html, detailsDepartment.html
templates/employee/general/
    profile.html            — профиль текущего пользователя
    change-password.html    — смена собственного пароля
```

### Особенности
- Удаление = деактивация (`isActive = false`), кнопка «Деактивировать»
- Сброс пароля — отдельная операция на отдельной странице
- AJAX-проверка уникальности username
- Принудительная смена пароля при первом входе (`needChangePassword`)
- AJAX-проверка уникальности названия отдела

### Методы EmployeeService
- `checkUserName(username)` / `checkUserNameExcluding(username, id)` — уникальность логина
- `saveUser(user, rawPassword)` — BCrypt при создании
- `editUser(id, ...)` — данные без пароля
- `resetPassword(id, newRawPassword)` — отдельная операция
- `deactivateUser(id)` — `isActive = false`
- `getAllUsers()`, `getAllByRole(role)`

---

## ЭТАП 2: Судебные дела и образцы (Laborant)

**Цель:** лаборант регистрирует судебные дела, добавляет гистологические образцы, продвигает стадии исследования.

### Файлы
```
models/ForensicCase.java
models/Sample.java
enumeration/TissueType.java
enumeration/StainingMethod.java
enumeration/ResearchStage.java
enumeration/SampleStatus.java
enumeration/CaseStatus.java
repo/ForensicCaseRepository.java, repo/SampleRepository.java
dto/ForensicCaseDTO.java, dto/SampleDTO.java
mapper/ForensicCaseMapper.java, mapper/SampleMapper.java

service/employee/laborant/ForensicCaseService.java
service/employee/laborant/SampleService.java
controllers/employee/laborant/ForensicCaseController.java
controllers/employee/laborant/SampleController.java
templates/employee/laborant/cases/
    allCases.html           — таблица дел + фильтры (статус, дата, номер)
    addCase.html            — номер дела, дата поступления, описание, ответственный эксперт
    editCase.html
    detailsCase.html        — карточка дела + список образцов + кнопка «Добавить образец»
templates/employee/laborant/samples/
    allSamples.html         — все образцы + фильтры (тип ткани, метод, стадия, статус, эксперт)
    addSample.html          — номер образца, тип ткани, метод окрашивания, назначенный гистолог
    editSample.html
    detailsSample.html      — карточка образца + стадия + кнопка «Продвинуть стадию» + список изображений
```

### Особенности
- AJAX-проверка уникальности номера дела
- Уникальность номера образца в пределах дела (unique constraint: forensicCase + sampleNumber)
- Кнопка «Продвинуть стадию» — `POST advanceStage/{id}`, последовательная смена: RECEIVED → FIXATION → ... → COMPLETED
- При переходе на MICROSCOPY → статус образца автоматически меняется на AWAITING_ANALYSIS
- Фильтрация по всем параметрам из ТЗ: дата, номер дела/образца, тип ткани, метод окрашивания, стадия, эксперт, статус

---

## ЭТАП 3: Управление изображениями (Laborant + Histologist)

**Цель:** лаборант загружает микроскопические изображения, гистолог просматривает.

### Файлы
```
models/MicroscopeImage.java
repo/MicroscopeImageRepository.java
dto/MicroscopeImageDTO.java
mapper/MicroscopeImageMapper.java

service/general/FileStorageService.java             — загрузка/удаление файлов на диске
service/employee/laborant/ImageUploadService.java   — бизнес-логика загрузки
controllers/employee/laborant/ImageController.java  — upload, delete, view
templates/employee/laborant/images/
    allImages.html          — галерея изображений образца (миниатюры)
    uploadImage.html        — форма загрузки (multipart, TIF/JPG)
    detailsImage.html       — метаданные + полноэкранный просмотр

service/employee/histologist/ImageViewService.java  — read-only доступ к изображениям
controllers/employee/histologist/ImageController.java
templates/employee/histologist/images/
    allImages.html          — галерея изображений образца
    detailsImage.html       — метаданные + просмотр + кнопка «Улучшить» (этап 6)
    viewImage.html          — полноэкранный viewer с зумом
```

### Особенности
- Multipart upload: форматы TIF, JPG, ограничение 50 МБ
- Хранение: `uploads/images/{caseNumber}/{sampleNumber}/{UUID}.{ext}`
- `FileStorageService` — сохранение/удаление физических файлов
- Миниатюры: масштабирование через `BufferedImage` (Java)
- Полноэкранный viewer: vanilla JS с зумом (scroll) и панорамированием (drag)
- При удалении образца (этап 2) — каскадное удаление файлов через `FileStorageService`

---

## ЭТАП 4: Заключения гистолога и протоколы (Histologist)

**Цель:** врач-гистолог просматривает назначенные образцы, пишет заключения, формирует протоколы исследования.

### Файлы
```
models/HistologistConclusion.java
models/ResearchProtocol.java
repo/HistologistConclusionRepository.java, repo/ResearchProtocolRepository.java
dto/HistologistConclusionDTO.java, dto/ResearchProtocolDTO.java
mapper/HistologistConclusionMapper.java, mapper/ResearchProtocolMapper.java

service/employee/histologist/SampleViewService.java         — read-only просмотр образцов
service/employee/histologist/ConclusionService.java
service/employee/histologist/ProtocolService.java
controllers/employee/histologist/SampleViewController.java  — read-only
controllers/employee/histologist/ConclusionController.java
controllers/employee/histologist/ProtocolController.java
templates/employee/histologist/samples/
    allSamples.html         — образцы, назначенные текущему гистологу + фильтры
    detailsSample.html      — карточка + изображения + заключение + протокол
templates/employee/histologist/conclusions/
    allConclusions.html     — список заключений текущего гистолога
    addConclusion.html      — микроскопическое описание, диагноз, текст заключения
    editConclusion.html
    detailsConclusion.html  — полное заключение + связанный образец
templates/employee/histologist/protocols/
    allProtocols.html       — список протоколов
    generateProtocol.html   — автогенерация из данных образца + заключения, редактирование текста
    detailsProtocol.html    — полный протокол
    editProtocol.html
```

### Особенности
- Заключение привязано к образцу (ManyToOne Sample)
- При создании заключения: статус образца → ANALYZED
- Протокол формируется автоматически из данных образца + заключения, затем редактируется вручную
- Номер протокола — auto-increment (следующий свободный номер)
- Экспорт протокола в Excel (Apache POI)
- Гистолог видит только образцы, назначенные на него (assignedHistologist == currentUser)

---

## ЭТАП 5: Судебно-медицинские заключения (Head)

**Цель:** начальник БСМЭ просматривает все дела/образцы и формирует итоговые судебно-медицинские заключения.

### Файлы
```
models/ForensicConclusion.java
repo/ForensicConclusionRepository.java
dto/ForensicConclusionDTO.java
mapper/ForensicConclusionMapper.java

service/employee/head/ForensicConclusionService.java
service/employee/head/OversightService.java             — read-only агрегация данных
controllers/employee/head/ForensicConclusionController.java
controllers/employee/head/OversightController.java
templates/employee/head/conclusions/
    allConclusions.html     — список судебно-медицинских заключений + фильтры
    addConclusion.html      — текст заключения, выбор образца, флаг isFinal
    editConclusion.html
    detailsConclusion.html  — полное заключение + данные образца + заключение гистолога
templates/employee/head/oversight/
    allCases.html           — все дела (read-only) + фильтры
    detailsCase.html        — дело + образцы + статусы
    allSamples.html         — все образцы (read-only) + фильтры
    detailsSample.html      — образец + изображения + заключения
```

### Особенности
- Head видит все дела и образцы (не только назначенные на него)
- При создании судебно-медицинского заключения с `isFinal=true`: статус образца → CONCLUDED, статус дела пересчитывается
- Заключение привязано к образцу (ManyToOne Sample)
- Начальник может видеть заключение гистолога, но не редактировать его

---

## ЭТАП 6: Автоэнкодер — Python-микросервис + интеграция

**Цель:** Python-микросервис для улучшения качества микроскопических изображений + интеграция со Spring Boot.

### Python-микросервис (папка `autoencoder/`)
```
autoencoder/
├── app.py                  — Flask/FastAPI REST API
├── model/
│   └── autoencoder.py      — архитектура свёрточного автоэнкодера (Encoder + Decoder)
├── train.py                — скрипт обучения
├── data/                   — датасеты
├── weights/                — сохранённые веса
├── requirements.txt        — torch, flask/fastapi, pillow, numpy
└── README.md               — инструкция запуска
```

REST API микросервиса:
- `POST /enhance` — принимает изображение (multipart), возвращает улучшенное (JPEG/PNG)
- `POST /train` — запуск обучения (параметры: epochs, batch_size, learning_rate)
- `GET /models` — список обученных моделей с метриками
- `GET /health` — проверка доступности

### Интеграция в Spring Boot
```
models/AutoencoderModel.java
models/ImageProcessingLog.java
repo/AutoencoderModelRepository.java, repo/ImageProcessingLogRepository.java
dto/AutoencoderModelDTO.java, dto/ImageProcessingLogDTO.java
mapper/AutoencoderModelMapper.java, mapper/ImageProcessingLogMapper.java

service/general/AutoencoderClientService.java       — REST-клиент к Python-сервису
controllers/employee/histologist/ImageController.java — добавить enhance/{id} и compare/{originalId}/{enhancedId}
templates/employee/histologist/images/
    enhance.html            — кнопка запуска + прогресс
    compare.html            — side-by-side: оригинал vs улучшенное
```

### Архитектура автоэнкодера
- Encoder: Conv2D → ReLU → MaxPool → Conv2D → ReLU → MaxPool (латентное пространство)
- Decoder: Conv2D → ReLU → Upsample → Conv2D → ReLU → Upsample (восстановление)
- Loss: MSE между входным и восстановленным изображением
- Данные: микроскопические изображения из архива системы

### Особенности
- `AutoencoderClientService` использует `RestTemplate` для вызова Python-сервиса
- Улучшенное изображение сохраняется как новый `MicroscopeImage` с `isEnhanced=true`
- `ImageProcessingLog` фиксирует: дату, время обработки, модель, оригинал, результат
- Обработка ошибок: если Python-сервис недоступен → сообщение пользователю
- `application.properties`: `autoencoder.service.url=http://localhost:5000`

---

## ЭТАП 7: Отчёты и аналитический дашборд (Head)

**Цель:** начальник БСМЭ формирует статистические отчёты и видит аналитику.

### Файлы
```
service/employee/head/ReportService.java        — Apache POI, генерация Excel
service/employee/head/AnalyticsService.java     — агрегация данных для дашборда
controllers/employee/head/ReportController.java
templates/employee/head/reports/
    dashboard.html          — аналитический дашборд
    reportParams.html       — общая форма параметров (период, тип отчёта)
```

### Excel-отчёты (5 типов)
1. **Отчёт о поступивших образцах** (`samplesReceived`): период → таблица (дата, номер дела, номер образца, тип ткани, метод, эксперт) + итоги по типам ткани
2. **Отчёт о выполненных исследованиях** (`completedStudies`): период → таблица завершённых (дата завершения, образец, гистолог, диагноз) + итого по экспертам
3. **Отчёт о методах окрашивания** (`stainingMethods`): период → таблица (метод, количество, % от общего)
4. **Отчёт о результатах исследований** (`researchResults`): период → таблица (образец, диагноз, заключение гистолога, судебно-медицинское заключение)
5. **Отчёт об обработке изображений** (`imageProcessingStats`): период → таблица (дата, изображение, модель, время обработки) + итого

### Дашборд
- Всего дел (открытых / закрытых)
- Всего образцов по статусам (круговая диаграмма)
- Образцы за последние 30 дней (линейный график, vanilla JS `<canvas>`)
- Топ-5 методов окрашивания
- Среднее время от поступления до заключения
- Количество обработанных автоэнкодером изображений

---

## ЭТАП 8: Электронный журнал и финальная полировка

**Цель:** электронный журнал поступления и обработки образцов, финальная проверка всей системы.

### Файлы
```
service/employee/laborant/JournalService.java
controllers/employee/laborant/JournalController.java
templates/employee/laborant/journal/
    view.html               — сводная таблица журнала + фильтры по всем полям
```

### Электронный журнал
- Сводная таблица: номер дела, номер образца, дата поступления, тип ткани, метод окрашивания, стадия, ФИО эксперта, статус исследования
- Сортировка по любому столбцу (vanilla JS)
- Фильтрация по всем полям (дата, номер, тип, метод, стадия, эксперт, статус)
- Пагинация (vanilla JS)
- Экспорт в Excel (Apache POI)

### Финальная полировка
- [ ] Кросс-ролевая проверка навигации
- [ ] Проверка всех фильтров и поиска
- [ ] Проверка всех Excel-отчётов
- [ ] Проверка загрузки/просмотра/удаления изображений
- [ ] Проверка интеграции с автоэнкодером
- [ ] Проверка workflow стадий исследования
- [ ] Проверка заключений с разграничением доступа
- [ ] Адаптивность на мобильных устройствах

---

## UPDATE: Управляемое обучение автоэнкодера через интерфейс гистолога

**Цель:** сделать нейросетевое улучшение изображений наглядным и управляемым из интерфейса, чтобы гистолог мог запускать обучение модели, менять гиперпараметры, видеть метрики и сравнивать результаты.

### Что должно появиться в UI гистолога
- Отдельная страница или модуль: `Нейросетевое улучшение` / `Обучение автоэнкодера`
- Форма параметров обучения:
  - epochs
  - batch size
  - learning rate
  - image size
  - режим улучшения: `auto` / `baseline` / `neural`
- Кнопка запуска обучения модели
- Блок текущего статуса:
  - обучается / завершено / ошибка
  - дата последнего запуска
  - активная модель
  - число изображений в датасете
- Блок метрик:
  - train loss
  - validation loss
  - PSNR
  - SSIM
- Блок сравнения:
  - оригинал
  - baseline-результат
  - neural-результат
  - side-by-side сравнение
- История запусков обучения с параметрами и итоговыми метриками

### Новые backend-задачи для Spring Boot
```
models/TrainingSession.java              — история запусков обучения модели
repo/TrainingSessionRepository.java
dto/TrainingSessionDTO.java
mapper/TrainingSessionMapper.java

service/employee/histologist/AutoencoderTrainingService.java
controllers/employee/histologist/AutoencoderTrainingController.java

templates/employee/histologist/autoencoder/
    dashboard.html                       — общая страница управления обучением
    trainingHistory.html                — история запусков
    metrics.html                        — метрики модели и результаты
```

### Что должен делать Spring Boot
- Передавать параметры обучения в Python-сервис
- Получать статус обучения и метрики
- Сохранять историю запусков в БД
- Показывать активную модель и результаты последнего обучения
- Давать возможность явно запускать улучшение изображения в режимах `baseline` и `neural`
- Давать возможность сравнивать baseline и neural на одном и том же изображении

### Новые endpoint'ы Python-сервиса
- `POST /train` — запуск обучения с параметрами
- `GET /training/status` — текущий статус обучения
- `GET /training/history` — история запусков
- `GET /metrics` — метрики активной модели
- `POST /enhance` — улучшение изображения с параметром режима `baseline` / `neural` / `auto`
- `GET /models` — список моделей и их состояние

### Что должен делать Python-сервис
- Учить модель по схеме `искусственно ухудшенное изображение -> качественное изображение`
- Делать деградацию на лету:
  - blur
  - noise
  - JPEG artifacts
  - downscale/upscale
  - снижение контраста / яркости
- Сохранять веса модели и metadata
- Сохранять метрики после каждой тренировки
- Отдавать текущую активную модель и её характеристики

### Метрики для диплома
- Train Loss
- Validation Loss
- PSNR
- SSIM
- Визуальное сравнение до/после
- Сравнение baseline vs neural

### Порядок реализации
1. Добавить сущность истории обучения в Java и таблицу для хранения запусков
2. Добавить Python endpoint'ы статуса, истории и метрик
3. Добавить страницу управления обучением для гистолога
4. Добавить форму гиперпараметров и запуск обучения из UI
5. Добавить вывод метрик и активной модели
6. Добавить сравнение baseline и neural в интерфейсе
7. Добавить историю запусков обучения
8. Подготовить набор контрольных изображений для демонстрации в дипломе

### Ожидаемый результат
- Работа нейросетевого улучшения становится видимой через интерфейс
- Пользователь-гистолог может влиять на обучение и видеть эффект параметров
- Система показывает не только итоговую картинку, но и процесс ML-обработки
- Модуль выглядит как полноценная дипломная подсистема, а не скрытая внутренняя функция
