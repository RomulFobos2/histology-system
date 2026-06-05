const fs = require("fs");
const path = require("path");

const WIDTH = 3200;
const HEIGHT = 2050;
const HEADER_HEIGHT = 58;
const LINE_HEIGHT = 30;
const BOX_PADDING = 16;

const COLORS = {
  background: "#f5f7fb",
  card: "#ffffff",
  border: "#38506b",
  header: "#38506b",
  headerText: "#ffffff",
  text: "#1f2937",
  muted: "#5f6f82",
  line: "#56718d",
  pillBg: "#e8eef5",
  noteBg: "#fff9db",
  noteBorder: "#d6b24c",
};

const entities = [
  {
    id: "role",
    title: "Роль",
    x: 60,
    y: 60,
    w: 360,
    fields: [
      "PK id : BIGINT",
      "name : VARCHAR",
      "description : VARCHAR",
    ],
  },
  {
    id: "employee",
    title: "Сотрудник",
    x: 60,
    y: 250,
    w: 520,
    fields: [
      "PK id : BIGINT",
      "lastName : VARCHAR(50)",
      "firstName : VARCHAR(50)",
      "middleName : VARCHAR(50)",
      "birthDate : DATE",
      "position : VARCHAR(200)",
      "username : VARCHAR(50)",
      "password : VARCHAR(200)",
      "isActive : BOOLEAN",
      "needChangePassword : BOOLEAN",
    ],
  },
  {
    id: "forensicCase",
    title: "Судебное дело",
    x: 680,
    y: 60,
    w: 470,
    fields: [
      "PK id : BIGINT",
      "caseNumber : VARCHAR(50)",
      "receiptDate : DATE",
      "description : TEXT",
      "status : ENUM",
    ],
  },
  {
    id: "sample",
    title: "Образец",
    x: 1240,
    y: 60,
    w: 520,
    fields: [
      "PK id : BIGINT",
      "sampleNumber : VARCHAR(50)",
      "receiptDate : DATE",
      "tissueType : ENUM",
      "stainingMethod : ENUM",
      "status : ENUM",
      "notes : TEXT",
    ],
  },
  {
    id: "microscopeImage",
    title: "Микроскопическое изображение",
    x: 1850,
    y: 60,
    w: 600,
    fields: [
      "PK id : BIGINT",
      "originalFilename : VARCHAR(255)",
      "storedFilename : VARCHAR(255)",
      "filePath : VARCHAR(500)",
      "fileSize : BIGINT",
      "contentType : VARCHAR(100)",
      "uploadDate : DATE",
      "description : TEXT",
      "isEnhanced : BOOLEAN",
      "enhancementQuality : ENUM",
      "magnification : VARCHAR(50)",
    ],
  },
  {
    id: "autoencoderModel",
    title: "Модель автоэнкодера",
    x: 2550,
    y: 60,
    w: 560,
    fields: [
      "PK id : BIGINT",
      "modelName : VARCHAR(255)",
      "description : TEXT",
      "trainedDate : DATE",
      "epochs : INT",
      "loss : DOUBLE",
      "validationLoss : DOUBLE",
      "psnr : DOUBLE",
      "ssim : DOUBLE",
      "isActive : BOOLEAN",
    ],
  },
  {
    id: "histologistConclusion",
    title: "Заключение гистолога",
    x: 1240,
    y: 760,
    w: 520,
    fields: [
      "PK id : BIGINT",
      "microscopicDescription : TEXT",
      "diagnosis : VARCHAR(500)",
      "conclusionText : TEXT",
      "conclusionDate : DATE",
    ],
  },
  {
    id: "forensicConclusion",
    title: "Судебно-медицинское заключение",
    x: 1240,
    y: 1040,
    w: 520,
    fields: [
      "PK id : BIGINT",
      "conclusionText : TEXT",
      "conclusionDate : DATE",
      "isFinal : BOOLEAN",
    ],
  },
  {
    id: "researchProtocol",
    title: "Протокол исследования",
    x: 1850,
    y: 900,
    w: 600,
    fields: [
      "PK id : BIGINT",
      "protocolNumber : VARCHAR(50)",
      "createdDate : DATE",
      "protocolText : TEXT",
    ],
  },
  {
    id: "imageProcessingLog",
    title: "Журнал обработки изображений",
    x: 1850,
    y: 1220,
    w: 600,
    fields: [
      "PK id : BIGINT",
      "processedDate : DATE",
      "processingTimeMs : BIGINT",
    ],
  },
  {
    id: "trainingSession",
    title: "Журнал сеансов обучения",
    x: 2550,
    y: 620,
    w: 560,
    fields: [
      "PK id : BIGINT",
      "startedAt : DATETIME",
      "finishedAt : DATETIME",
      "status : VARCHAR(30)",
      "epochs : INT",
      "batchSize : INT",
      "learningRate : DOUBLE",
      "imageSize : INT",
      "datasetSize : INT",
      "loss : DOUBLE",
      "validationLoss : DOUBLE",
      "psnr : DOUBLE",
      "ssim : DOUBLE",
      "modelName : VARCHAR(255)",
      "message : TEXT",
    ],
  },
];

