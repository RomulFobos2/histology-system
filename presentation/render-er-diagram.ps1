Add-Type -AssemblyName System.Drawing

$width = 3200
$height = 2050
$headerHeight = 58
$lineHeight = 30
$boxPadding = 16
$outputPath = Join-Path $PSScriptRoot "er-diagram-crow-foot.png"

$colors = @{
    Background = [System.Drawing.ColorTranslator]::FromHtml("#f5f7fb")
    Card       = [System.Drawing.ColorTranslator]::FromHtml("#ffffff")
    Border     = [System.Drawing.ColorTranslator]::FromHtml("#38506b")
    Header     = [System.Drawing.ColorTranslator]::FromHtml("#38506b")
    HeaderText = [System.Drawing.ColorTranslator]::FromHtml("#ffffff")
    Text       = [System.Drawing.ColorTranslator]::FromHtml("#1f2937")
    Muted      = [System.Drawing.ColorTranslator]::FromHtml("#5f6f82")
    Line       = [System.Drawing.ColorTranslator]::FromHtml("#56718d")
    PillBg     = [System.Drawing.ColorTranslator]::FromHtml("#e8eef5")
    NoteBg     = [System.Drawing.ColorTranslator]::FromHtml("#fff9db")
    NoteBorder = [System.Drawing.ColorTranslator]::FromHtml("#d6b24c")
    InfoBg     = [System.Drawing.ColorTranslator]::FromHtml("#edf4ff")
}

$fonts = @{
    PageTitle    = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
    PageSubtitle = New-Object System.Drawing.Font("Segoe UI", 13, [System.Drawing.FontStyle]::Regular)
    EntityTitle  = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    Field        = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Regular)
    Relation     = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
    Cardinality  = New-Object System.Drawing.Font("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)
    NoteTitle    = New-Object System.Drawing.Font("Segoe UI", 15, [System.Drawing.FontStyle]::Bold)
    NoteText     = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Regular)
    NoteCode     = New-Object System.Drawing.Font("Consolas", 12, [System.Drawing.FontStyle]::Bold)
}

function New-RoundedRectanglePath {
    param(
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius
    )

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $diameter = $Radius * 2

    $path.AddArc($X, $Y, $diameter, $diameter, 180, 90)
    $path.AddArc($X + $Width - $diameter, $Y, $diameter, $diameter, 270, 90)
    $path.AddArc($X + $Width - $diameter, $Y + $Height - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($X, $Y + $Height - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()

    return $path
}

function Measure-Text {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font
    )

    return $Graphics.MeasureString($Text, $Font)
}

function Draw-RoundedBox {
    param(
        [System.Drawing.Graphics]$Graphics,
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height,
        [float]$Radius,
        [System.Drawing.Color]$FillColor,
        [System.Drawing.Color]$BorderColor,
        [float]$BorderWidth = 2
    )

    $path = New-RoundedRectanglePath -X $X -Y $Y -Width $Width -Height $Height -Radius $Radius
    $brush = New-Object System.Drawing.SolidBrush($FillColor)
    $pen = New-Object System.Drawing.Pen($BorderColor, $BorderWidth)

    $Graphics.FillPath($brush, $path)
    $Graphics.DrawPath($pen, $path)

    $brush.Dispose()
    $pen.Dispose()
    $path.Dispose()
}

function Draw-TextCentered {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [System.Drawing.Color]$Color,
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height
    )

    $brush = New-Object System.Drawing.SolidBrush($Color)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $Graphics.DrawString($Text, $Font, $brush, (New-Object System.Drawing.RectangleF($X, $Y, $Width, $Height)), $format)
    $brush.Dispose()
    $format.Dispose()
}

function Draw-TextLeft {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [System.Drawing.Font]$Font,
        [System.Drawing.Color]$Color,
        [float]$X,
        [float]$Y,
        [float]$Width,
        [float]$Height
    )

    $brush = New-Object System.Drawing.SolidBrush($Color)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Near
    $format.LineAlignment = [System.Drawing.StringAlignment]::Near
    $Graphics.DrawString($Text, $Font, $brush, (New-Object System.Drawing.RectangleF($X, $Y, $Width, $Height)), $format)
    $brush.Dispose()
    $format.Dispose()
}

