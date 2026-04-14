// АИС ГИ — презентация для защиты
const pptxgen = require("pptxgenjs");
const pres = new pptxgen();

pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.title = "АИС ГИ — Автоматизированная информационная система гистологических исследований";

// Палитра: Medical Teal
const C = {
  primary: "0E4D64",   // глубокий teal
  secondary: "2D9CDB", // небесный
  accent:   "F39C2B",  // янтарный
  bg:       "FAFAF7",  // тёплый off-white
  ink:      "1B2A32",  // почти чёрный
  muted:    "6B7A82",
  soft:     "E6EEF1",
  white:    "FFFFFF",
};

const FH = "Georgia";
const FB = "Calibri";

// ---------- helpers ----------
function bgLight(slide) {
  slide.background = { color: C.bg };
}
function bgDark(slide) {
  slide.background = { color: C.primary };
}
function footer(slide, num, total) {
  slide.addText("АИС ГИ · ГБУ БСМЭ г. Байконур", {
    x: 0.5, y: 7.1, w: 8, h: 0.3,
    fontFace: FB, fontSize: 10, color: C.muted,
  });
  slide.addText(`${num} / ${total}`, {
    x: 12.3, y: 7.1, w: 0.7, h: 0.3,
    fontFace: FB, fontSize: 10, color: C.muted, align: "right",
  });
}
function title(slide, text) {
  slide.addShape("rect", { x: 0, y: 0, w: 13.33, h: 1.15, fill: { color: C.primary } });
  slide.addShape("rect", { x: 0, y: 1.15, w: 13.33, h: 0.08, fill: { color: C.accent }, line: { color: C.accent } });
  slide.addText(text, {
    x: 0.5, y: 0.2, w: 12.3, h: 0.85,
    fontFace: FH, fontSize: 28, bold: true, color: C.white, valign: "middle",
  });
}

const TOTAL = 15;
let N = 0;
const next = () => ++N;

// ================= SLIDE 1 — TITLE =================
{
  const s = pres.addSlide();
  bgDark(s);
  // декоративная полоса
  s.addShape("rect", { x: 0, y: 6.3, w: 13.33, h: 0.12, fill: { color: C.accent }, line: { color: C.accent } });
  s.addShape("rect", { x: 0, y: 0, w: 0.35, h: 7.5, fill: { color: C.accent }, line: { color: C.accent } });

  s.addText("АИС ГИ", {
    x: 0.8, y: 1.6, w: 12, h: 1.3,
    fontFace: FH, fontSize: 60, bold: true, color: C.white,
  });
  s.addText("Автоматизированная информационная система\nгистологических исследований", {
    x: 0.8, y: 3.0, w: 12, h: 1.3,
    fontFace: FH, fontSize: 24, color: "CADCFC", italic: true,
  });
  s.addText("Ведение образцов · Архив изображений · Улучшение качества нейросетью · Отчётность", {
    x: 0.8, y: 4.5, w: 12, h: 0.5,
    fontFace: FB, fontSize: 16, color: C.white,
  });
  s.addText("ГБУ «Бюро судебно-медицинской экспертизы г. Байконур»", {
    x: 0.8, y: 6.5, w: 12, h: 0.5,
    fontFace: FB, fontSize: 14, color: "CADCFC",
  });
}

// ================= SLIDE 2 — Цели и задачи =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Зачем нужна система");

  s.addText("Проблема", {
    x: 0.5, y: 1.5, w: 6, h: 0.5,
    fontFace: FH, fontSize: 22, bold: true, color: C.primary,
  });
  s.addText(
    [
      { text: "• Регистрация образцов, дел и заключений ведётся на бумаге\n", options: {} },
      { text: "• Микроскопические снимки хранятся без единого архива\n", options: {} },
      { text: "• Качество изображений зависит от оборудования и освещения\n", options: {} },
      { text: "• Отчёты начальнику БСМЭ собираются вручную", options: {} },
    ],
    { x: 0.5, y: 2.0, w: 6.0, h: 4.5, fontFace: FB, fontSize: 16, color: C.ink, paraSpaceAfter: 6 }
  );

  // правая колонка — цели
  s.addShape("roundRect", { x: 7.0, y: 1.5, w: 5.8, h: 5.3, fill: { color: C.soft }, line: { color: C.secondary, width: 1 }, rectRadius: 0.15 });
  s.addText("Цели АИС ГИ", {
    x: 7.2, y: 1.65, w: 5.5, h: 0.5,
    fontFace: FH, fontSize: 20, bold: true, color: C.primary,
  });
  s.addText(
    [
      { text: "Единый электронный журнал образцов\n", options: { bullet: { code: "25A0" } } },
      { text: "Архив микроскопических изображений\n", options: { bullet: { code: "25A0" } } },
      { text: "Улучшение снимков свёрточной нейросетью\n", options: { bullet: { code: "25A0" } } },
      { text: "Заключения гистолога и начальника БСМЭ\n", options: { bullet: { code: "25A0" } } },
      { text: "Протоколы исследования (экспорт в Word)\n", options: { bullet: { code: "25A0" } } },
      { text: "5 статистических отчётов (экспорт в Excel)", options: { bullet: { code: "25A0" } } },
    ],
    { x: 7.3, y: 2.2, w: 5.4, h: 4.5, fontFace: FB, fontSize: 15, color: C.ink, paraSpaceAfter: 6 }
  );

  footer(s, 2, TOTAL);
}

