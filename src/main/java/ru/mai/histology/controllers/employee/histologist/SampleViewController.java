package ru.mai.histology.controllers.employee.histologist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.mai.histology.dto.SampleDTO;
import ru.mai.histology.enumeration.SampleStatus;
import ru.mai.histology.enumeration.StainingMethod;
import ru.mai.histology.enumeration.TissueType;
import ru.mai.histology.service.employee.histologist.ConclusionService;
import ru.mai.histology.service.employee.histologist.ProtocolService;
import ru.mai.histology.service.employee.histologist.SampleViewService;

import java.util.Optional;

/** Контроллер просмотра образцов для гистолога (read-only) */
@Controller
@Slf4j
public class SampleViewController {

    private final SampleViewService sampleViewService;
    private final ConclusionService conclusionService;
    private final ProtocolService protocolService;

    public SampleViewController(SampleViewService sampleViewService,
                                ConclusionService conclusionService,
                                ProtocolService protocolService) {
        this.sampleViewService = sampleViewService;
        this.conclusionService = conclusionService;
        this.protocolService = protocolService;
    }

    @GetMapping("/employee/histologist/samples/allSamples")
    public String allSamples(Model model) {
        model.addAttribute("allSamples", sampleViewService.getMySamples());
        model.addAttribute("tissueTypes", TissueType.values());
        model.addAttribute("stainingMethods", StainingMethod.values());
        model.addAttribute("sampleStatuses", SampleStatus.values());
        return "employee/histologist/samples/allSamples";
    }

    @GetMapping("/employee/histologist/samples/detailsSample/{id}")
    public String detailsSample(@PathVariable(value = "id") long id, Model model) {
        if (!sampleViewService.isAssignedToCurrentUser(id)) {
            return "redirect:/employee/histologist/samples/allSamples";
        }
        Optional<SampleDTO> sampleOpt = sampleViewService.getSampleById(id);
        if (sampleOpt.isEmpty()) {
            return "redirect:/employee/histologist/samples/allSamples";
        }

        SampleDTO sampleDTO = sampleOpt.get();
        model.addAttribute("sampleDTO", sampleDTO);

        // Проверяем наличие заключения и протокола для данного образца
        model.addAttribute("hasConclusionForSample", conclusionService.conclusionExistsForSample(id));
        model.addAttribute("hasProtocolForSample", protocolService.protocolExistsForSample(id));

        // Если есть — передаём их ID для ссылок
        conclusionService.getConclusionBySampleId(id).ifPresent(c ->
                model.addAttribute("conclusionId", c.getId()));
        protocolService.getProtocolBySampleId(id).ifPresent(p ->
                model.addAttribute("protocolId", p.getId()));

        return "employee/histologist/samples/detailsSample";
    }
}