function Draw-Pill {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [float]$CenterX,
        [float]$CenterY
    )

    $size = Measure-Text -Graphics $Graphics -Text $Text -Font $fonts.Relation
    $width = [Math]::Max(160, $size.Width + 28)
    $height = 36
    $x = $CenterX - ($width / 2)
    $y = $CenterY - ($height / 2)

    Draw-RoundedBox -Graphics $Graphics -X $x -Y $y -Width $width -Height $height -Radius 18 -FillColor $colors.PillBg -BorderColor $colors.Border -BorderWidth 1
    Draw-TextCentered -Graphics $Graphics -Text $Text -Font $fonts.Relation -Color $colors.Border -X $x -Y $y -Width $width -Height $height
}

function Draw-Badge {
    param(
        [System.Drawing.Graphics]$Graphics,
        [string]$Text,
        [float]$CenterX,
        [float]$CenterY
    )

    $size = Measure-Text -Graphics $Graphics -Text $Text -Font $fonts.Cardinality
    $width = [Math]::Max(52, $size.Width + 22)
    $height = 28
    $x = $CenterX - ($width / 2)
    $y = $CenterY - ($height / 2)

    Draw-RoundedBox -Graphics $Graphics -X $x -Y $y -Width $width -Height $height -Radius 10 -FillColor $colors.Card -BorderColor $colors.Border -BorderWidth 1
    Draw-TextCentered -Graphics $Graphics -Text $Text -Font $fonts.Cardinality -Color $colors.Border -X $x -Y $y -Width $width -Height $height
}

function Draw-Polyline {
    param(
        [System.Drawing.Graphics]$Graphics,
        [array]$Points,
        [bool]$Dashed = $false
    )

    $pen = New-Object System.Drawing.Pen($colors.Line, 4)
    if ($Dashed) {
        $pen.DashPattern = @(6, 5)
    }

    for ($i = 0; $i -lt ($Points.Count - 1); $i++) {
        $p1 = $Points[$i]
        $p2 = $Points[$i + 1]
        $Graphics.DrawLine($pen, [float]$p1.x, [float]$p1.y, [float]$p2.x, [float]$p2.y)
    }

    $pen.Dispose()
}

function Draw-Relation {
    param(
        [System.Drawing.Graphics]$Graphics,
        [hashtable]$Relation
    )

    Draw-Polyline -Graphics $Graphics -Points $Relation.points -Dashed $Relation.dashed
    Draw-Pill -Graphics $Graphics -Text $Relation.label -CenterX $Relation.labelX -CenterY $Relation.labelY
    Draw-Badge -Graphics $Graphics -Text $Relation.startCard -CenterX $Relation.startCardX -CenterY $Relation.startCardY
    Draw-Badge -Graphics $Graphics -Text $Relation.endCard -CenterX $Relation.endCardX -CenterY $Relation.endCardY
}

function Draw-Entity {
    param(
        [System.Drawing.Graphics]$Graphics,
        [hashtable]$Entity
    )

    Draw-RoundedBox -Graphics $Graphics -X $Entity.x -Y $Entity.y -Width $Entity.w -Height $Entity.h -Radius 18 -FillColor $colors.Card -BorderColor $colors.Border -BorderWidth 3

    $headerBrush = New-Object System.Drawing.SolidBrush($colors.Header)
    $headerRectTop = New-Object System.Drawing.RectangleF($Entity.x, $Entity.y, $Entity.w, 28)
    $headerRectBottom = New-Object System.Drawing.RectangleF($Entity.x, $Entity.y + 28, $Entity.w, $headerHeight - 28)
    $Graphics.FillRectangle($headerBrush, $headerRectTop)
    $Graphics.FillRectangle($headerBrush, $headerRectBottom)
    $headerBrush.Dispose()

    $borderPen = New-Object System.Drawing.Pen($colors.Border, 3)
    $Graphics.DrawLine($borderPen, [float]$Entity.x, [float]($Entity.y + $headerHeight), [float]($Entity.x + $Entity.w), [float]($Entity.y + $headerHeight))
    $borderPen.Dispose()

    Draw-TextCentered -Graphics $Graphics -Text $Entity.title -Font $fonts.EntityTitle -Color $colors.HeaderText -X $Entity.x -Y $Entity.y -Width $Entity.w -Height $headerHeight

    for ($i = 0; $i -lt $Entity.fields.Count; $i++) {
        $fieldY = $Entity.y + $headerHeight + $boxPadding + 2 + ($i * $lineHeight)
        Draw-TextLeft -Graphics $Graphics -Text $Entity.fields[$i] -Font $fonts.Field -Color $colors.Text -X ($Entity.x + 18) -Y $fieldY -Width ($Entity.w - 36) -Height $lineHeight
    }
}