// ================= SLIDE 3 — Архитектура =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Архитектура системы");

  // Три блока: Браузер → Spring Boot → MySQL, параллельно Python-сервис
  const box = (x, y, w, h, label, sub, fill) => {
    s.addShape("roundRect", { x, y, w, h, fill: { color: fill }, line: { color: C.primary, width: 1.5 }, rectRadius: 0.1 });
    s.addText(label, { x, y: y + 0.1, w, h: 0.5, fontFace: FH, fontSize: 18, bold: true, color: C.primary, align: "center" });
    s.addText(sub,   { x, y: y + 0.65, w, h: h - 0.7, fontFace: FB, fontSize: 13, color: C.ink, align: "center", valign: "top" });
  };

  box(0.6, 1.7, 3.0, 1.8, "Браузер",     "Thymeleaf-шаблоны\nBootstrap 5\nVanilla JS",        C.white);
  box(4.0, 1.7, 4.6, 1.8, "Spring Boot", "Java 21 · Security 6\nJPA/Hibernate · MapStruct\nApache POI (Word/Excel)", C.soft);
  box(9.0, 1.7, 3.7, 1.8, "MySQL",       "Пользователи, дела,\nобразцы, заключения,\nлоги обработки",  C.white);

  // стрелки
  s.addShape("rightTriangle", { x: 3.65, y: 2.45, w: 0.35, h: 0.35, fill: { color: C.secondary }, line: { color: C.secondary }, rotate: 90 });
  s.addShape("rightTriangle", { x: 8.65, y: 2.45, w: 0.35, h: 0.35, fill: { color: C.secondary }, line: { color: C.secondary }, rotate: 90 });

  // Python service
  box(4.0, 4.3, 4.6, 2.3, "Python сервис",
    "FastAPI · PyTorch\nU-Net + Baseline (Pillow)\nREST: /enhance /train /models",
    C.soft);

  // связь со Spring Boot
  s.addShape("line", { x: 6.3, y: 3.55, w: 0, h: 0.75, line: { color: C.accent, width: 3 } });
  s.addText("HTTP / REST", { x: 6.45, y: 3.65, w: 1.6, h: 0.3, fontFace: FB, fontSize: 11, color: C.accent, italic: true });

  // диск
  box(9.0, 4.3, 3.7, 2.3, "Файловое хранилище",
    "uploads/images/\n{caseNumber}/{sampleNumber}/\nTIFF · PNG · JPG",
    C.white);

  footer(s, 3, TOTAL);
}

// ================= SLIDE 4 — Роли =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Роли пользователей");

  const roles = [
    ["Администратор", "Управление пользователями, сброс паролей, деактивация", C.secondary],
    ["Лаборант",      "Регистрация дел, образцов, загрузка изображений, журнал", C.accent],
    ["Врач-гистолог", "Анализ образцов, улучшение снимков, заключения, протоколы", C.primary],
    ["Начальник БСМЭ","Судебные заключения, надзор, 5 отчётов", "6D2E46"],
  ];

  roles.forEach((r, i) => {
    const y = 1.7 + i * 1.25;
    // индикатор-круг
    s.addShape("ellipse", { x: 0.7, y: y + 0.15, w: 0.7, h: 0.7, fill: { color: r[2] }, line: { color: r[2] } });
    s.addText(String(i + 1), { x: 0.7, y: y + 0.15, w: 0.7, h: 0.7, fontFace: FH, fontSize: 22, bold: true, color: C.white, align: "center", valign: "middle" });
    // карточка
    s.addShape("roundRect", { x: 1.7, y: y, w: 11.1, h: 1.0, fill: { color: C.white }, line: { color: C.soft, width: 1 }, rectRadius: 0.1 });
    s.addText(r[0], { x: 1.9, y: y + 0.1, w: 4.0, h: 0.4, fontFace: FH, fontSize: 18, bold: true, color: r[2] });
    s.addText(r[1], { x: 1.9, y: y + 0.5, w: 10.7, h: 0.45, fontFace: FB, fontSize: 14, color: C.ink });
  });

  footer(s, 4, TOTAL);
}

