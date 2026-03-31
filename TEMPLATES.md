# TEMPLATES.md — Шаблоны кода для АОС ПИ

> Читать только при **создании новой сущности с нуля** вместе с `CLAUDE.md` и `CODESTYLE.md`.
> При мелких правках, добавлении полей или исправлении багов этот файл не нужен.

---

## Entity

```java
package com.mai.aospi.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * [Описание сущности на русском — что представляет, какую роль играет в системе]
 *
 * Связи:
 * - ManyToOne → RelatedEntity
 * - OneToMany → ChildEntity
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "t_entityName")
@EqualsAndHashCode(of = "id")
public class EntityName {

    // ========== ПОЛЯ ==========

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** [Описание поля] */
    @Column(nullable = false)
    private String name;

    /** [Уникальное поле] */
    @Column(unique = true, nullable = false)
    private String uniqueField;

    /** [Статус] */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntityStatus status;

    /** [Связь с родителем] */
    @ManyToOne
    @JoinColumn(nullable = false)
    private RelatedEntity related;

    /** [Дочерние записи] */
    @ToString.Exclude
    @OneToMany(mappedBy = "entityName", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChildEntity> children = new ArrayList<>();

    // ========== КОНСТРУКТОРЫ ==========

    // ========== МЕТОДЫ ==========

    @Transient
    public int getComputedField() {
        return 0; // вычисляемое поле
    }
}
```

---

## Repository

```java
package com.mai.aospi.repo;

import com.mai.aospi.models.EntityName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntityNameRepository extends JpaRepository<EntityName, Long> {

    boolean existsByUniqueField(String value);

    boolean existsByUniqueFieldAndIdNot(String value, Long id);

    // Пример поиска с пагинацией:
    // Page<EntityName> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // Пример JPQL:
    // @Query("SELECT e FROM EntityName e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    // List<EntityName> findByName(@Param("search") String search);
}
```

---

## DTO

```java
package com.mai.aospi.dto;

import lombok.Data;

@Data
public class EntityNameDTO {
    private Long id;
    private String name;
    private String uniqueField;

    // Развёрнутые поля из связей (не объекты, а конкретные поля)
    private Long relatedId;
    private String relatedName;

    // Enum-статус
    private String statusDisplayName;

    // Вычисляемые поля
    private int computedField;
}
```

---

## Mapper

```java
package com.mai.aospi.mapper;

import com.mai.aospi.dto.EntityNameDTO;
import com.mai.aospi.models.EntityName;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface EntityNameMapper {
    EntityNameMapper INSTANCE = Mappers.getMapper(EntityNameMapper.class);

    @Mapping(source = "related.id", target = "relatedId")
    @Mapping(source = "related.name", target = "relatedName")
    @Mapping(source = "status", target = "statusDisplayName", qualifiedByName = "statusToDisplay")
    @Mapping(source = "entity", target = "computedField", qualifiedByName = "computedField")
    EntityNameDTO toDTO(EntityName entity);

    List<EntityNameDTO> toDTOList(List<EntityName> entities);

    @Named("statusToDisplay")
    default String statusToDisplay(EntityStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    @Named("computedField")
    default int computedField(EntityName entity) {
        return entity.getComputedField();
    }
}
```

---

## Enum

```java
package com.mai.aospi.enumeration;

import lombok.Getter;

@Getter
public enum EntityStatus {
    NEW("Новый"),
    IN_PROGRESS("В работе"),
    COMPLETED("Завершён"),
    CANCELLED("Отменён");

    private final String displayName;

    EntityStatus(String displayName) {
        this.displayName = displayName;
    }
}
```

---

## Service

