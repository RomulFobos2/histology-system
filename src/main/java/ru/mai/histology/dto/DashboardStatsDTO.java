package ru.mai.histology.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DashboardStatsDTO {
    private long totalCases;
    private long openCases;
    private long closedCases;

    /** Количество образцов по статусам (для круговой диаграммы) */
    private Map<String, Long> samplesByStatus = new LinkedHashMap<>();

    /** Количество образцов за последние 30 дней (ключ: "dd.MM") */
    private Map<String, Long> samplesLast30Days = new LinkedHashMap<>();

    /** Топ-5 методов окрашивания */
    private Map<String, Long> topStainingMethods = new LinkedHashMap<>();

    /** Среднее количество дней от поступления до заключения */
    private double avgDaysReceiptToConclusion;

    /** Количество обработанных автоэнкодером изображений */
    private long autoencoderProcessedCount;
}