function Get-Anchor {
    param(
        [hashtable]$Entity,
        [string]$Side,
        [float]$Offset = 0
    )

    $cx = $Entity.x + ($Entity.w / 2)
    $cy = $Entity.y + ($Entity.h / 2)

    switch ($Side) {
        "top"    { return @{ x = $cx + $Offset; y = $Entity.y } }
        "right"  { return @{ x = $Entity.x + $Entity.w; y = $cy + $Offset } }
        "bottom" { return @{ x = $cx + $Offset; y = $Entity.y + $Entity.h } }
        "left"   { return @{ x = $Entity.x; y = $cy + $Offset } }
    }
}

$entities = @(
    @{ id = "role"; title = "Роль"; x = 60; y = 60; w = 360; fields = @("PK id : BIGINT", "name : VARCHAR", "description : VARCHAR") },
    @{ id = "employee"; title = "Сотрудник"; x = 60; y = 250; w = 520; fields = @("PK id : BIGINT", "lastName : VARCHAR(50)", "firstName : VARCHAR(50)", "middleName : VARCHAR(50)", "birthDate : DATE", "position : VARCHAR(200)", "username : VARCHAR(50)", "password : VARCHAR(200)", "isActive : BOOLEAN", "needChangePassword : BOOLEAN") },
    @{ id = "forensicCase"; title = "Судебное дело"; x = 680; y = 60; w = 470; fields = @("PK id : BIGINT", "caseNumber : VARCHAR(50)", "receiptDate : DATE", "description : TEXT", "status : ENUM") },
    @{ id = "sample"; title = "Образец"; x = 1240; y = 60; w = 520; fields = @("PK id : BIGINT", "sampleNumber : VARCHAR(50)", "receiptDate : DATE", "tissueType : ENUM", "stainingMethod : ENUM", "status : ENUM", "notes : TEXT") },
    @{ id = "microscopeImage"; title = "Микроскопическое изображение"; x = 1850; y = 60; w = 600; fields = @("PK id : BIGINT", "originalFilename : VARCHAR(255)", "storedFilename : VARCHAR(255)", "filePath : VARCHAR(500)", "fileSize : BIGINT", "contentType : VARCHAR(100)", "uploadDate : DATE", "description : TEXT", "isEnhanced : BOOLEAN", "enhancementQuality : ENUM", "magnification : VARCHAR(50)") },
    @{ id = "autoencoderModel"; title = "Модель автоэнкодера"; x = 2550; y = 60; w = 560; fields = @("PK id : BIGINT", "modelName : VARCHAR(255)", "description : TEXT", "trainedDate : DATE", "epochs : INT", "loss : DOUBLE", "validationLoss : DOUBLE", "psnr : DOUBLE", "ssim : DOUBLE", "isActive : BOOLEAN") },
    @{ id = "histologistConclusion"; title = "Заключение гистолога"; x = 1240; y = 760; w = 520; fields = @("PK id : BIGINT", "microscopicDescription : TEXT", "diagnosis : VARCHAR(500)", "conclusionText : TEXT", "conclusionDate : DATE") },
    @{ id = "forensicConclusion"; title = "Судебно-медицинское заключение"; x = 1240; y = 1040; w = 520; fields = @("PK id : BIGINT", "conclusionText : TEXT", "conclusionDate : DATE", "isFinal : BOOLEAN") },
    @{ id = "researchProtocol"; title = "Протокол исследования"; x = 1850; y = 900; w = 600; fields = @("PK id : BIGINT", "protocolNumber : VARCHAR(50)", "createdDate : DATE", "protocolText : TEXT") },
    @{ id = "imageProcessingLog"; title = "Журнал обработки изображений"; x = 1850; y = 1220; w = 600; fields = @("PK id : BIGINT", "processedDate : DATE", "processingTimeMs : BIGINT") },
    @{ id = "trainingSession"; title = "Журнал сеансов обучения"; x = 2550; y = 620; w = 560; fields = @("PK id : BIGINT", "startedAt : DATETIME", "finishedAt : DATETIME", "status : VARCHAR(30)", "epochs : INT", "batchSize : INT", "learningRate : DOUBLE", "imageSize : INT", "datasetSize : INT", "loss : DOUBLE", "validationLoss : DOUBLE", "psnr : DOUBLE", "ssim : DOUBLE", "modelName : VARCHAR(255)", "message : TEXT") }
)