```java
package com.mai.aospi.service.employee.rolename;

import com.mai.aospi.dto.EntityNameDTO;
import com.mai.aospi.mapper.EntityNameMapper;
import com.mai.aospi.models.EntityName;
import com.mai.aospi.models.RelatedEntity;
import com.mai.aospi.repo.EntityNameRepository;
import com.mai.aospi.repo.RelatedEntityRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;
import java.util.Optional;

/**
 * Сервис управления [сущностями].
 */
@Service
@Getter
@Slf4j
public class EntityNameService {

    private final EntityNameRepository entityNameRepository;
    private final RelatedEntityRepository relatedEntityRepository;

    public EntityNameService(EntityNameRepository entityNameRepository,
                             RelatedEntityRepository relatedEntityRepository) {
        this.entityNameRepository = entityNameRepository;
        this.relatedEntityRepository = relatedEntityRepository;
    }

    // --- Проверка уникальности ---

    public boolean checkUniqueField(String value, Long id) {
        if (value == null || value.isBlank()) return false;
        if (id != null) return entityNameRepository.existsByUniqueFieldAndIdNot(value, id);
        return entityNameRepository.existsByUniqueField(value);
    }

    // --- Создание ---

    @Transactional
    public Optional<Long> saveEntityName(EntityName entity, Long relatedId) {
        if (checkUniqueField(entity.getUniqueField(), null)) {
            log.error("Запись с uniqueField={} уже существует.", entity.getUniqueField());
            return Optional.empty();
        }

        Optional<RelatedEntity> relatedOpt = relatedEntityRepository.findById(relatedId);
        if (relatedOpt.isEmpty()) {
            log.error("RelatedEntity с id={} не найдена.", relatedId);
            return Optional.empty();
        }
        entity.setRelated(relatedOpt.get());

        try {
            entityNameRepository.save(entity);
        } catch (Exception e) {
            log.error("Ошибка при сохранении EntityName: {}", e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }

        log.info("EntityName сохранена: id={}", entity.getId());
        return Optional.of(entity.getId());
    }

    // --- Редактирование ---

    @Transactional
    public Optional<Long> editEntityName(Long id, String inputName, String inputUniqueField) {
        Optional<EntityName> entityOpt = entityNameRepository.findById(id);
        if (entityOpt.isEmpty()) {
            log.error("EntityName с id={} не найдена.", id);
            return Optional.empty();
        }

        if (checkUniqueField(inputUniqueField, id)) {
            log.error("Запись с uniqueField={} уже существует.", inputUniqueField);
            return Optional.empty();
        }

        EntityName entity = entityOpt.get();
        entity.setName(inputName);
        entity.setUniqueField(inputUniqueField);

        try {
            entityNameRepository.save(entity);
        } catch (Exception e) {
            log.error("Ошибка при обновлении EntityName id={}: {}", id, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return Optional.empty();
        }

        return Optional.of(entity.getId());
    }

    // --- Удаление ---

    @Transactional
    public boolean deleteEntityName(Long id) {
        Optional<EntityName> entityOpt = entityNameRepository.findById(id);
        if (entityOpt.isEmpty()) {
            log.error("EntityName с id={} не найдена.", id);
            return false;
        }

        // Проверка зависимостей (раскомментировать при наличии дочерних сущностей):
        // if (childRepository.countByEntityNameId(id) > 0) {
        //     log.error("Невозможно удалить EntityName id={}: есть зависимые записи.", id);
        //     return false;
        // }

        try {
            entityNameRepository.delete(entityOpt.get());
        } catch (Exception e) {
            log.error("Ошибка при удалении EntityName id={}: {}", id, e.getMessage(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }

        log.info("EntityName удалена: id={}", id);
        return true;
    }

    // --- Получение ---

    @Transactional
    public List<EntityNameDTO> getAllEntityNames() {
        return EntityNameMapper.INSTANCE.toDTOList(entityNameRepository.findAll());
    }
}
```

---

## Controller

