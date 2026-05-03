package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.mai.histology.dto.ResearchProtocolDTO;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.service.employee.histologist.ProtocolService;
import ru.mai.histology.service.employee.histologist.SampleViewService;
import ru.mai.histology.service.general.WordExportService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Контроллер протоколов исследования */
@Controller
@Slf4j
public class ProtocolController {

    private final ProtocolService protocolService;
    private final SampleViewService sampleViewService;
    private final WordExportService wordExportService;

    public ProtocolController(ProtocolService protocolService,
                              SampleViewService sampleViewService,
                              WordExportService wordExportService) {
        this.protocolService = protocolService;
        this.sampleViewService = sampleViewService;
        this.wordExportService = wordExportService;
    }

    // ========== Список протоколов ==========

    @GetMapping("/employee/histologist/protocols/allProtocols")
    public String allProtocols(Model model) {
        model.addAttribute("allProtocols", protocolService.getAllProtocols());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        return "employee/histologist/protocols/allProtocols";
    }

    // ========== Генерация протокола ==========

    @GetMapping("/employee/histologist/protocols/generateProtocol/{sampleId}")
    public String generateProtocolForm(@PathVariable(value = "sampleId") long sampleId, Model model) {
        Optional<SampleDTO> sampleOpt = sampleViewService.getSampleById(sampleId);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }

        // Проверяем, нет ли уже протокола для этого образца
        if (protocolService.protocolExistsForSample(sampleId)) {
            return "redirect:/employee/histologist/samples/detailsSample/" + sampleId;
        }

        model.addAttribute("sampleDTO", sampleOpt.get());
        model.addAttribute("generatedText", protocolService.generateProtocolText(sampleId));
        return "employee/histologist/protocols/generateProtocol";
    }

    @PostMapping("/employee/histologist/protocols/generateProtocol/{sampleId}")
    public String generateProtocol(@PathVariable(value = "sampleId") long sampleId,
                                   @RequestParam String inputProtocolNumber,
                                   @RequestParam String inputProtocolText,
                                   Model model) {
        Optional<Long> savedId = protocolService.generateProtocol(sampleId,
                inputProtocolNumber, inputProtocolText);

        if (savedId.isEmpty()) {
            model.addAttribute("protocolError", "Ошибка при создании протокола. Проверьте номер протокола.");
            sampleViewService.getSampleById(sampleId).ifPresent(s -> model.addAttribute("sampleDTO", s));
            model.addAttribute("generatedText", protocolService.generateProtocolText(sampleId));
            return "employee/histologist/protocols/generateProtocol";
        }

        return "redirect:/employee/histologist/protocols/detailsProtocol/" + savedId.get();
    }

    // ========== Карточка протокола ==========

    @GetMapping("/employee/histologist/protocols/detailsProtocol/{id}")
    public String detailsProtocol(@PathVariable(value = "id") long id, Model model) {
        Optional<ResearchProtocolDTO> protocolOpt = protocolService.getProtocolById(id);
        if (protocolOpt.isEmpty()) {
            return "redirect:/employee/histologist/protocols/allProtocols";
        }
        model.addAttribute("protocolDTO", protocolOpt.get());
        return "employee/histologist/protocols/detailsProtocol";
    }

    // ========== Редактирование ==========

    @GetMapping("/employee/histologist/protocols/editProtocol/{id}")
    public String editProtocolForm(@PathVariable(value = "id") long id, Model model) {
        Optional<ResearchProtocolDTO> protocolOpt = protocolService.getProtocolById(id);
        if (protocolOpt.isEmpty()) {
            return "redirect:/employee/histologist/protocols/allProtocols";
        }
        model.addAttribute("protocolDTO", protocolOpt.get());
        return "employee/histologist/protocols/editProtocol";
    }

    @PostMapping("/employee/histologist/protocols/editProtocol/{id}")
    public String editProtocol(@PathVariable(value = "id") long id,
                               @RequestParam String inputProtocolNumber,
                               @RequestParam String inputProtocolText,
                               Model model) {
        Optional<Long> editedId = protocolService.editProtocol(id,
                inputProtocolNumber, inputProtocolText);

        if (editedId.isEmpty()) {
            model.addAttribute("protocolError", "Ошибка при обновлении протокола. Проверьте номер протокола.");
            protocolService.getProtocolById(id).ifPresent(p -> model.addAttribute("protocolDTO", p));
            return "employee/histologist/protocols/editProtocol";
        }

        return "redirect:/employee/histologist/protocols/detailsProtocol/" + id;
    }

    // ========== AJAX проверка номера протокола ==========

    @GetMapping("/employee/histologist/protocols/check-protocolNumber")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkProtocolNumber(
            @RequestParam String protocolNumber,
            @RequestParam(required = false) Long id) {
        Map<String, Boolean> response = new HashMap<>();
        if (id != null) {
            response.put("exists", protocolService.checkProtocolNumberExcluding(protocolNumber, id));
        } else {
            response.put("exists", protocolService.checkProtocolNumber(protocolNumber));
        }
        return ResponseEntity.ok(response);
    }

    // ========== Экспорт протокола (Word-подобный вывод) ==========

    @PostMapping("/employee/histologist/protocols/exportProtocol/{id}")
    public ResponseEntity<byte[]> exportProtocol(@PathVariable(value = "id") long id) {
        Optional<ResearchProtocolDTO> protocolOpt = protocolService.getProtocolById(id);
        if (protocolOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] docx = wordExportService.exportProtocol(protocolOpt.get());
            String filename = "protocol_" + protocolOpt.get().getProtocolNumber() + ".docx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docx);
        } catch (Exception e) {
            log.error("Ошибка экспорта протокола id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