for (const entity of entities) {
  entity.h = HEADER_HEIGHT + BOX_PADDING * 2 + entity.fields.length * LINE_HEIGHT;
}

const byId = Object.fromEntries(entities.map((entity) => [entity.id, entity]));

function escapeXml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function entityAnchor(entityId, side, offset = 0) {
  const entity = byId[entityId];
  const cx = entity.x + entity.w / 2;
  const cy = entity.y + entity.h / 2;

  switch (side) {
    case "top":
      return { x: cx + offset, y: entity.y };
    case "right":
      return { x: entity.x + entity.w, y: cy + offset };
    case "bottom":
      return { x: cx + offset, y: entity.y + entity.h };
    case "left":
      return { x: entity.x, y: cy + offset };
    default:
      throw new Error(`Unknown side: ${side}`);
  }
}

function pathFromPoints(points) {
  return points
    .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`)
    .join(" ");
}

function pill(x, y, text) {
  const width = Math.max(160, text.length * 8 + 28);
  return `
    <g transform="translate(${x - width / 2} ${y - 18})">
      <rect width="${width}" height="36" rx="18" fill="${COLORS.pillBg}" stroke="${COLORS.border}" stroke-width="1"/>
      <text x="${width / 2}" y="23" text-anchor="middle" class="relation-label">${escapeXml(text)}</text>
    </g>
  `;
}

function cardinalityLabel(x, y, text) {
  return `
    <g transform="translate(${x} ${y})">
      <rect x="-26" y="-14" width="${Math.max(52, text.length * 14)}" height="28" rx="10" fill="#ffffff" stroke="${COLORS.border}" stroke-width="1"/>
      <text x="0" y="6" text-anchor="middle" class="cardinality">${escapeXml(text)}</text>
    </g>
  `;
}

function line(points, options = {}) {
  const {
    dashed = false,
    label = null,
    labelX = null,
    labelY = null,
    startCard = null,
    startCardX = null,
    startCardY = null,
    endCard = null,
    endCardX = null,
    endCardY = null,
  } = options;

  const dash = dashed ? `stroke-dasharray="12 10"` : "";
  const parts = [
    `<path d="${pathFromPoints(points)}" fill="none" stroke="${COLORS.line}" stroke-width="4" stroke-linejoin="round" stroke-linecap="round" ${dash}/>`,
  ];

  if (label && labelX !== null && labelY !== null) {
    parts.push(pill(labelX, labelY, label));
  }

  if (startCard && startCardX !== null && startCardY !== null) {
    parts.push(cardinalityLabel(startCardX, startCardY, startCard));
  }

  if (endCard && endCardX !== null && endCardY !== null) {
    parts.push(cardinalityLabel(endCardX, endCardY, endCard));
  }

  return parts.join("\n");
}

function renderEntity(entity) {
  const dividerY = HEADER_HEIGHT;
  const fields = entity.fields
    .map((field, index) => {
      const y = HEADER_HEIGHT + BOX_PADDING + 22 + index * LINE_HEIGHT;
      return `<text x="${entity.x + 20}" y="${entity.y + y}" class="field">${escapeXml(field)}</text>`;
    })
    .join("\n");

  return `
    <g>
      <rect x="${entity.x}" y="${entity.y}" width="${entity.w}" height="${entity.h}" rx="18" fill="${COLORS.card}" stroke="${COLORS.border}" stroke-width="3"/>
      <rect x="${entity.x}" y="${entity.y}" width="${entity.w}" height="${HEADER_HEIGHT}" rx="18" fill="${COLORS.header}"/>
      <rect x="${entity.x}" y="${entity.y + 28}" width="${entity.w}" height="${HEADER_HEIGHT - 28}" fill="${COLORS.header}"/>
      <line x1="${entity.x}" y1="${entity.y + dividerY}" x2="${entity.x + entity.w}" y2="${entity.y + dividerY}" stroke="${COLORS.border}" stroke-width="3"/>
      <text x="${entity.x + entity.w / 2}" y="${entity.y + 37}" text-anchor="middle" class="entity-title">${escapeXml(entity.title)}</text>
      ${fields}
    </g>
  `;
}

const relations = [
  line(
    [
      entityAnchor("role", "bottom"),
      entityAnchor("employee", "top"),
    ],
    {
      label: "назначается",
      labelX: 300,
      labelY: 232,
      startCard: "1",
      startCardX: 330,
      startCardY: 170,
      endCard: "0..N",
      endCardX: 330,
      endCardY: 242,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", -96),
      { x: 620, y: 344 },
      { x: 620, y: 178 },
      entityAnchor("forensicCase", "left"),
    ],
    {
      label: "ответственный эксперт",
      labelX: 690,
      labelY: 300,
      startCard: "0..1",
      startCardX: 640,
      startCardY: 360,
      endCard: "0..N",
      endCardX: 640,
      endCardY: 196,
    }
  ),
  line(
    [
      entityAnchor("forensicCase", "right", 10),
      { x: 1190, y: 188 },
      { x: 1190, y: 210 },
      entityAnchor("sample", "left", 2),
    ],
    {
      label: "содержит",
      labelX: 1190,
      labelY: 160,
      startCard: "1",
      startCardX: 1170,
      startCardY: 138,
      endCard: "0..N",
      endCardX: 1210,
      endCardY: 236,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", -26),
      { x: 900, y: 414 },
      { x: 900, y: 288 },
      { x: 1220, y: 288 },
    ],
    {
      label: "зарегистрировал",
      labelX: 980,
      labelY: 388,
      startCard: "1",
      startCardX: 650,
      startCardY: 404,
      endCard: "0..N",
      endCardX: 1200,
      endCardY: 304,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 42),
      { x: 960, y: 482 },
      { x: 960, y: 356 },
      { x: 1220, y: 356 },
    ],
    {
      label: "назначен гистологом",
      labelX: 1020,
      labelY: 458,
      startCard: "0..1",
      startCardX: 676,
      startCardY: 492,
      endCard: "0..N",
      endCardX: 1200,
      endCardY: 372,
    }
  ),
  line(
    [
      entityAnchor("sample", "right", -18),
      { x: 1800, y: 208 },
      entityAnchor("microscopeImage", "left", -36),
    ],
    {
      label: "имеет изображения",
      labelX: 1795,
      labelY: 174,
      startCard: "1",
      startCardX: 1784,
      startCardY: 232,
      endCard: "0..N",
      endCardX: 1816,
      endCardY: 170,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 108),
      { x: 980, y: 548 },
      { x: 980, y: 520 },
      { x: 1850, y: 520 },
      { x: 1850, y: 340 },
    ],
    {
      label: "загрузил",
      labelX: 1040,
      labelY: 522,
      startCard: "1",
      startCardX: 690,
      startCardY: 560,
      endCard: "0..N",
      endCardX: 1830,
      endCardY: 360,
    }
  ),
  line(
    [
      entityAnchor("microscopeImage", "bottom", -120),
      { x: 2030, y: 470 },
      { x: 1940, y: 470 },
      { x: 1940, y: 270 },
      { x: 1850, y: 270 },
    ],
    {
      label: "оригинал для улучшенного",
      labelX: 2150,
      labelY: 468,
      startCard: "0..1",
      startCardX: 2040,
      startCardY: 496,
      endCard: "0..N",
      endCardX: 1828,
      endCardY: 246,
    }
  ),
  line(
    [
      entityAnchor("sample", "bottom", -90),
      { x: 1410, y: 700 },
      entityAnchor("histologistConclusion", "top", -90),
    ],
    {
      label: "гистологическое заключение",
      labelX: 1560,
      labelY: 700,
      startCard: "1",
      startCardX: 1386,
      startCardY: 670,
      endCard: "0..N",
      endCardX: 1386,
      endCardY: 744,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 154),
      { x: 1080, y: 594 },
      { x: 1080, y: 840 },
      { x: 1240, y: 840 },
    ],
    {
      label: "составил",
      labelX: 1080,
      labelY: 812,
      startCard: "1",
      startCardX: 706,
      startCardY: 606,
      endCard: "0..N",
      endCardX: 1218,
      endCardY: 858,
    }
  ),
  line(
    [
      entityAnchor("sample", "bottom", 0),
      { x: 1500, y: 980 },
      entityAnchor("forensicConclusion", "top"),
    ],
    {
      label: "судебно-медицинское заключение",
      labelX: 1650,
      labelY: 980,
      startCard: "1",
      startCardX: 1476,
      startCardY: 670,
      endCard: "0..N",
      endCardX: 1476,
      endCardY: 1024,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 192),
      { x: 1120, y: 632 },
      { x: 1120, y: 1120 },
      { x: 1240, y: 1120 },
    ],
    {
      label: "подписал",
      labelX: 1120,
      labelY: 1092,
      startCard: "1",
      startCardX: 730,
      startCardY: 646,
      endCard: "0..N",
      endCardX: 1218,
      endCardY: 1138,
    }
  ),
  line(
    [
      entityAnchor("sample", "right", 76),
      { x: 1800, y: 302 },
      { x: 1800, y: 980 },
      entityAnchor("researchProtocol", "left", -10),
    ],
    {
      label: "протокол исследования",
      labelX: 1800,
      labelY: 874,
      startCard: "1",
      startCardX: 1780,
      startCardY: 320,
      endCard: "0..N",
      endCardX: 1818,
      endCardY: 974,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 230),
      { x: 1160, y: 670 },
      { x: 1160, y: 1040 },
      { x: 1850, y: 1040 },
    ],
    {
      label: "создал",
      labelX: 1160,
      labelY: 1012,
      startCard: "1",
      startCardX: 752,
      startCardY: 684,
      endCard: "0..N",
      endCardX: 1828,
      endCardY: 1058,
    }
  ),
  line(
    [
      entityAnchor("microscopeImage", "bottom", 120),
      { x: 2330, y: 470 },
      { x: 2330, y: 1250 },
      entityAnchor("imageProcessingLog", "top", -120),
    ],
    {
      label: "исходное изображение",
      labelX: 2360,
      labelY: 844,
      startCard: "1",
      startCardX: 2346,
      startCardY: 496,
      endCard: "0..N",
      endCardX: 2210,
      endCardY: 1202,
    }
  ),
  line(
    [
      entityAnchor("microscopeImage", "bottom", 200),
      { x: 2410, y: 520 },
      { x: 2410, y: 1300 },
      entityAnchor("imageProcessingLog", "top", 110),
    ],
    {
      label: "улучшенное изображение",
      labelX: 2440,
      labelY: 918,
      startCard: "1",
      startCardX: 2426,
      startCardY: 548,
      endCard: "0..N",
      endCardX: 2326,
      endCardY: 1204,
    }
  ),
  line(
    [
      entityAnchor("autoencoderModel", "bottom", -110),
      { x: 2720, y: 430 },
      { x: 2720, y: 1304 },
      entityAnchor("imageProcessingLog", "right", -16),
    ],
    {
      label: "использована при обработке",
      labelX: 2880,
      labelY: 944,
      startCard: "1",
      startCardX: 2738,
      startCardY: 454,
      endCard: "0..N",
      endCardX: 2470,
      endCardY: 1270,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 268),
      { x: 1188, y: 708 },
      { x: 1188, y: 1380 },
      { x: 1850, y: 1380 },
    ],
    {
      label: "обработал",
      labelX: 1188,
      labelY: 1352,
      startCard: "1",
      startCardX: 774,
      startCardY: 724,
      endCard: "0..N",
      endCardX: 1828,
      endCardY: 1398,
    }
  ),
  line(
    [
      entityAnchor("employee", "right", 306),
      { x: 1220, y: 746 },
      { x: 1220, y: 1600 },
      { x: 2550, y: 1600 },
      entityAnchor("trainingSession", "left", 180),
    ],
    {
      label: "запустил обучение",
      labelX: 1450,
      labelY: 1570,
      startCard: "1",
      startCardX: 792,
      startCardY: 764,
      endCard: "0..N",
      endCardX: 2528,
      endCardY: 1600,
    }
  ),
  line(
    [
      entityAnchor("trainingSession", "top", -120),
      { x: 2710, y: 560 },
      { x: 2710, y: 520 },
      { x: 2830, y: 520 },
      entityAnchor("autoencoderModel", "bottom", 120),
    ],
    {
      dashed: true,
      label: "логическая связь по modelName",
      labelX: 2780,
      labelY: 500,
      startCard: "1",
      startCardX: 2692,
      startCardY: 578,
      endCard: "0..N",
      endCardX: 2850,
      endCardY: 454,
    }
  ),
];

const notes = `
  <g>
    <rect x="80" y="1720" width="860" height="150" rx="18" fill="${COLORS.noteBg}" stroke="${COLORS.noteBorder}" stroke-width="3"/>
    <text x="110" y="1770" class="note-title">Примечание по таблице «Образец»</text>
    <text x="110" y="1812" class="note-text">Уникальность обеспечивается составным ограничением:</text>
    <text x="110" y="1850" class="note-code">(forensic_case_id, sampleNumber)</text>
  </g>
  <g>
    <rect x="1040" y="1720" width="1120" height="180" rx="18" fill="${COLORS.noteBg}" stroke="${COLORS.noteBorder}" stroke-width="3"/>
    <text x="1070" y="1770" class="note-title">Примечание по «Журналу сеансов обучения»</text>
    <text x="1070" y="1812" class="note-text">В текущей БД у TrainingSession нет отдельного внешнего ключа на AutoencoderModel.</text>
    <text x="1070" y="1850" class="note-text">Используется поле modelName, поэтому на схеме связь показана как логическая и пунктирная.</text>
  </g>
  <g>
    <rect x="2280" y="1720" width="840" height="180" rx="18" fill="#edf4ff" stroke="${COLORS.border}" stroke-width="3"/>
    <text x="2310" y="1770" class="note-title">Кардинальности</text>
    <text x="2310" y="1812" class="note-text">1 — ровно одна связанная запись</text>
    <text x="2310" y="1850" class="note-text">0..1 — связь необязательная, не более одной записи</text>
    <text x="2310" y="1888" class="note-text">0..N — связь необязательная, допускает множество записей</text>
  </g>
`;

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}">
  <style>
    .page-title { font: 700 40px "Segoe UI", Arial, sans-serif; fill: ${COLORS.text}; }
    .page-subtitle { font: 400 22px "Segoe UI", Arial, sans-serif; fill: ${COLORS.muted}; }
    .entity-title { font: 700 26px "Segoe UI", Arial, sans-serif; fill: ${COLORS.headerText}; }
    .field { font: 500 21px "Segoe UI", Arial, sans-serif; fill: ${COLORS.text}; }
    .relation-label { font: 700 18px "Segoe UI", Arial, sans-serif; fill: ${COLORS.border}; }
    .cardinality { font: 700 16px "Segoe UI", Arial, sans-serif; fill: ${COLORS.border}; }
    .note-title { font: 700 24px "Segoe UI", Arial, sans-serif; fill: ${COLORS.text}; }
    .note-text { font: 500 21px "Segoe UI", Arial, sans-serif; fill: ${COLORS.text}; }
    .note-code { font: 700 22px Consolas, monospace; fill: ${COLORS.border}; }
  </style>

  <rect width="100%" height="100%" fill="${COLORS.background}"/>
  <text x="60" y="199" class="page-title">ER-диаграмма предметной области «Гистологическая система»</text>
  <text x="60" y="235" class="page-subtitle">11 сущностей, атрибуты и связи; кардинальности подписаны в стиле Crow’s Foot</text>

  ${relations.join("\n")}
  ${entities.map(renderEntity).join("\n")}
  ${notes}
</svg>
`;

const outputPath = path.join(__dirname, "er-diagram-crow-foot.svg");
fs.writeFileSync(outputPath, svg, "utf8");
console.log(`Saved ${outputPath}`);