```java
package com.mai.aospi.controllers.employee.rolename;

import com.mai.aospi.dto.EntityNameDTO;
import com.mai.aospi.mapper.EntityNameMapper;
import com.mai.aospi.models.EntityName;
import com.mai.aospi.service.employee.rolename.EntityNameService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@Slf4j
public class EntityNameController {

    private final EntityNameService entityNameService;

    public EntityNameController(EntityNameService entityNameService) {
        this.entityNameService = entityNameService;
    }

    // AJAX-проверка уникальности
    @GetMapping("/employee/rolename/entities/check-uniqueField")
    public ResponseEntity<Map<String, Boolean>> checkUniqueField(
            @RequestParam String value,
            @RequestParam(required = false) Long id) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", entityNameService.checkUniqueField(value, id));
        return ResponseEntity.ok(response);
    }

    // Список
    @GetMapping("/employee/rolename/entities/allEntities")
    public String allEntities(Model model) {
        model.addAttribute("allEntities", entityNameService.getAllEntityNames().stream()
                .sorted(Comparator.comparing(EntityNameDTO::getName))
                .toList());
        return "employee/rolename/entities/allEntities";
    }

    // Форма добавления
    @GetMapping("/employee/rolename/entities/addEntity")
    public String addEntity(Model model) {
        // model.addAttribute("allRelated", ...); // справочники если нужны
        return "employee/rolename/entities/addEntity";
    }

    // Сохранение
    @PostMapping("/employee/rolename/entities/addEntity")
    public String addEntity(@RequestParam String inputName,
                            @RequestParam String inputUniqueField,
                            @RequestParam Long inputRelatedId,
                            Model model) {
        EntityName entity = new EntityName();
        entity.setName(inputName);
        entity.setUniqueField(inputUniqueField);

        Optional<Long> savedId = entityNameService.saveEntityName(entity, inputRelatedId);
        if (savedId.isEmpty()) {
            model.addAttribute("entityError", "Ошибка при сохранении. Проверьте введённые данные.");
            // model.addAttribute("allRelated", ...); // переподготовить справочники
            return "employee/rolename/entities/addEntity";
        }
        return "redirect:/employee/rolename/entities/detailsEntity/" + savedId.get();
    }

    // Просмотр
    @GetMapping("/employee/rolename/entities/detailsEntity/{id}")
    public String detailsEntity(@PathVariable(value = "id") long id, Model model) {
        if (!entityNameService.getEntityNameRepository().existsById(id)) {
            return "redirect:/employee/rolename/entities/allEntities";
        }
        EntityName entity = entityNameService.getEntityNameRepository().findById(id).get();
        model.addAttribute("entityDTO", EntityNameMapper.INSTANCE.toDTO(entity));
        return "employee/rolename/entities/detailsEntity";
    }

    // Форма редактирования
    @GetMapping("/employee/rolename/entities/editEntity/{id}")
    public String editEntity(@PathVariable(value = "id") long id, Model model) {
        if (!entityNameService.getEntityNameRepository().existsById(id)) {
            return "redirect:/employee/rolename/entities/allEntities";
        }
        EntityName entity = entityNameService.getEntityNameRepository().findById(id).get();
        model.addAttribute("entityDTO", EntityNameMapper.INSTANCE.toDTO(entity));
        // model.addAttribute("allRelated", ...);
        return "employee/rolename/entities/editEntity";
    }

    // Сохранение изменений
    @PostMapping("/employee/rolename/entities/editEntity/{id}")
    public String editEntity(@PathVariable(value = "id") long id,
                             @RequestParam String inputName,
                             @RequestParam String inputUniqueField,
                             RedirectAttributes redirectAttributes) {
        Optional<Long> editedId = entityNameService.editEntityName(id, inputName, inputUniqueField);
        if (editedId.isEmpty()) {
            redirectAttributes.addFlashAttribute("entityError", "Ошибка при сохранении.");
            return "redirect:/employee/rolename/entities/editEntity/" + id;
        }
        return "redirect:/employee/rolename/entities/detailsEntity/" + editedId.get();
    }

    // Удаление
    @GetMapping("/employee/rolename/entities/deleteEntity/{id}")
    public String deleteEntity(@PathVariable(value = "id") long id,
                               RedirectAttributes redirectAttributes) {
        if (!entityNameService.deleteEntityName(id)) {
            redirectAttributes.addFlashAttribute("deleteError", "Невозможно удалить запись: есть зависимые данные.");
            return "redirect:/employee/rolename/entities/detailsEntity/" + id;
        }
        return "redirect:/employee/rolename/entities/allEntities";
    }
}
```

---

## HTML — allEntities.html

```html
<!DOCTYPE HTML>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Список [сущностей] | АОС ПИ</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/style.css" rel="stylesheet">
    <script src="/js/bootstrap.bundle.min.js"></script>
</head>
<body>
<header th:insert="~{blocks/header :: header}"></header>

<div class="container my-5">
    <div class="profile-card mb-4">
        <div class="profile-actions">
            <a th:href="'/'" class="btn btn-secondary btn-profile">← Главная</a>
            <a th:href="'/employee/rolename/entities/addEntity'" class="btn btn-success btn-profile">+ Добавить</a>
        </div>
    </div>

    <div class="profile-card p-4">
        <h2 class="h3 mb-4 fw-bold">Список [сущностей]</h2>
        <div class="table-responsive">
            <table class="table table-modern">
                <thead>
                    <tr>
                        <th>№</th>
                        <th>Название</th>
                        <th>Поле</th>
                        <th>Статус</th>
                    </tr>
                </thead>
                <tbody>
                    <tr th:each="item, index : ${allEntities}">
                        <td th:text="${index.index + 1}"></td>
                        <td>
                            <a th:text="${item.getName()}"
                               th:href="'/employee/rolename/entities/detailsEntity/' + ${item.getId()}"></a>
                        </td>
                        <td th:text="${item.getField()}"></td>
                        <td th:text="${item.getStatusDisplayName()}"></td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div th:insert="~{blocks/footer :: footer}"></div>
</body>
</html>
```