$entityMap = @{}
foreach ($entity in $entities) {
    $entity.h = $headerHeight + ($boxPadding * 2) + ($entity.fields.Count * $lineHeight)
    $entityMap[$entity.id] = $entity
}

$relations = @(
    @{
        points = @((Get-Anchor $entityMap.role "bottom"), (Get-Anchor $entityMap.employee "top"))
        label = "назначается"; labelX = 300; labelY = 232
        startCard = "1"; startCardX = 330; startCardY = 170
        endCard = "0..N"; endCardX = 330; endCardY = 242
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" -96), @{ x = 620; y = 344 }, @{ x = 620; y = 178 }, (Get-Anchor $entityMap.forensicCase "left"))
        label = "ответственный эксперт"; labelX = 690; labelY = 300
        startCard = "0..1"; startCardX = 640; startCardY = 360
        endCard = "0..N"; endCardX = 640; endCardY = 196
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.forensicCase "right" 10), @{ x = 1190; y = 188 }, @{ x = 1190; y = 210 }, (Get-Anchor $entityMap.sample "left" 2))
        label = "содержит"; labelX = 1190; labelY = 160
        startCard = "1"; startCardX = 1170; startCardY = 138
        endCard = "0..N"; endCardX = 1210; endCardY = 236
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" -26), @{ x = 900; y = 414 }, @{ x = 900; y = 288 }, @{ x = 1220; y = 288 })
        label = "зарегистрировал"; labelX = 980; labelY = 388
        startCard = "1"; startCardX = 650; startCardY = 404
        endCard = "0..N"; endCardX = 1200; endCardY = 304
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 42), @{ x = 960; y = 482 }, @{ x = 960; y = 356 }, @{ x = 1220; y = 356 })
        label = "назначен гистологом"; labelX = 1020; labelY = 458
        startCard = "0..1"; startCardX = 676; startCardY = 492
        endCard = "0..N"; endCardX = 1200; endCardY = 372
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.sample "right" -18), @{ x = 1800; y = 208 }, (Get-Anchor $entityMap.microscopeImage "left" -36))
        label = "имеет изображения"; labelX = 1795; labelY = 174
        startCard = "1"; startCardX = 1784; startCardY = 232
        endCard = "0..N"; endCardX = 1816; endCardY = 170
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 108), @{ x = 980; y = 548 }, @{ x = 980; y = 520 }, @{ x = 1850; y = 520 }, @{ x = 1850; y = 340 })
        label = "загрузил"; labelX = 1040; labelY = 522
        startCard = "1"; startCardX = 690; startCardY = 560
        endCard = "0..N"; endCardX = 1830; endCardY = 360
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.microscopeImage "bottom" -120), @{ x = 2030; y = 470 }, @{ x = 1940; y = 470 }, @{ x = 1940; y = 270 }, @{ x = 1850; y = 270 })
        label = "оригинал для улучшенного"; labelX = 2150; labelY = 468
        startCard = "0..1"; startCardX = 2040; startCardY = 496
        endCard = "0..N"; endCardX = 1828; endCardY = 246
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.sample "bottom" -90), @{ x = 1410; y = 700 }, (Get-Anchor $entityMap.histologistConclusion "top" -90))
        label = "гистологическое заключение"; labelX = 1560; labelY = 700
        startCard = "1"; startCardX = 1386; startCardY = 670
        endCard = "0..N"; endCardX = 1386; endCardY = 744
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 154), @{ x = 1080; y = 594 }, @{ x = 1080; y = 840 }, @{ x = 1240; y = 840 })
        label = "составил"; labelX = 1080; labelY = 812
        startCard = "1"; startCardX = 706; startCardY = 606
        endCard = "0..N"; endCardX = 1218; endCardY = 858
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.sample "bottom" 0), @{ x = 1500; y = 980 }, (Get-Anchor $entityMap.forensicConclusion "top"))
        label = "судебно-медицинское заключение"; labelX = 1650; labelY = 980
        startCard = "1"; startCardX = 1476; startCardY = 670
        endCard = "0..N"; endCardX = 1476; endCardY = 1024
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 192), @{ x = 1120; y = 632 }, @{ x = 1120; y = 1120 }, @{ x = 1240; y = 1120 })
        label = "подписал"; labelX = 1120; labelY = 1092
        startCard = "1"; startCardX = 730; startCardY = 646
        endCard = "0..N"; endCardX = 1218; endCardY = 1138
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.sample "right" 76), @{ x = 1800; y = 302 }, @{ x = 1800; y = 980 }, (Get-Anchor $entityMap.researchProtocol "left" -10))
        label = "протокол исследования"; labelX = 1800; labelY = 874
        startCard = "1"; startCardX = 1780; startCardY = 320
        endCard = "0..N"; endCardX = 1818; endCardY = 974
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 230), @{ x = 1160; y = 670 }, @{ x = 1160; y = 1040 }, @{ x = 1850; y = 1040 })
        label = "создал"; labelX = 1160; labelY = 1012
        startCard = "1"; startCardX = 752; startCardY = 684
        endCard = "0..N"; endCardX = 1828; endCardY = 1058
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.microscopeImage "bottom" 120), @{ x = 2330; y = 470 }, @{ x = 2330; y = 1250 }, (Get-Anchor $entityMap.imageProcessingLog "top" -120))
        label = "исходное изображение"; labelX = 2360; labelY = 844
        startCard = "1"; startCardX = 2346; startCardY = 496
        endCard = "0..N"; endCardX = 2210; endCardY = 1202
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.microscopeImage "bottom" 200), @{ x = 2410; y = 520 }, @{ x = 2410; y = 1300 }, (Get-Anchor $entityMap.imageProcessingLog "top" 110))
        label = "улучшенное изображение"; labelX = 2440; labelY = 918
        startCard = "1"; startCardX = 2426; startCardY = 548
        endCard = "0..N"; endCardX = 2326; endCardY = 1204
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.autoencoderModel "bottom" -110), @{ x = 2720; y = 430 }, @{ x = 2720; y = 1304 }, (Get-Anchor $entityMap.imageProcessingLog "right" -16))
        label = "использована при обработке"; labelX = 2880; labelY = 944
        startCard = "1"; startCardX = 2738; startCardY = 454
        endCard = "0..N"; endCardX = 2470; endCardY = 1270
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 268), @{ x = 1188; y = 708 }, @{ x = 1188; y = 1380 }, @{ x = 1850; y = 1380 })
        label = "обработал"; labelX = 1188; labelY = 1352
        startCard = "1"; startCardX = 774; startCardY = 724
        endCard = "0..N"; endCardX = 1828; endCardY = 1398
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.employee "right" 306), @{ x = 1220; y = 746 }, @{ x = 1220; y = 1600 }, @{ x = 2550; y = 1600 }, (Get-Anchor $entityMap.trainingSession "left" 180))
        label = "запустил обучение"; labelX = 1450; labelY = 1570
        startCard = "1"; startCardX = 792; startCardY = 764
        endCard = "0..N"; endCardX = 2528; endCardY = 1600
        dashed = $false
    },
    @{
        points = @((Get-Anchor $entityMap.trainingSession "top" -120), @{ x = 2710; y = 560 }, @{ x = 2710; y = 520 }, @{ x = 2830; y = 520 }, (Get-Anchor $entityMap.autoencoderModel "bottom" 120))
        label = "логическая связь по modelName"; labelX = 2780; labelY = 500
        startCard = "1"; startCardX = 2692; startCardY = 578
        endCard = "0..N"; endCardX = 2850; endCardY = 454
        dashed = $true
    }
)

