# vg3

*vector graphics 3* — инструмент для генерации твёрдотельных 3D-моделей кодом и их экспорта в STL/STEP/PNG и отчёта JSON.

`vg3` — тонкая обёртка над [OpenCASCADE Technology (OCCT)](https://dev.opencascade.org/): модель
описывается типизированным JSON (IR), лёгкий нативный движок строит OCCT-шейпы и экспортирует их.
Фронтенд (Kotlin) генерирует IR; геометрия считается в движке.

Этот README — о **ядре (Rust-движок + CLI)**. Kotlin-фронтенд (`kt/`) и его использование — в
[kt/README.md](kt/README.md).

## Зачем

Существующие code-CAD инструменты (ZenCAD, CadQuery) завязаны на Python и/или прячут и искажают OCCT.
`vg3` делает ставку на:

- **OCCT-идиоматичность** — операции и типы следуют OCCT, без выдуманных абстракций.
- **Строгость** — типизированный IR; невозможные состояния невыразимы; канонизация — в типах.
- **Минимализм и предсказуемость** — маленький инструмент, который делает одно дело хорошо.

## Конвейер

Три явных шага; CLI лишь оркестрирует их и не содержит собственной геометрии.

```
Kotlin-фронтенд (типизированный DSL) ──serialize──▶ JSON IR
                                                     │
        ┌──────────────────── конвейер vg3 ────────────────────┐
        │  1. parse       2. evaluate            3. export      │
        │  str -> Model   Model -> Vec<Output>   Output -> файл │
        └───────────────────────────────────────────────────────┘
                                                     │
                                                     ▼
                                                STL / STEP / PNG / JSON
```

| Шаг | Сигнатура | Что делает |
|---|---|---|
| 1. parse | `vg3_model::parse(&str) -> Result<Model>` | JSON → доменное дерево; канонизация в типах |
| 2. evaluate | `vg3_engine::evaluate(&Model, &mut Parts, &mut Sketches) -> Result<Vec<Output>>` | обход дерева, вызовы OCCT, построение шейпов |
| 3. export | `ExportConfig::export(&[Output])` | STL / STEP / рендер PNG / отчёт JSON |

Поток **односторонний**: движок только читает IR и никогда не сериализует его обратно. Экспортируется
то, что перечислено в `export` (в этом порядке). Экспортируемый узел может быть и промежуточным.

## Установка и сборка

Требуется OCCT (например, `brew install opencascade`) и Rust toolchain. Если OCCT не в стандартном
месте — задать `OCCT_DIR`.

```sh
cd processor
cargo build                          # бинарь: processor/target/debug/vg3
cargo test
```

Запуск:

```sh
vg3 --model-file model.json \
    --export-config-json '{ "format": "stl", "output": { "type": "single", "filename": "out.stl" } }'

# модель из stdin, без временных файлов:
vg3 --export-config-json '{ "format": "png", "output": { "type": "single", "filename": "out.png" } }' < model.json
```

## CLI

Три входа — IR-модель, **конфиг экспорта** (обязателен) и необязательный **конфиг работы**. Каждый
задаётся либо файлом, либо inline-JSON; `-` как путь означает stdin, а модель без `--model*` читается
из stdin.

```
vg3 [--model-file <PATH> | --model-json <JSON>]
    (--export-config-file <PATH> | --export-config-json <JSON>)
    [--run-config-file <PATH> | --run-config-json <JSON>]
```

### Конфиг экспорта

Размеченное объединение по `format`; раскладка вывода — общий для всех форматов объект `output`.
Десериализуется типизированно (канонизация через `Scalar`, лишние поля — ошибка).

```jsonc
{ "format": "stl",  "output": { "type": "single", "filename": "out.stl" }, "tolerance": 0.1 }
{ "format": "stl",  "output": { "type": "multi", "path": "out" } }
{ "format": "png",  "output": { "type": "single", "filename": "out.png" }, "size": 512, "azimuth": 35, "elevation": 25, "compression": 6 }
{ "format": "png",  "output": { "type": "multi", "path": "out" } }
{ "format": "step", "filename": "out.step" }
{ "format": "json", "filename": "report.json" }
```

- `output` (STL/PNG): `single` — всё в один файл (`filename`); `multi` — по файлу на экспортируемую
  часть, `<path>/<name>.<ext>` по `name` из `export` модели (каталог создаётся; пустое или
  повторяющееся имя — ошибка).
- `stl`: `tolerance` (по умолчанию `0.1`) — линейная деформация триангуляции.
- `png`: `tolerance` (по умолчанию `0.1`) — деформация триангуляции; `size` (512), `azimuth` (35),
  `elevation` (25) — вид камеры; `compression` (`0..=9`, по умолчанию `6`) — уровень DEFLATE для PNG
  (`0` — без сжатия).
- `step`: **всегда один файл** (`filename`), без раскладки `output`; схема AP214 (`AUTOMOTIVE_DESIGN`).
  Каждая часть пишется отдельным изделием с **именем** и **цветом** из `export` (XCAF), поэтому
  предпросмотрщик показывает части разноцветными.
- `json`: **всегда один файл** (`filename`) — **отчёт-метаданные** о телах (геометрия не пишется):
  `{ "version": 1, "bodies": [ { "name", "color"?, "bounds": {min,max}, "volume", "area",
  "solids", "faces", "edges" }, … ] }` (порядок — как в `export`; `color` опускается, если нет).

В `single` несколько экспортируемых `Part` пишутся в **один** файл (общий `Compound` / одно
изображение). Цвет учитывает PNG и STEP (имя и цвет изделия); STL его игнорирует.

### Конфиг работы

Необязательный третий вход (`--run-config-file` / `--run-config-json`) — как запускать (сейчас:
дисковый кэш). По умолчанию **включён** и пишет в системную папку кэша (`~/Library/Caches/vg3` на macOS,
`~/.cache/vg3` на Linux, `%LOCALAPPDATA%\vg3` на Windows — не в `~`).

```jsonc
{}                                          // по умолчанию: диск включён, системная папка
{ "cache": { "dir": "/tmp/vg3-cache" } }    // своя папка
{ "cache": { "enabled": false } }           // только память
```

Приоритет папки: `cache.dir` → `VG3_CACHE_DIR` → системная папка.

## Формат IR (структура)

Структура IR задаётся **генерируемой JSON Schema** — единственным источником правды:

```sh
cd processor && cargo run -p vg3-schema   # -> scheme/vg3.schema.json
```

Схема выводится из Rust-типов `vg3-model` (`schemars`, те же serde-атрибуты, что и десериализация),
поэтому не может разойтись с форматом. Дерево — `oneOf` по `type` (`const`-дискриминатор), структуры —
`additionalProperties: false`, дефолты (`right_handed`, `ruled`) сохранены, операнды — целые индексы.
Файл — вычисляемый артефакт, в git не хранится.

Дальше в этом README — **семантика**, которую схема не выражает.

### Верхний уровень

`{ "version": 1, "sketches": [ ... ], "bodies": [ ... ], "export": [ ... ] }`:

- `version` — целое; текущее `1`.
- `sketches` — **плоская арена 2D**: `Sketch`-узлы (планарные контуры, булевы, трансформации) в
  топологическом порядке. Поле **необязательное** (по умолчанию пусто): модель без 2D-арены валидна.
  Эскизы существуют только для внутренней работы и **не экспортируются**.
- `bodies` — **плоская арена 3D**: список всех узлов в топологическом порядке (узел после тех, на
  которые ссылается).
- `export` — **явный список** экспортируемого (`index`/`name`/`color`), `index` — в `bodies`; порядок =
  порядок вывода. Что не указано в `export`, не экспортируется. Промежуточный узел — валидная цель
  экспорта.

**Ссылки** — всегда целое число: индекс **назад** в своей арене (`index < current`), inline-объектов нет.
Тело может ссылаться на эскиз (`SketchIndex`), но эскиз на тело — никогда: миры независимы. Ацикличность
гарантирована по построению; `index >= current` — ошибка.

### Базовые типы и канонизация

Канонизация выполняется **при десериализации, в типах** (`try_from`), **не проходом**; после `parse`
дерево всегда каноническое.

| Тип | JSON | Канонизация |
|---|---|---|
| `Scalar` | число (`f64`) | конечное; `−0 → +0` |
| `Angle` | число (`f64`) | конечное; `−0 → +0`; **не** mod 2π |
| `Vec2` | `{ "x", "y" }` | компоненты — `Scalar` |
| `Vec3` | `{ "x", "y", "z" }` | компоненты — `Scalar` |

`Vec3` используется и как точка, и как вектор, и как направление (ось `rotate`, нормаль `mirror`) —
различение по смыслу задаёт операция, а не тип. Нормировка не выполняется: как у любой операции OCCT,
корректность входных векторов — забота того, кто пишет JSON. `Angle` **не** приводится mod 2π: иначе
полный оборот (`revolve` на 2π) схлопнулся бы в 0. Следствия (избыточность кэша, не ошибка): `rotate`
на `θ` и `θ+2π` — разные ключи; `mirror` с `n` и `−n` — разные формулы.

Списки, которые обязаны быть непустыми, типизированы как `NonEmpty<T>`: пустой массив отвергается **при
десериализации**, а схема несёт `minItems: 1`. Так типизированы `Sketch.Contour.edges`, `Path.edges`,
`Sketch.Polygon.points`, `Curve2/3.Spline.points`, `Loft.sections`, обе группы `bool`
(`arguments`/`tools`), а также `Polyhedron.points` и внешний список `Polyhedron.faces` (сами грани —
списки индексов).

Сахар (`mirrorXY`, `rotateX`, `circle`, …) существует **только в DSL** и разворачивается в канонические
формы; в JSON не встречается.

### Кривые и контуры

Profile больше нет: 2D-геометрия живёт в арене `sketches`. Свободный контур — это узел `Sketch.Contour`.

```jsonc
// Curve2 (Vec2) / Curve3 (Vec3)
{ "type": "line",   "to": Point }
{ "type": "arc",    "via": Point, "to": Point }   // дуга через 3 точки: start(=prev), via, to
{ "type": "spline", "points": [ Point, ... ] }    // интерполяция
// Curve3 only — винтовая линия вокруг +Z через начало:
{ "type": "helix", "pitch": Scalar, "height": Scalar, "right_handed": true }

Path = { "start": Vec3, "edges": [ Curve3... ] }   // edges непусто (≥1)
```

- `arc`/`spline` начинаются в конце предыдущего ребра (или в `start`).
- `helix` начинается в предыдущей точке (радиус/фаза оттуда): число витков `height / pitch`.
- **`Path` авто-замыкается только как секция `loft`**; иначе — открытая спина для `sweep`.

### Эскизы (2D-арена)

```jsonc
circle(radius)                      // окружность (OCCT `gp_Circ`), центр в начале
polygon(points)                     // замкнутая ломаная по точкам (Vec2)
contour(start, edges)               // свободный 2D-контур (Curve2); edges непусто (≥1)
bool(kind, arguments, tools)        // булевы над эскизами (SketchIndex); как у тел
transform(target, op)               // планарная трансформация (TransformOp2)
fillet2d(target, radius)            // скругление углов; radius — как у fillet (RadiusSpec)
offset2d(target, distance, join?)   // рост (>0) / усадка (<0) контура (BRepOffsetAPI_MakeOffset)
```

- `polygon`/`contour` авто-замыкаются; нулевое ребро / самопересечение → ошибка.
- Узла `rect` в IR **нет** (в OpenCASCADE нет прямоугольника): это чистый DSL-сахар — `polygon` из
  четырёх точек. `circle` — наоборот, полноценный узел (одно ребро `gp_Circ`, не две полуокружности);
  эллипс при необходимости — так же через `gp_Elips`.
- `TransformOp2`: `translate(Vec2)`, `rotate(center: Vec2, angle)`, `mirror(center: Vec2, normal: Vec2)`,
  `scale(value: Vec2)`. (2D-трансформации отдельны от 3D: нет оси/`z`/матрицы.)
- `fillet2d` скругляет углы (вершины, где сходятся ≥2 ребра) и берёт `radius` той же формы, что и
  `fillet`: `all` (постоянный), `selected` (булев предикат) или `expression` (радиус по углу). В
  выражениях оцениваемый элемент — `vertex` (см. ниже); `≤ 0` → угол пропускается.
- `offset2d` смещает **весь контур** (внешний + отверстия) в правильном направлении: у кольца
  внешний контур растёт, а отверстие сжимается (при `|distance|` меньше радиуса отверстия кольцо
  остаётся кольцом). Так же `extrude`/`revolve`/`sweep` строятся по грани целиком, поэтому дырки
  (например, булев результат «большой круг − маленький круг») сохраняются в теле.
- Эскизы не экспортируются; они нужны только как профиль для `extrude`/`revolve`/`sweep`.

### Операции

**Примитивы** (каноническая ориентация; размещение — только `transform`):

```jsonc
box(width, length, height)          // угол в начале, +октант
sphere(radius, angle?)              // центр в начале; angle — сферический клин (2π = вся сфера)
cylinder(radius, height, angle?)    // основание в начале, ось +Z; angle — клин
cone(radius_bottom, radius_top, height, angle?)
torus(major_radius, minor_radius, angle?)   // центр в начале, пл. XY; angle — клин
wedge(width, length, height, top_width)
halfspace                           // бесконечный solid z ≤ 0; инструмент для cut
```

- `angle` (необязательный) делает из примитива **сегмент-клин** вокруг оси/центра
  (`BRepPrimAPI_Make{Sphere,Cylinder,Cone,Torus}` с углом); без него — полный примитив.

```jsonc
{ "type": "polyhedron",
  "points": [ { "x": …, "y": …, "z": … }, … ],   // ≥ 1 точка
  "faces":  [ [ i, j, k, … ], … ] }              // ≥ 1 грань; каждая — индексы точек (≥ 3), по порядку
```

- `faces` ссылаются на позиции в `points`; грани замыкаются движком. Оболочка сшивается
  (`BRepBuilderAPI_Sewing`) и превращается в solid — незамкнутый многогранник → ошибка.

**Генерация тел:**

```jsonc
extrude(profile, height)            // profile: SketchIndex; из XY вдоль +Z; height > 0
revolve(profile, angle)             // profile: SketchIndex; вокруг оси Y; профиль по одну сторону
sweep(profile, path, mode, transition?)   // profile: SketchIndex; mode: "follow" (default) | "rigid"
loft(sections, ruled?, smoothing?, continuity?, parametrization?, max_degree?, skip_compatibility?)
                                    // sections: [Path...]; секций ≥ 2
```

- `revolve`: профиль не пересекает ось Y, иначе ошибка.
- `sweep`: профиль ставится в начало спины перпендикулярно касательной; `follow` — поворот по спине
  (Frenet), `rigid` — жёсткий перенос. Вдоль `helix` это даёт резьбу.
  `transition` — стык труб на изломах спины: `right_corner` (default, в стык), `round_corner`
  (скругление), `transformed` (может дать самопересекающуюся оболочку — тогда ошибка).
- `loft`: секции авто-замыкаются. `ruled` — линейчатые поверхности между секциями (иначе сглаженные);
  `smoothing` — вариационное сглаживание; `continuity` (`c0|c1|c2|c3`); `parametrization`
  (`chord_length|centripetal|iso_parametric`); `max_degree` — макс. степень поверхности;
  `skip_compatibility` (default false) — отключить авто-ориентацию секций против «перекрута».

**Булевы** — один узел на все три операции (`BRepAlgoAPI_BooleanOperation`): `kind` плюс две группы операндов `arguments` (Objects) и `tools` (Tools), которые OCCT объединяет/вычитает/пересекает одним вызовом (обе группы должны быть непустыми):

```jsonc
{ "type": "bool", "kind": "fuse",   "arguments": [a], "tools": [b, c] }   // a ∪ b ∪ c
{ "type": "bool", "kind": "common", "arguments": [a], "tools": [b, c] }   // a ∩ b ∩ c
{ "type": "bool", "kind": "cut",    "arguments": [a], "tools": [b, c] }   // a − b − c
```

Пустой результат (0 solid) допустим.

**Трансформации:** `transform(target, op)` применяет **одну** трансформацию к `target`; композиция — вложенностью узлов:

```jsonc
{ "type": "transform", "target": operand, "op": {
    "type": "translate", "value": Vec3 } }
```

```jsonc
{ "type": "translate", "value": Vec3 }
{ "type": "rotate",    "center": Vec3, "axis": Vec3, "angle": Angle }
{ "type": "mirror",    "center": Vec3, "normal": Vec3 }
{ "type": "scale",     "value": Vec3 }
{ "type": "matrix",    "m": [ Scalar × 16 ] }                    // ROW-MAJOR 4×4
```

Узел несёт **один** `op` (не список): несколько трансформаций выражаются вложенными `transform`-узлами,
а не альтернативным списком — «одна вещь — один способ». Список `ops` в v1 не существует.

**Fillet / chamfer** — один узел, `kind: "fillet" | "chamfer"`:

```jsonc
{ "type": "fillet", "target": operand, "kind": "fillet",
  "radius":
      { "type": "all",        "radius": Scalar }                       // постоянный радиус всем рёбрам
    | { "type": "selected",   "expression": "<Rhai bool>", "radius": Scalar } // выбранным рёбрам
    | { "type": "expression", "expression": "<Rhai>" } }               // радиус по ребру (число)
```

- Движок обходит рёбра `target`; `all` даёт всем один радиус; `selected` берёт радиус, если булев
  предикат истинен; `expression` вычисляет радиус по ребру. `≤ 0` → ребро пропускается.
- Для multi-solid `Part` применяется к каждому solid'у.
- **Seam-рёбра** (швы поверхностей — артефакт параметризации) **не участвуют**: движок их не обходит
  и не скругляет (OCCT не умеет).

**Offset** — утолщение/утоньшение тела смещением оболочек:

```jsonc
{ "type": "offset", "target": operand, "distance": Scalar,
  "join"?: "arc" | "tangent" | "intersection" }   // >0 наружу, <0 внутрь; join по умолчанию arc
```

**Thick solid** — оболочка заданной толщины с открытыми гранями (`BRepOffsetAPI_MakeThickSolid`):

```jsonc
{ "type": "thick_solid", "target": operand, "offset": Scalar,
  "faces": { "type": "selected", "expression": "<Rhai bool>" },   // какие грани убрать (открыть)
  "join"?: "arc" | "tangent" | "intersection" }
```

- `offset` — знаковое смещение оболочки, как у `offset` (OCCT): `>0` — наружу, `<0` — внутрь (полое
  тело — отрицательное, как в туториалах OCCT).
- `faces.selected` — булев предикат по граням (`face`); истинные грани удаляются (становятся
  проёмами). Если ни одна не выбрана, получится замкнутая полая оболочка.

### Выражения (Rhai)

`expression` — исходник на Rhai; выбор радиуса по ребру. В scope — низкоуровневые данные и функции
(без доменного сахара):

**Данные.** Ребро `edge` (точки/векторы — объекты `{x, y, z}`):

| Поле | Смысл |
|---|---|
| `edge.curve_type` | `"line"` \| `"circle"` \| `"ellipse"` \| `"hyperbola"` \| `"parabola"` \| `"bezier"` \| `"bspline"` \| `"offset"` \| `"other"` |
| `edge.length` | длина ребра |
| `edge.radius` | радиус (`circle`/`ellipse` — большая полуось); иначе `0` |
| `edge.start`, `edge.end` | начало / конец |
| `edge.center` | точка на середине параметра |
| `edge.direction` | единичная касательная в `center` |
| `edge.min`, `edge.max` | bbox самого ребра |

Bounding box тела: `box.min`, `box.max`, `box.center`. Оси: `X`, `Y`, `Z` (единичные векторы).
Bbox (как тела, так и ребра/грани) — **точный**, по геометрии, без прибавки допусков субшейпов; поэтому
предикаты «на границе» вроде `is_close(edge.min.z, box.max.z)` дают истину для ребра на верхней грани.

**Элемент `fillet2d`.** Угол `vertex` (вместо `edge`); `profile` — bbox плоского региона:

| Поле | Смысл |
|---|---|
| `vertex.point` | положение вершины |
| `vertex.direction1`, `vertex.direction2` | единичные касательные двух смежных рёбер (от вершины) |
| `vertex.angle` | угол между ними (радианы, `0..π`) |

Пример — скруглить только острые углы: `vertex.angle < 1.5`.

**Элемент `thick_solid`.** Грань `face` (3D); `box` — bbox тела:

| Поле | Смысл |
|---|---|
| `face.normal` | единичная нормаль грани (с учётом ориентации) |
| `face.center` | центр масс грани |
| `face.area` | площадь грани |
| `face.min`, `face.max` | bbox грани |

Пример — открыть верхнюю грань: `is_parallel(face.normal, Z) && is_close(face.center.z, box.max.z)`.

**Функции.** `dot(a, b)`, `cross(a, b)`, `length(v)`, `normalized(v)`, `distance(a, b)`, `angle(a, b)`,
`vec(x, y, z)`, `add(a, b)`, `sub(a, b)`, `scale(v, s)`, `is_close(a, b)`, `is_close_point(a, b)`,
`is_parallel(a, b)`, `is_perpendicular(a, b)` (плюс `abs`/`sqrt`/… из Rhai).

Пример — фаска только на рёбрах верхней грани (минимум ребра по Z совпал с максимумом тела ⇒ ребро
целиком в верхней плоскости):

```
if is_close(edge.min.z, box.max.z) { 2.0 } else { 0.0 }
```

Вертикальные рёбра: `is_parallel(edge.direction, Z)`. Вдоль X: `is_parallel(edge.direction, X)`.
Круглые рёбра: `edge.curve_type == "circle"`.

Результат — число; `≤ 0` → ребро пропускается; не число / NaN → ошибка. Лимит `max_operations` = 10 000;
IO не регистрируется. Тернарного `? :` нет — `if cond { a } else { b }`. Seam-рёбра в контекст не попадают.

### Общие правила обработки

- Ссылки — только назад; канонизация — в типах; сахар — только в DSL; одна вещь — один способ.
- **Проверяем всё и падаем рано**: после каждой операции OCCT — `IsDone()` и валидность
  (`BRepCheck_Analyzer`); проблема → явная ошибка с контекстом.
- **После каждого узла — `unify`** (`ShapeUpgrade_UnifySameDomain`): грани на одной поверхности и рёбра
  на одной кривой сливаются. Геометрия не меняется, но BRep каноничен — в частности, `fillet` видит
  целые рёбра, а не нарезанные булевыми куски.
- Допуски — OCCT-дефолты; параметры экспорта — в конфиге экспорта, не в модели.

## Реализовано

- **Примитивы**: `box`, `sphere`, `cylinder`, `cone`, `torus`, `wedge`, `halfspace`, `polyhedron`.
- **Генерация тел**: `extrude`, `revolve`, `sweep` (`follow`/`rigid`), `loft` (`ruled`).
- **Булевы**: `bool` (fuse / cut / common).
- **Трансформации**: `translate`, `rotate`, `mirror`, `scale`, `matrix`.
- **Fillet / chamfer** с `radius: all | selected | expression` (Rhai), пропуск швов.
- **Offset**: утолщение/утоньшение тела (`BRepOffsetAPI_MakeOffsetShape`), сшивание `join`.
- **Thick solid**: полая оболочка с открытыми гранями (`BRepOffsetAPI_MakeThickSolid`), грани
  выбираются Rhai-выражением, сшивание `join`.
- **Сегменты примитивов**: необязательный `angle` у `sphere`/`cylinder`/`cone`/`torus`.
- **Sweep transition** (`right_corner`/`round_corner`/`transformed`) и настройки loft
  (`smoothing`/`continuity`/`parametrization`/`max_degree`/`skip_compatibility`).
- **Кривые**: `line`, `arc`, `spline`, `helix`.
- **Экспорт**: STL (бинарный), STEP (AP214, один файл, с именами и цветами частей), PNG (собственный z-буфер-растеризатор без OpenGL — headless), JSON (отчёт-метаданные о телах).

Геометрия проверяется **данными**, а не кодом: в `processor/crates/engine/tests/cases/` лежат
пары `<case>_in.json` (модель) и `<case>_out.json` (ожидаемый `json`-отчёт-метаданные). Кейсы —
**узкие**, по одному на узел/вариант модели. `tests/report.rs` обходит все пары и сравнивает
метаданные (числа — с допуском) и следит, что покрыты все типы узлов и не-дефолтные варианты
операций — новый узел без кейса валит тест. `tests/checks.rs` — **независимые оракулы**: замкнутые
формулы (`πR²H`, `w·l·h`, `4πR²`, …) и геометрические инварианты (объём>0, min≤max, …), т.е.
проверка корректности, а не только фиксация поведения. Перегенерировать эталоны после осознанного
изменения: `VG3_UPDATE_EXPECTED=1 cargo test -p vg3-engine --test report` (изменения эталонов
ревьюятся в diff).

## Архитектура

### Крейты (Cargo workspace в `processor/`)

```
processor/            # самостоятельный Cargo workspace
  crates/
    cache/   vg3-cache   — кэш: Cache/Codec, Key, Noop/Memory/Disk. Зависит только от blake3.
    model/   vg3-model   — IR: Body/Model + parse + канонические типы. Зависит только от serde.
    engine/  vg3-engine  — Body->Part (OCCT через cxx), BrepPartCodec/BrepRegionCodec, evaluate, экспорт STL/STEP/PNG/JSON.
                           Зависит от vg3-model и vg3-cache. Здесь же native/ и build.rs.
    cli/     vg3         — бинарь: аргументы, конфиги, сборка кэша. Зависит от всех трёх.
    schema/  vg3-schema  — генератор JSON Schema из vg3-model (бинарь, не входит в конвейер).
```

Граф: `vg3-cache -> {}`, `vg3-model -> {}`, `vg3-engine -> {cache, model}`, `vg3 -> {cache, model, engine}`,
`vg3-schema -> {model}`. То есть `vg3-cache` **не может** упомянуть `Body`/`Part` — это гарантируется
компилятором, а не соглашением.

### Доменная модель (`model`)

Доменная модель и её JSON-представление живут **вместе** (serde-атрибуты прямо на типах):

- `Model { version, sketches: Vec<Sketch>, bodies: Vec<Body>, export: Vec<Export> }` — верхний уровень.
- `Body` — узел 3D IR: примитивы, генерация тел, булевы, трансформации, `fillet`.
- `Sketch` — узел 2D IR: `circle`/`polygon`/`contour`, булевы, трансформации, `fillet2d`/`offset2d`.
  `rect` — только DSL-сахар. Тела ссылаются на эскизы (`SketchIndex`), но не наоборот.
- Операнды — всегда `usize` (индекс назад в своей арене).
- `Sketch.Contour`, `Path` (`Curve2`/`Curve3`).
- Канонические значения: `Scalar`, `Angle`, `Vec2`, `Vec3`.

### OCCT-процессор (`engine`)

`evaluate`:

1. **Валидирует ссылки** по обеим аренам (один `try_map` на каждую): каждый операнд — `index < current`.
2. **Строит эскизы** bottom-up в планарные `Region` (грани) через кэш регионов; ключ эскиза —
   `H(узел ‖ ключи операндов…)`.
3. **Merkle-проход тел**: ключ узла — `H(версия ‖ узел ‖ ключи операндов… ‖ ключи используемых эскизов)`,
   bottom-up (содержимое эскиза, а не его индекс: слот эскиза в узле канонизируется, а ключи
   эскизов подмешиваются отдельно).
4. **Строит корни**: операнды берутся из кэша по ключу или строятся рекурсивно, затем
   `Body<Part>::evaluate` применяет операцию; результат кладётся в кэш.
5. После **каждой** операции — `IsDone()`, `BRepCheck_Analyzer`, инвариант «только `Solid`».
6. Результат каждого узла нормализуется `unify`.

`Part` — построенная сущность (`Rc` над нативным шейпом; дешёвый клон). Умеет `solid_count()`,
`face_count()`, `volume()`, `bounding_box()`. `Region` — её 2D-аналог (грань), тоже `Rc` и дешёвый клон.
Не путать с IR-узлами `Body`/`Sketch` (описаниями).

### Кэш

**Трейт `Cache<K, V>`**: `get`/`put` + provided `wrap_with`; свободная функция `get_or_put(cache, key,
compute)`. Объектно-безопасен. Реализации — `Noop`, `Memory<K, V>` и `store::Disk<K, V, C>`. Склейка:
`back.wrap_with(front)` (CLI: `disk.wrap_with(memory)`).

Кэш **не знает домена**. `Disk` работает с байтами; значения превращает **`Codec<T>`** (`encode`/`decode`).
Домен живёт в `engine`: `BrepPartCodec: Codec<Part>` и `BrepRegionCodec: Codec<Region>` (BREP через
`BRepTools`) плюс Merkle-обход (`key_of`, `sketch_keys`). Ключ — `Fingerprinter::of(value)` (BLAKE3);
в сид подмешиваются хеш запущенного бинарника (любое изменение семантики движка/нативного моста меняет
бинарник) и версия OCCT (динамическая библиотека, в бинарник не входит), чтобы кэш не переиспользовался
при смене семантики. Версия IR-контракта намеренно не подмешивается — важно, каким кодом построена
запись, а не под какой схемой её записали.

Тела и эскизы кэшируются **раздельно** — два независимых кэша (`Cache<Key, Part>` и `Cache<Key, Region>`),
ключи живут в непересекающихся входных доменах, поэтому обе части пишутся в одну папку. Оба кодека
**публичны**, поэтому кэши собираются снаружи:

```rust
let mut parts = Disk::new(dir.clone(), BrepPartCodec).wrap_with(Memory::default());
let mut sketches = Disk::new(dir, BrepRegionCodec).wrap_with(Memory::default());
let outputs = vg3_engine::evaluate(&model, &mut parts, &mut sketches)?;
```

### Нативный слой (`native` + `sys`)

- `sys.rs` — `cxx::bridge`: объявления и opaque-типы (`Shape`, `WireBuilder`, …). Приватный.
- `native/` — C++-слой, **по одной функции на операцию OCCT** (`BRepPrimAPI_*`,
  `BRepAlgoAPI_*`, `BRepFilletAPI_*`, `BRepOffsetAPI_*`, `BRepBuilderAPI_*`, `StlAPI_Writer`,
  `STEPCAFControl_Writer`), разбит по операциям: `primitives`, `transform`, `booleans`, `sweep`,
  `fillet`, `mesh`, `edges`, `brep`, `export` (+ `detail`, `occt_internal.h`). Публичный контракт
  cxx — `occt.{h,cpp}`-объявления в `occt.h`.
- Сборка (`build.rs`) линкует OCCT (`OCCT_DIR` или Homebrew) и компилирует мост.

### Просмотр

`vg3 --model-file model.json --export-config-json '{ "format": "png", "output": { "type": "single", "filename": "model.png" } }'`
— рендер в PNG (ортопроекция, z-буфер, плоскостное затенение), без OpenGL, headless.

`tools/render_stl.py <file.stl> <out.png>` — то же для произвольного STL (dev-утилита).

## Пример IR

Пластина с бобышкой, скруглённая по вертикальным рёбрам (плоская арена; экспортируется узел `4`):

```json
{
  "version": 1,
  "sketches": [],
  "bodies": [
    { "type": "box", "width": 20, "length": 20, "height": 5 },
    { "type": "transform", "target": 0,
      "op": { "type": "translate", "value": { "x": 0, "y": 0, "z": 5 } } },
    { "type": "cylinder", "radius": 5, "height": 10 },
    { "type": "bool", "kind": "fuse", "arguments": [1], "tools": [2] },
    { "type": "fillet", "kind": "fillet", "target": 3,
      "radius": { "type": "selected",
                  "expression": "is_parallel(edge.direction, Z)", "radius": 2.0 } }
  ],
  "export": [ { "index": 4, "name": "plate", "color": { "r": 0.35, "g": 0.6, "b": 0.95 } } ]
}
```

Переиспользование (шестерня): узел-зуб используется дважды — на него ссылаются два `transform`;
переиспользуемый узел занимает одну позицию, сколько бы раз на неё ни ссылались.

## Статус

Rust-движок реализован целиком. Kotlin-фронтенд — в `kt/` ([kt/README.md](kt/README.md)).