---

## HTML — addEntity.html

```html
<!DOCTYPE HTML>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Добавление [сущности] | АОС ПИ</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/style.css" rel="stylesheet">
    <script src="/js/bootstrap.bundle.min.js"></script>
</head>
<body>
<header th:insert="~{blocks/header :: header}"></header>

<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-12 col-lg-10">
            <div class="profile-card p-4">
                <form action="/employee/rolename/entities/addEntity" method="post" class="auth-form">
                    <h1 class="page-title">Добавление [сущности]</h1>
                    <p class="page-subtitle">Заполните поля и сохраните запись</p>

                    <div th:if="${entityError}" class="alert alert-danger" th:text="${entityError}"></div>

                    <div class="mb-4">
                        <h6 class="form-section-title">Основные данные</h6>
                        <div class="row g-3">
                            <div class="col-md-6">
                                <input type="text" class="form-control" name="inputName"
                                       required placeholder="Название" maxlength="255">
                            </div>
                            <div class="col-md-6">
                                <input type="text" class="form-control" id="inputUniqueField"
                                       name="inputUniqueField" required placeholder="Уникальное поле" maxlength="100">
                                <div id="uniqueFieldError" class="invalid-feedback" style="display:none">
                                    Такое значение уже существует
                                </div>
                            </div>
                            <div class="col-md-6">
                                <select class="form-select" name="inputRelatedId" required>
                                    <option value="" disabled selected>— Выберите —</option>
                                    <option th:each="rel : ${allRelated}"
                                            th:value="${rel.getId()}"
                                            th:text="${rel.getName()}"></option>
                                </select>
                            </div>
                        </div>
                    </div>

                    <button type="submit" id="submitBtn" class="btn btn-success btn-profile">Сохранить</button>
                </form>
                <div class="link-section mt-3">
                    <a th:href="'/employee/rolename/entities/allEntities'">← К списку</a>
                </div>
            </div>
        </div>
    </div>
</div>

<div th:insert="~{blocks/footer :: footer}"></div>
<script>
    // AJAX-валидация уникального поля
    const inputUnique = document.getElementById('inputUniqueField');
    const uniqueError = document.getElementById('uniqueFieldError');
    const submitBtn = document.getElementById('submitBtn');

    inputUnique.addEventListener('input', function () {
        const value = inputUnique.value.trim();
        if (value.length === 0) {
            uniqueError.style.display = 'none';
            inputUnique.classList.remove('is-invalid', 'is-valid');
            submitBtn.disabled = false;
            return;
        }
        fetch('/employee/rolename/entities/check-uniqueField?value=' + encodeURIComponent(value))
            .then(r => r.json())
            .then(data => {
                if (data.exists) {
                    uniqueError.style.display = 'block';
                    inputUnique.classList.add('is-invalid');
                    inputUnique.classList.remove('is-valid');
                    submitBtn.disabled = true;
                } else {
                    uniqueError.style.display = 'none';
                    inputUnique.classList.remove('is-invalid');
                    inputUnique.classList.add('is-valid');
                    submitBtn.disabled = false;
                }
            });
    });
</script>
</body>
</html>
```

---

## HTML — editEntity.html

Идентична `addEntity.html`, отличия:
- `th:action="'/employee/rolename/entities/editEntity/' + ${entityDTO.getId()}"`
- Поля с `th:value="${entityDTO.getField()}"`
- Select с `th:selected="${rel.getId() == entityDTO.getRelatedId()}"`
- Заголовок: «Редактирование [сущности]»
- Кнопка: «Сохранить изменения»
- AJAX check с параметром `&id=` + `entityDTO.getId()` для исключения текущей записи:
  ```javascript
  fetch('/employee/rolename/entities/check-uniqueField?value=' + encodeURIComponent(value)
      + '&id=' + /*[[${entityDTO.getId()}]]*/ 0)
  ```