$bitmap = New-Object System.Drawing.Bitmap($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$graphics.Clear($colors.Background)

Draw-TextLeft -Graphics $graphics -Text "ER-диаграмма предметной области 'Гистологическая система'" -Font $fonts.PageTitle -Color $colors.Text -X 60 -Y 28 -Width 3000 -Height 50
Draw-TextLeft -Graphics $graphics -Text "11 сущностей, атрибуты и связи; кардинальности подписаны в стиле Crow's Foot" -Font $fonts.PageSubtitle -Color $colors.Muted -X 60 -Y 84 -Width 3000 -Height 30

foreach ($relation in $relations) {
    Draw-Relation -Graphics $graphics -Relation $relation
}

foreach ($entity in $entities) {
    Draw-Entity -Graphics $graphics -Entity $entity
}

Draw-RoundedBox -Graphics $graphics -X 80 -Y 1720 -Width 860 -Height 150 -Radius 18 -FillColor $colors.NoteBg -BorderColor $colors.NoteBorder -BorderWidth 3
Draw-TextLeft -Graphics $graphics -Text "Примечание по таблице 'Образец'" -Font $fonts.NoteTitle -Color $colors.Text -X 110 -Y 1746 -Width 800 -Height 30
Draw-TextLeft -Graphics $graphics -Text "Уникальность обеспечивается составным ограничением:" -Font $fonts.NoteText -Color $colors.Text -X 110 -Y 1790 -Width 800 -Height 30
Draw-TextLeft -Graphics $graphics -Text "(forensic_case_id, sampleNumber)" -Font $fonts.NoteCode -Color $colors.Border -X 110 -Y 1828 -Width 800 -Height 30

Draw-RoundedBox -Graphics $graphics -X 1040 -Y 1720 -Width 1120 -Height 180 -Radius 18 -FillColor $colors.NoteBg -BorderColor $colors.NoteBorder -BorderWidth 3
Draw-TextLeft -Graphics $graphics -Text "Примечание по 'Журналу сеансов обучения'" -Font $fonts.NoteTitle -Color $colors.Text -X 1070 -Y 1746 -Width 1040 -Height 30
Draw-TextLeft -Graphics $graphics -Text "В текущей БД у TrainingSession нет отдельного внешнего ключа на AutoencoderModel." -Font $fonts.NoteText -Color $colors.Text -X 1070 -Y 1790 -Width 1040 -Height 30
Draw-TextLeft -Graphics $graphics -Text "Используется поле modelName, поэтому на схеме связь показана как логическая и пунктирная." -Font $fonts.NoteText -Color $colors.Text -X 1070 -Y 1828 -Width 1040 -Height 30

Draw-RoundedBox -Graphics $graphics -X 2280 -Y 1720 -Width 840 -Height 180 -Radius 18 -FillColor $colors.InfoBg -BorderColor $colors.Border -BorderWidth 3
Draw-TextLeft -Graphics $graphics -Text "Кардинальности" -Font $fonts.NoteTitle -Color $colors.Text -X 2310 -Y 1746 -Width 760 -Height 30
Draw-TextLeft -Graphics $graphics -Text "1 — ровно одна связанная запись" -Font $fonts.NoteText -Color $colors.Text -X 2310 -Y 1790 -Width 760 -Height 28
Draw-TextLeft -Graphics $graphics -Text "0..1 — связь необязательная, не более одной записи" -Font $fonts.NoteText -Color $colors.Text -X 2310 -Y 1824 -Width 760 -Height 28
Draw-TextLeft -Graphics $graphics -Text "0..N — связь необязательная, допускает множество записей" -Font $fonts.NoteText -Color $colors.Text -X 2310 -Y 1858 -Width 760 -Height 28

$bitmap.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)

$graphics.Dispose()
$bitmap.Dispose()
foreach ($font in $fonts.Values) {
    $font.Dispose()
}

Write-Output $outputPath