// ================= SLIDE 5 — Рабочий процесс =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Жизненный цикл образца");

  const steps = [
    ["1", "Регистрация", "Лаборант создаёт дело и образцы"],
    ["2", "Снимки",      "Загрузка TIFF/JPG\nпревью, архив"],
    ["3", "Улучшение",   "Автоэнкодер\n(U-Net или baseline)"],
    ["4", "Анализ",      "Гистолог: диагноз\nи заключение"],
    ["5", "Заключение",  "Начальник БСМЭ:\nсудебное заключение"],
    ["6", "Отчётность",  "Протоколы Word,\nотчёты Excel"],
  ];

  steps.forEach((st, i) => {
    const x = 0.5 + i * 2.14;
    s.addShape("roundRect", { x, y: 2.1, w: 2.0, h: 3.8, fill: { color: C.white }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
    s.addShape("ellipse", { x: x + 0.6, y: 2.3, w: 0.8, h: 0.8, fill: { color: C.primary }, line: { color: C.primary } });
    s.addText(st[0], { x: x + 0.6, y: 2.3, w: 0.8, h: 0.8, fontFace: FH, fontSize: 24, bold: true, color: C.white, align: "center", valign: "middle" });
    s.addText(st[1], { x: x + 0.1, y: 3.3, w: 1.8, h: 0.5, fontFace: FH, fontSize: 16, bold: true, color: C.primary, align: "center" });
    s.addText(st[2], { x: x + 0.1, y: 3.85, w: 1.8, h: 1.9, fontFace: FB, fontSize: 12, color: C.ink, align: "center" });

    if (i < steps.length - 1) {
      s.addShape("rightTriangle", {
        x: x + 2.02, y: 3.95, w: 0.2, h: 0.3,
        fill: { color: C.accent }, line: { color: C.accent }, rotate: 90,
      });
    }
  });

  footer(s, 5, TOTAL);
}

// ================= SLIDE 6 — Почему Python-сервис =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Почему Python-микросервис, а не Java");

  s.addText("Нейросети живут в Python", {
    x: 0.5, y: 1.5, w: 12.3, h: 0.5,
    fontFace: FH, fontSize: 22, italic: true, color: C.primary,
  });

  const cards = [
    ["PyTorch",        "Стандартная библиотека для свёрточных сетей. Для Java нет сравнимого по зрелости решения."],
    ["Pillow / NumPy", "Готовые примитивы для работы с изображениями: фильтры, масштабирование, тензоры."],
    ["Изоляция",       "Тяжёлая ML-часть отдельно от бизнес-логики. Сервис можно перезапустить, не трогая основную систему."],
    ["REST-интерфейс", "Spring Boot вызывает Python по HTTP через RestTemplate. Никаких JNI и нативных библиотек."],
  ];
  cards.forEach((c, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.5 + col * 6.4, y = 2.2 + row * 2.25;
    s.addShape("roundRect", { x, y, w: 6.2, h: 2.0, fill: { color: C.white }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
    s.addShape("ellipse", { x: x + 0.25, y: y + 0.25, w: 0.5, h: 0.5, fill: { color: C.accent }, line: { color: C.accent } });
    s.addText("✓", { x: x + 0.25, y: y + 0.25, w: 0.5, h: 0.5, fontFace: FB, fontSize: 18, bold: true, color: C.white, align: "center", valign: "middle" });
    s.addText(c[0], { x: x + 0.95, y: y + 0.2, w: 5.1, h: 0.5, fontFace: FH, fontSize: 18, bold: true, color: C.primary });
    s.addText(c[1], { x: x + 0.95, y: y + 0.7, w: 5.1, h: 1.25, fontFace: FB, fontSize: 13, color: C.ink });
  });

  footer(s, 6, TOTAL);
}

// ================= SLIDE 7 — Две стратегии улучшения =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Две стратегии улучшения изображений");

  s.addText("Сервис /enhance поддерживает два режима. Запрос может прислать mode=baseline, mode=neural или mode=auto.", {
    x: 0.5, y: 1.4, w: 12.3, h: 0.6,
    fontFace: FB, fontSize: 14, color: C.muted, italic: true,
  });

  // Baseline
  s.addShape("roundRect", { x: 0.5, y: 2.2, w: 6.1, h: 4.7, fill: { color: C.soft }, line: { color: C.secondary, width: 2 }, rectRadius: 0.15 });
  s.addText("Baseline — Pillow-фильтры", { x: 0.7, y: 2.35, w: 5.7, h: 0.5, fontFace: FH, fontSize: 20, bold: true, color: C.primary });
  s.addText("Детерминированный алгоритм. Обучение не нужно.", { x: 0.7, y: 2.85, w: 5.7, h: 0.4, fontFace: FB, fontSize: 12, italic: true, color: C.muted });
  s.addText(
    [
      { text: "autocontrast — растягивает гистограмму\n", options: { bullet: true } },
      { text: "UnsharpMask — чёткость краёв\n", options: { bullet: true } },
      { text: "Contrast +8% — усиление контраста\n", options: { bullet: true } },
      { text: "Sharpness +15% — резкость", options: { bullet: true } },
    ],
    { x: 0.85, y: 3.4, w: 5.5, h: 2.3, fontFace: FB, fontSize: 14, color: C.ink, paraSpaceAfter: 6 }
  );
  s.addText("Работает всегда, за доли секунды. Результат предсказуем и одинаков на любом ПК.", {
    x: 0.7, y: 5.8, w: 5.7, h: 1.0, fontFace: FB, fontSize: 12, color: C.primary, italic: true,
  });

  // U-Net
  s.addShape("roundRect", { x: 6.8, y: 2.2, w: 6.1, h: 4.7, fill: { color: C.primary }, line: { color: C.primary }, rectRadius: 0.15 });
  s.addText("Neural — U-Net (PyTorch)", { x: 7.0, y: 2.35, w: 5.7, h: 0.5, fontFace: FH, fontSize: 20, bold: true, color: C.white });
  s.addText("Обучаемая свёрточная сеть. Нужны данные и тренировка.", { x: 7.0, y: 2.85, w: 5.7, h: 0.4, fontFace: FB, fontSize: 12, italic: true, color: "CADCFC" });
  s.addText(
    [
      { text: "3 DownBlock (32→64→128)\n", options: { bullet: true } },
      { text: "Bottleneck 256 каналов\n", options: { bullet: true } },
      { text: "3 UpBlock + skip connections\n", options: { bullet: true } },
      { text: "Sigmoid + residual: 0.35·in + 0.65·out", options: { bullet: true } },
    ],
    { x: 7.15, y: 3.4, w: 5.5, h: 2.3, fontFace: FB, fontSize: 14, color: C.white, paraSpaceAfter: 6 }
  );
  s.addText("Даёт лучший результат на сложных кадрах, но требует обучения и весов.", {
    x: 7.0, y: 5.8, w: 5.7, h: 1.0, fontFace: FB, fontSize: 12, color: C.accent, italic: true,
  });

  footer(s, 7, TOTAL);
}

// ================= SLIDE 8 — Почему одной нужно обучаться =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Почему U-Net обучается, а baseline — нет");

  // Baseline
  s.addShape("roundRect", { x: 0.5, y: 1.5, w: 6.1, h: 5.4, fill: { color: C.white }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
  s.addText("Baseline = готовая формула", { x: 0.7, y: 1.6, w: 5.7, h: 0.5, fontFace: FH, fontSize: 18, bold: true, color: C.primary });
  s.addText(
    "Pillow-фильтры — это математические операции над пикселями: " +
    "вычислить среднее, сдвинуть яркость, применить свёртку с фиксированным ядром резкости. " +
    "Все коэффициенты уже зашиты в библиотеку.",
    { x: 0.7, y: 2.15, w: 5.7, h: 2.2, fontFace: FB, fontSize: 13, color: C.ink }
  );
  s.addText("Вывод", { x: 0.7, y: 4.5, w: 5.7, h: 0.4, fontFace: FH, fontSize: 14, bold: true, color: C.accent });
  s.addText(
    "Алгоритм ничего не «помнит». Каждый запуск — один и тот же набор операций. " +
    "Обучать нечего: параметры не являются переменными.",
    { x: 0.7, y: 4.9, w: 5.7, h: 1.9, fontFace: FB, fontSize: 13, color: C.ink, italic: true }
  );

  // U-Net
  s.addShape("roundRect", { x: 6.8, y: 1.5, w: 6.1, h: 5.4, fill: { color: C.white }, line: { color: C.primary, width: 2 }, rectRadius: 0.12 });
  s.addText("U-Net = модель с параметрами", { x: 7.0, y: 1.6, w: 5.7, h: 0.5, fontFace: FH, fontSize: 18, bold: true, color: C.primary });
  s.addText(
    "В свёрточной сети ~11 миллионов весов. При инициализации они случайны — " +
    "сеть на выходе выдаёт шум. Веса должны быть подобраны под задачу " +
    "«взять плохой снимок и получить чистый».",
    { x: 7.0, y: 2.15, w: 5.7, h: 2.2, fontFace: FB, fontSize: 13, color: C.ink }
  );
  s.addText("Подбор весов — это и есть обучение", { x: 7.0, y: 4.4, w: 5.7, h: 0.4, fontFace: FH, fontSize: 14, bold: true, color: C.accent });
  s.addText(
    "Показываем сети пары «испорченный → чистый» снимок, считаем ошибку, " +
    "обратным распространением корректируем веса. Тысячи итераций — и сеть " +
    "запоминает, как выглядит «хороший» гистологический кадр.",
    { x: 7.0, y: 4.8, w: 5.7, h: 2.0, fontFace: FB, fontSize: 13, color: C.ink, italic: true }
  );

  footer(s, 8, TOTAL);
}

// ================= SLIDE 9 — Архитектура U-Net =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Архитектура U-Net");

  s.addText("Encoder → Bottleneck → Decoder. Skip connections передают детали напрямую из encoder в decoder.", {
    x: 0.5, y: 1.4, w: 12.3, h: 0.5, fontFace: FB, fontSize: 14, color: C.muted, italic: true,
  });

  // Блоки encoder
  const layers = [
    { name: "Input\n256×256×3", c: C.white, ink: C.primary },
    { name: "Down 32",  c: C.soft,      ink: C.ink },
    { name: "Down 64",  c: "BFDCE5",    ink: C.ink },
    { name: "Down 128", c: "8CC0CE",    ink: C.white },
    { name: "Bottle\n256", c: C.primary, ink: C.white },
    { name: "Up 128",   c: "8CC0CE",    ink: C.white },
    { name: "Up 64",    c: "BFDCE5",    ink: C.ink },
    { name: "Up 32",    c: C.soft,      ink: C.ink },
    { name: "Output\n256×256×3", c: C.accent, ink: C.white },
  ];
  const w = 1.25, h = 1.4, gap = 0.12;
  const totalW = layers.length * w + (layers.length - 1) * gap;
  const startX = (13.33 - totalW) / 2;
  layers.forEach((l, i) => {
    const x = startX + i * (w + gap);
    const y = 2.2 + (i === 4 ? 0.3 : 0); // bottleneck чуть ниже
    s.addShape("roundRect", { x, y, w, h, fill: { color: l.c }, line: { color: C.primary, width: 1 }, rectRadius: 0.08 });
    s.addText(l.name, { x, y, w, h, fontFace: FB, fontSize: 12, bold: true, color: l.ink, align: "center", valign: "middle" });
  });

  // skip connections — дуги сверху
  const pairs = [[1, 7], [2, 6], [3, 5]];
  pairs.forEach(([a, b]) => {
    const x1 = startX + a * (w + gap) + w / 2;
    const x2 = startX + b * (w + gap) + w / 2;
    s.addShape("line", { x: x1, y: 2.2, w: x2 - x1, h: 0, line: { color: C.accent, width: 2, dashType: "dash" } });
    s.addShape("line", { x: x1, y: 1.9, w: 0, h: 0.3, line: { color: C.accent, width: 2, dashType: "dash" } });
    s.addShape("line", { x: x2, y: 1.9, w: 0, h: 0.3, line: { color: C.accent, width: 2, dashType: "dash" } });
    s.addShape("line", { x: x1, y: 1.9, w: x2 - x1, h: 0, line: { color: C.accent, width: 2, dashType: "dash" } });
  });
  s.addText("skip connections", { x: 5.0, y: 1.55, w: 3.3, h: 0.3, fontFace: FB, fontSize: 12, italic: true, color: C.accent, align: "center" });

  // residual формула
  s.addShape("roundRect", { x: 1.0, y: 4.9, w: 11.3, h: 1.7, fill: { color: C.soft }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
  s.addText("Residual connection на выходе", { x: 1.2, y: 5.0, w: 11, h: 0.4, fontFace: FH, fontSize: 16, bold: true, color: C.primary });
  s.addText("result = 0.35 · input + 0.65 · sigmoid(network(input))", {
    x: 1.2, y: 5.45, w: 11, h: 0.5, fontFace: "Consolas", fontSize: 18, bold: true, color: C.ink, align: "center",
  });
  s.addText("Сеть предсказывает «поправку», а не рисует изображение с нуля — это стабилизирует обучение и не даёт потерять исходные структуры.", {
    x: 1.2, y: 6.0, w: 11, h: 0.5, fontFace: FB, fontSize: 12, color: C.muted, align: "center", italic: true,
  });

  footer(s, 9, TOTAL);
}

// ================= SLIDE 10 — Параметры обучения =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Параметры обучения U-Net");

  const params = [
    ["epochs", "15", "Сколько раз сеть увидит весь датасет. Больше — лучше качество, но дольше и риск переобучения."],
    ["batch_size", "4", "Сколько изображений обрабатывается за один шаг. Маленький — чтобы уместиться в память CPU."],
    ["learning_rate", "0.0005", "Шаг оптимизатора. Слишком большой — расходимость; слишком маленький — очень медленное обучение."],
    ["image_size", "256×256", "Все кадры приводятся к одному разрешению. Также поддерживается 224 — быстрее, но менее детально."],
    ["optimizer", "Adam", "Адаптивный оптимизатор: сам подбирает скорость для каждого веса. Стандарт для CV-задач."],
    ["loss", "L1 + 0.15·edge", "L1 учит точному пикселю, edge-loss (градиенты Собеля) — чётким границам тканей."],
  ];

  params.forEach((p, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.5 + col * 6.4, y = 1.6 + row * 1.75;
    s.addShape("roundRect", { x, y, w: 6.2, h: 1.55, fill: { color: C.white }, line: { color: C.soft, width: 1 }, rectRadius: 0.1 });
    // badge
    s.addShape("roundRect", { x: x + 0.2, y: y + 0.2, w: 1.9, h: 0.55, fill: { color: C.primary }, line: { color: C.primary }, rectRadius: 0.05 });
    s.addText(p[0], { x: x + 0.2, y: y + 0.2, w: 1.9, h: 0.55, fontFace: "Consolas", fontSize: 12, bold: true, color: C.white, align: "center", valign: "middle" });
    s.addText(p[1], { x: x + 2.2, y: y + 0.15, w: 3.9, h: 0.65, fontFace: FH, fontSize: 20, bold: true, color: C.accent });
    s.addText(p[2], { x: x + 0.2, y: y + 0.85, w: 5.9, h: 0.65, fontFace: FB, fontSize: 11, color: C.ink });
  });

  footer(s, 10, TOTAL);
}

// ================= SLIDE 11 — Данные и augmentation =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Откуда берутся обучающие пары");

  s.addText("У нас только «хорошие» снимки — как научить сеть исправлять «плохие»?", {
    x: 0.5, y: 1.45, w: 12.3, h: 0.5, fontFace: FH, fontSize: 18, italic: true, color: C.primary,
  });
  s.addText("Ответ: «плохой» вариант создаётся искусственно — из хорошего. Сеть учится возвращать исходник.", {
    x: 0.5, y: 1.95, w: 12.3, h: 0.5, fontFace: FB, fontSize: 13, color: C.muted,
  });

  // Цепочка деградации
  const steps = [
    ["Оригинал", "чистый кадр"],
    ["Downscale", "0.35–0.7×"],
    ["Gaussian blur", "расфокус"],
    ["Contrast / brightness", "±35%"],
    ["Шум", "гауссовский"],
    ["JPEG артефакты", "qf 25–55"],
    ["«Плохой»", "вход U-Net"],
  ];
  const sw = 1.7, sh = 1.1;
  steps.forEach((st, i) => {
    const x = 0.5 + i * 1.83;
    const fill = i === 0 ? C.accent : (i === steps.length - 1 ? C.primary : C.white);
    const ink = (i === 0 || i === steps.length - 1) ? C.white : C.ink;
    s.addShape("roundRect", { x, y: 2.9, w: sw, h: sh, fill: { color: fill }, line: { color: C.primary, width: 1 }, rectRadius: 0.08 });
    s.addText(st[0], { x, y: 2.95, w: sw, h: 0.45, fontFace: FH, fontSize: 13, bold: true, color: ink, align: "center" });
    s.addText(st[1], { x, y: 3.4, w: sw, h: 0.55, fontFace: FB, fontSize: 11, color: ink, align: "center", italic: true });
    if (i < steps.length - 1) {
      s.addShape("rightTriangle", { x: x + sw + 0.01, y: 3.3, w: 0.12, h: 0.25, fill: { color: C.accent }, line: { color: C.accent }, rotate: 90 });
    }
  });

  // Целевая функция
  s.addShape("roundRect", { x: 1.0, y: 4.6, w: 11.3, h: 2.2, fill: { color: C.soft }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
  s.addText("Функция потерь", { x: 1.2, y: 4.7, w: 11, h: 0.4, fontFace: FH, fontSize: 16, bold: true, color: C.primary });
  s.addText("loss = L1(out, target) + 0.15 · L1(Sobel(out), Sobel(target))", {
    x: 1.2, y: 5.15, w: 11, h: 0.5, fontFace: "Consolas", fontSize: 16, bold: true, color: C.ink, align: "center",
  });
  s.addText(
    "• L1 — средняя абсолютная разница по пикселям: «восстанови цвет»\n" +
    "• Edge-loss на операторе Собеля — «сохрани контуры клеток и ядер»\n" +
    "• Коэффициент 0.15 выбран опытно: не даёт edge-loss заглушить основную задачу",
    { x: 1.4, y: 5.7, w: 10.8, h: 1.1, fontFace: FB, fontSize: 12, color: C.ink, paraSpaceAfter: 3 }
  );

  footer(s, 11, TOTAL);
}

// ================= SLIDE 12 — Что мы запрограммировали =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Что именно мы запрограммировали");

  s.addText("Библиотеки дают кирпичи. Из них мы собрали конкретную работающую систему под задачу гистологии.", {
    x: 0.5, y: 1.4, w: 12.3, h: 0.5,
    fontFace: FB, fontSize: 14, color: C.muted, italic: true,
  });

  // Левая колонка — НАШ КОД
  s.addShape("roundRect", { x: 0.5, y: 2.0, w: 6.1, h: 4.9, fill: { color: C.primary }, line: { color: C.primary }, rectRadius: 0.15 });
  s.addText("Наш код", {
    x: 0.7, y: 2.15, w: 5.7, h: 0.5,
    fontFace: FH, fontSize: 22, bold: true, color: C.white,
  });
  s.addText(
    [
      { text: "Топология U-Net (3 down + bottleneck + 3 up + skip)\n", options: { bullet: { code: "2713" } } },
      { text: "Residual: 0.35·in + 0.65·sigmoid(out)\n", options: { bullet: { code: "2713" } } },
      { text: "Пайплайн деградации из 6 шагов (blur, шум, JPEG…)\n", options: { bullet: { code: "2713" } } },
      { text: "Гибридная loss: L1 + 0.15·edge (Sobel)\n", options: { bullet: { code: "2713" } } },
      { text: "Цикл обучения + PSNR/SSIM после каждой эпохи\n", options: { bullet: { code: "2713" } } },
      { text: "Инференс: паддинг до кратного 8, un-pad, формат\n", options: { bullet: { code: "2713" } } },
      { text: "Baseline: подбор 4 фильтров Pillow (коэф. 1.08, 1.15)\n", options: { bullet: { code: "2713" } } },
      { text: "FastAPI-слой из 8 endpoint-ов\n", options: { bullet: { code: "2713" } } },
      { text: "Subprocess + PID-трекинг + training_status.json\n", options: { bullet: { code: "2713" } } },
      { text: "Java-клиент + синхронизация сессий по timestamp", options: { bullet: { code: "2713" } } },
    ],
    { x: 0.85, y: 2.7, w: 5.5, h: 4.1, fontFace: FB, fontSize: 13, color: C.white, paraSpaceAfter: 4 }
  );

  // Правая колонка — ИЗ БИБЛИОТЕК
  s.addShape("roundRect", { x: 6.8, y: 2.0, w: 6.1, h: 4.9, fill: { color: C.soft }, line: { color: C.secondary, width: 2 }, rectRadius: 0.15 });
  s.addText("Из библиотек", {
    x: 7.0, y: 2.15, w: 5.7, h: 0.5,
    fontFace: FH, fontSize: 22, bold: true, color: C.primary,
  });
  s.addText(
    [
      { text: "torch.nn.Conv2d / BatchNorm / ReLU — кирпичи слоёв\n", options: { bullet: true } },
      { text: "torch.nn.MaxPool2d / ConvTranspose2d — пулинг / апсемпл\n", options: { bullet: true } },
      { text: "torch.optim.Adam — оптимизатор\n", options: { bullet: true } },
      { text: "PIL.ImageOps.autocontrast / UnsharpMask — фильтры\n", options: { bullet: true } },
      { text: "NumPy — массивы пикселей\n", options: { bullet: true } },
      { text: "FastAPI + uvicorn — HTTP-сервер\n", options: { bullet: true } },
      { text: "Spring RestTemplate — Java HTTP-клиент\n", options: { bullet: true } },
      { text: "JPA / Hibernate — хранение сессий обучения", options: { bullet: true } },
    ],
    { x: 7.15, y: 2.7, w: 5.5, h: 4.1, fontFace: FB, fontSize: 13, color: C.ink, paraSpaceAfter: 4 }
  );

  s.addText("≈ 1480 строк прикладного кода (Python ≈ 1000, Java ≈ 480)", {
    x: 0.5, y: 7.0, w: 12.3, h: 0.4, fontFace: FB, fontSize: 12, italic: true, color: C.accent, align: "center",
  });

  footer(s, 12, TOTAL);
}

// ================= SLIDE 13 — Результаты =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "Результаты последнего обучения");

  // Большие числа
  const stats = [
    ["23 080",   "обучающих снимков"],
    ["10.7 ч",   "на CPU"],
    ["0.9749",   "SSIM"],
    ["25.6 дБ",  "PSNR"],
  ];
  stats.forEach((st, i) => {
    const x = 0.5 + i * 3.2;
    s.addShape("roundRect", { x, y: 1.7, w: 3.0, h: 2.4, fill: { color: C.white }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
    s.addText(st[0], { x, y: 1.9, w: 3.0, h: 1.2, fontFace: FH, fontSize: 40, bold: true, color: C.primary, align: "center" });
    s.addText(st[1], { x, y: 3.1, w: 3.0, h: 0.9, fontFace: FB, fontSize: 14, color: C.muted, align: "center", italic: true });
  });

  // Метрики — пояснение
  s.addShape("roundRect", { x: 0.5, y: 4.4, w: 12.3, h: 2.3, fill: { color: C.soft }, line: { color: C.secondary, width: 1 }, rectRadius: 0.12 });
  s.addText("Что означают метрики", { x: 0.7, y: 4.5, w: 12, h: 0.4, fontFace: FH, fontSize: 16, bold: true, color: C.primary });
  s.addText(
    [
      { text: "SSIM (Structural Similarity) — сходство структур: 1.0 = идентично, 0.97+ = визуально неразличимо.\n", options: {} },
      { text: "PSNR (Peak Signal-to-Noise Ratio) — дБ: >25 считается хорошим качеством, >30 — высоким.\n", options: {} },
      { text: "Сеть научилась восстанавливать кадры близко к оригиналу даже после агрессивной деградации.", options: {} },
    ],
    { x: 0.85, y: 4.95, w: 11.8, h: 1.7, fontFace: FB, fontSize: 13, color: C.ink, paraSpaceAfter: 4 }
  );

  footer(s, 13, TOTAL);
}

// ================= SLIDE 14 — REST API =================
{
  const s = pres.addSlide(); bgLight(s);
  title(s, "REST API Python-сервиса");

  const rows = [
    ["POST", "/enhance",         "Улучшить изображение. mode=auto|neural|baseline"],
    ["POST", "/train",           "Запустить обучение (subprocess, PID сохраняется)"],
    ["GET",  "/training/status", "Текущий статус обучения: running / done / error"],
    ["GET",  "/training/history","История запусков с метриками и временем"],
    ["GET",  "/models",          "Список обученных моделей, активная модель"],
    ["GET",  "/metrics",         "SSIM, PSNR, loss — агрегированные по последнему обучению"],
  ];

  // Header
  s.addShape("rect", { x: 0.5, y: 1.6, w: 12.3, h: 0.55, fill: { color: C.primary }, line: { color: C.primary } });
  s.addText("Метод", { x: 0.7, y: 1.6, w: 1.2, h: 0.55, fontFace: FH, fontSize: 14, bold: true, color: C.white, valign: "middle" });
  s.addText("Endpoint", { x: 2.0, y: 1.6, w: 3.5, h: 0.55, fontFace: FH, fontSize: 14, bold: true, color: C.white, valign: "middle" });
  s.addText("Описание", { x: 5.6, y: 1.6, w: 7.0, h: 0.55, fontFace: FH, fontSize: 14, bold: true, color: C.white, valign: "middle" });

  rows.forEach((r, i) => {
    const y = 2.15 + i * 0.65;
    s.addShape("rect", { x: 0.5, y, w: 12.3, h: 0.65, fill: { color: i % 2 ? C.soft : C.white }, line: { color: C.soft } });
    // method pill
    const methodColor = r[0] === "POST" ? C.accent : C.secondary;
    s.addShape("roundRect", { x: 0.7, y: y + 0.13, w: 1.0, h: 0.4, fill: { color: methodColor }, line: { color: methodColor }, rectRadius: 0.05 });
    s.addText(r[0], { x: 0.7, y: y + 0.13, w: 1.0, h: 0.4, fontFace: "Consolas", fontSize: 12, bold: true, color: C.white, align: "center", valign: "middle" });
    s.addText(r[1], { x: 2.0, y, w: 3.5, h: 0.65, fontFace: "Consolas", fontSize: 13, bold: true, color: C.primary, valign: "middle" });
    s.addText(r[2], { x: 5.6, y, w: 7.0, h: 0.65, fontFace: FB, fontSize: 12, color: C.ink, valign: "middle" });
  });

  s.addText("Spring Boot вызывает эти endpoint-ы через RestTemplate в AutoencoderClientService и логирует каждую операцию в ImageProcessingLog.", {
    x: 0.5, y: 6.4, w: 12.3, h: 0.5, fontFace: FB, fontSize: 12, italic: true, color: C.muted, align: "center",
  });

  footer(s, 14, TOTAL);
}

// ================= SLIDE 15 — Итоги =================
{
  const s = pres.addSlide(); bgDark(s);
  s.addShape("rect", { x: 0, y: 0, w: 0.35, h: 7.5, fill: { color: C.accent }, line: { color: C.accent } });

  s.addText("Что у нас получилось", {
    x: 0.8, y: 0.6, w: 12, h: 1.0, fontFace: FH, fontSize: 40, bold: true, color: C.white,
  });
  s.addText("АИС ГИ закрывает весь цикл: от регистрации образца до отчётов начальнику", {
    x: 0.8, y: 1.6, w: 12, h: 0.5, fontFace: FH, fontSize: 18, italic: true, color: "CADCFC",
  });

  const items = [
    ["Единая база",         "Образцы, дела, заключения, протоколы — в одной системе"],
    ["Архив снимков",       "TIFF/JPG с автоматическим превью и журналом загрузок"],
    ["Улучшение качества",  "Два режима: быстрый baseline и обучаемый U-Net"],
    ["Обучение на лету",    "Запуск тренировки из UI, отслеживание прогресса"],
    ["Документы",           "Протоколы и заключения — экспорт в Word"],
    ["Аналитика",           "5 отчётов для начальника БСМЭ — экспорт в Excel"],
  ];
  items.forEach((it, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.8 + col * 6.2, y = 2.5 + row * 1.4;
    s.addShape("ellipse", { x, y: y + 0.1, w: 0.5, h: 0.5, fill: { color: C.accent }, line: { color: C.accent } });
    s.addText("✓", { x, y: y + 0.1, w: 0.5, h: 0.5, fontFace: FB, fontSize: 18, bold: true, color: C.primary, align: "center", valign: "middle" });
    s.addText(it[0], { x: x + 0.7, y, w: 5.3, h: 0.5, fontFace: FH, fontSize: 18, bold: true, color: C.white });
    s.addText(it[1], { x: x + 0.7, y: y + 0.5, w: 5.3, h: 0.8, fontFace: FB, fontSize: 13, color: "CADCFC" });
  });

  s.addShape("rect", { x: 0, y: 7.2, w: 13.33, h: 0.3, fill: { color: C.accent }, line: { color: C.accent } });
  s.addText("Спасибо за внимание", {
    x: 0.8, y: 6.6, w: 12, h: 0.5, fontFace: FH, fontSize: 18, italic: true, color: "CADCFC",
  });
}

// ================= SAVE =================
pres.writeFile({ fileName: "АИС_ГИ_презентация.pptx" }).then((f) => {
  console.log("Saved:", f);
});