---

## HTML — detailsEntity.html

```html
<!DOCTYPE HTML>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Информация о [сущности] | АОС ПИ</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link href="/css/bootstrap.min.css" rel="stylesheet">
    <link href="/css/style.css" rel="stylesheet">
    <script src="/js/bootstrap.bundle.min.js"></script>
</head>
<body>
<header th:insert="~{blocks/header :: header}"></header>

<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-12 col-lg-10">
            <div class="profile-card p-4">
                <h1 class="page-title">Информация о [сущности]</h1>
                <p class="page-subtitle">Просмотр карточки</p>

                <div th:if="${deleteError}" class="alert alert-danger" th:text="${deleteError}"></div>

                <div class="mb-4">
                    <h6 class="form-section-title">Основные данные</h6>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label text-muted">Название</label>
                            <input type="text" class="form-control info-display"
                                   th:value="${entityDTO.getName()}" disabled>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label text-muted">Уникальное поле</label>
                            <input type="text" class="form-control info-display"
                                   th:value="${entityDTO.getUniqueField()}" disabled>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label text-muted">Связанная запись</label>
                            <input type="text" class="form-control info-display"
                                   th:value="${entityDTO.getRelatedName()}" disabled>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label text-muted">Статус</label>
                            <input type="text" class="form-control info-display"
                                   th:value="${entityDTO.getStatusDisplayName()}" disabled>
                        </div>
                    </div>
                </div>

                <!-- Связанные записи (если есть OneToMany) -->
                <div class="mb-4" th:if="${childItems != null and !childItems.isEmpty()}">
                    <h6 class="form-section-title">Связанные записи</h6>
                    <div class="table-responsive">
                        <table class="table table-sm table-striped">
                            <thead>
                                <tr><th>№</th><th>Поле</th></tr>
                            </thead>
                            <tbody>
                                <tr th:each="child, idx : ${childItems}">
                                    <td th:text="${idx.index + 1}"></td>
                                    <td th:text="${child.getField()}"></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <div class="action-buttons mb-4">
                    <button th:onclick="'window.location.href=\'/employee/rolename/entities/editEntity/' + ${entityDTO.getId()} + '\';'"
                            class="btn btn-primary btn-profile">Редактировать</button>
                    <button class="btn btn-danger btn-profile"
                            th:data-entityid="${entityDTO.getId()}"
                            th:data-name="${entityDTO.getName()}"
                            onclick="confirmDelete(this)">Удалить</button>
                </div>

                <div class="link-section">
                    <a th:href="'/employee/rolename/entities/allEntities'">← К списку</a>
                </div>
            </div>
        </div>
    </div>
</div>

<div th:insert="~{blocks/footer :: footer}"></div>
<script>
    function confirmDelete(button) {
        const id = button.getAttribute('data-entityid');
        const name = button.getAttribute('data-name');
        if (confirm('Вы точно хотите удалить «' + name + '»?')) {
            window.location.href = '/employee/rolename/entities/deleteEntity/' + id;
        }
    }
</script>
</body>
</html>
```

---

## JS — AJAX-валидация для editEntity

```javascript
// Передать id текущей записи через data-атрибут или Thymeleaf inline
const currentId = /*[[${entityDTO.getId()}]]*/ 0;
const inputUnique = document.getElementById('inputUniqueField');
const uniqueError = document.getElementById('uniqueFieldError');
const submitBtn = document.getElementById('submitBtn');

inputUnique.addEventListener('input', function () {
    const value = inputUnique.value.trim();
    if (value.length === 0) {
        uniqueError.style.display = 'none';
        inputUnique.classList.remove('is-invalid', 'is-valid');
        submitBtn.disabled = false;
        return;
    }
    fetch('/employee/rolename/entities/check-uniqueField?value='
            + encodeURIComponent(value) + '&id=' + currentId)
        .then(r => r.json())
        .then(data => {
            if (data.exists) {
                uniqueError.style.display = 'block';
                inputUnique.classList.add('is-invalid');
                inputUnique.classList.remove('is-valid');
                submitBtn.disabled = true;
            } else {
                uniqueError.style.display = 'none';
                inputUnique.classList.remove('is-invalid');
                inputUnique.classList.add('is-valid');
                submitBtn.disabled = false;
            }
        });
});
```
