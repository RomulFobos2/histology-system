package ru.mai.histology.controllers.employee.laborant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.mai.histology.dto.JournalEntryDTO;
import ru.mai.histology.enumeration.ResearchStage;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.service.employee.laborant.JournalService;

import java.util.List;

/** Контроллер электронного журнала лаборанта */
@Controller
@RequestMapping("/employee/laborant/journal")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping("/view")
    public String viewJournal(Model model) {
        List<JournalEntryDTO> entries = journalService.getJournalEntries();
        model.addAttribute("journalEntries", entries);
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        model.addAttribute("researchStages", ResearchStage.values());
        model.addAttribute("sampleStatuses", SampleStatus.values());
        return "employee/laborant/journal/view";
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportJournal() {
        byte[] excel = journalService.generateJournalExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"zhurnal.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excel.length)
                .body(excel);
    }
}
