# vg3

*vector graphics 3* — инструмент для генерации твёрдотельных 3D-моделей кодом и их экспорта в STL/PNG
(STEP — в планах).

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
                                                STL / PNG
```

| Шаг | Сигнатура | Что делает |
|---|---|---|
| 1. parse | `vg3_model::parse(&str) -> Result<Model>` | JSON → доменное дерево; канонизация в типах |
| 2. evaluate | `vg3_engine::evaluate(&Model, &mut Cache) -> Result<Vec<Output>>` | обход дерева, вызовы OCCT, построение шейпов |
| 3. export | `ExportConfig::export(&[Output])` | STL / рендер PNG |

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
{ "format": "stl", "output": { "type": "single", "filename": "out.stl" }, "tolerance": 0.1 }
{ "format": "stl", "output": { "type": "multi", "path": "out" } }
{ "format": "png", "output": { "type": "single", "filename": "out.png" }, "size": 512, "azimuth": 35, "elevation": 25 }
{ "format": "png", "output": { "type": "multi", "path": "out" } }
```

- `output`: `single` — всё в один файл (`filename`); `multi` — по файлу на экспортируемую часть,
  `<path>/<name>.<ext>` по `name` из `export` модели (каталог создаётся; пустое или повторяющееся
  имя — ошибка).
- `stl`: `tolerance` (по умолчанию `0.1`) — линейная деформация триангуляции.
- `png`: `size` (512), `azimuth` (35), `elevation` (25) — вид камеры.

В `single` несколько экспортируемых `Part` пишутся в **один** файл (общий `Compound` / одно
изображение). Цвет учитывает только PNG; STL его игнорирует.

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

`{ "version": 1, "parts": [ ... ], "export": [ ... ] }`:

- `version` — целое; текущее `1`.
- `parts` — **плоская арена**: список всех узлов в топологическом порядке (узел после тех, на которые
  ссылается).
- `export` — **явный список** экспортируемого (`index`/`name`/`color`), порядок = порядок вывода.
  Что не указано в `export`, не экспортируется. Промежуточный узел — валидная цель экспорта.

**Ссылки (operand)** — всегда целое число: индекс **назад** в `parts` (`index < current`), inline-объектов
нет. Ацикличность гарантирована по построению; `index >= current` — ошибка.

### Базовые типы и канонизация

Канонизация выполняется **при десериализации, в типах** (`try_from`), **не проходом**; после `parse`
дерево всегда каноническое.

| Тип | JSON | Канонизация |
|---|---|---|
| `Scalar` | число (`f64`) | конечное; `−0 → +0` |
| `Angle` | число (`f64`) | конечное; `−0 → +0`; **не** mod 2π |
| `Point2` | `{ "x", "y" }` | компоненты — `Scalar` |
| `Point3` | `{ "x", "y", "z" }` | компоненты — `Scalar` |
| `Vector3` | `{ "dx", "dy", "dz" }` | компоненты — `Scalar`; **не** нормируется (сдвиг) |
| `Normal3` | `{ "dx", "dy", "dz" }` | **единичный**, знак **сохранён**; нулевой → ошибка |

`Normal3` используется и для оси `rotate`, и для нормали `mirror`. Знак оси важен, поэтому `Normal3` его
не канонизирует. `Angle` **не** приводится mod 2π: иначе полный оборот (`revolve` на 2π) схлопнулся бы
в 0. Следствия (избыточность кэша, не ошибка): `rotate` на `θ` и `θ+2π` — разные ключи; `mirror` с `n`
и `−n` — разные формулы.

Сахар (`mirrorXY`, `rotateX`, `circle`, …) существует **только в DSL** и разворачивается в канонические
формы; в JSON не встречается.

### Кривые и контуры

```jsonc
// Curve2 (Point2) / Curve3 (Point3)
{ "type": "line",   "to": Point }
{ "type": "arc",    "via": Point, "to": Point }   // дуга через 3 точки: start(=prev), via, to
{ "type": "spline", "points": [ Point, ... ] }    // интерполяция
// Curve3 only — винтовая линия вокруг +Z через начало:
{ "type": "helix", "pitch": Scalar, "height": Scalar, "right_handed": true }

Profile = { "start": Point2, "edges": [ Curve2... ] }
Path    = { "start": Point3, "edges": [ Curve3... ] }
```

- `arc`/`spline` начинаются в конце предыдущего ребра (или в `start`).
- `helix` начинается в предыдущей точке (радиус/фаза оттуда): число витков `height / pitch`.
- **`Profile` авто-замыкается всегда**; **`Path` — только как секция `loft`**.
- Круг — **двумя `arc`** (`circle` в JSON запрещён; в DSL — сахар).
- Нулевое ребро / самопересечение / незамкнутый контур → ошибка.

### Операции

**Примитивы** (каноническая ориентация; размещение — только `transform`):

```jsonc
box(width, length, height)          // угол в начале, +октант
sphere(radius)                      // центр в начале
cylinder(radius, height)            // основание в начале, ось +Z
cone(radius_bottom, radius_top, height)
torus(major_radius, minor_radius)   // центр в начале, пл. XY
wedge(width, length, height, top_width)
halfspace                           // бесконечный solid z ≤ 0; инструмент для cut
```

**Генерация тел:**

```jsonc
extrude(profile, height)            // из XY вдоль +Z; height > 0
revolve(profile, angle)             // вокруг оси Y; профиль по одну сторону
sweep(profile, path, mode)          // mode: "follow" (default) | "rigid"
loft(sections, ruled)               // default false; секций ≥ 2
```

- `revolve`: профиль не пересекает ось Y, иначе ошибка.
- `sweep`: профиль ставится в начало спины перпендикулярно касательной; `follow` — поворот по спине
  (Frenet), `rigid` — жёсткий перенос. Вдоль `helix` это даёт резьбу.
- `loft`: секции авто-замыкаются; проверяется совместимость (число/порядок рёбер).

**Булевы:** `fuse(parts)`, `cut(base, tools)`, `common(parts)` — пустой список → ошибка; пустой
результат (0 solid) допустим.

**Трансформации:** `transform(target, ops)` применяет `ops` слева-направо:

```jsonc
{ "type": "translate", "value": Vector3 }
{ "type": "rotate",    "center": Point3, "axis": Normal3, "angle": Angle }
{ "type": "mirror",    "center": Point3, "normal": Normal3 }
{ "type": "scale",     "x": Scalar, "y": Scalar, "z": Scalar }   // НЕ Vector3
{ "type": "matrix",    "m": [ Scalar × 16 ] }                    // ROW-MAJOR 4×4
```

**Fillet / chamfer** — один узел, `kind: "fillet" | "chamfer"`:

```jsonc
{ "type": "fillet", "target": operand, "kind": "fillet",
  "radius": { "type": "all", "radius": Scalar }
          | { "type": "expression", "expression": "<Rhai>" } }
```

- Движок обходит рёбра `target`; для каждого вычисляет значение; `≤ 0` → ребро пропускается.
- Для multi-solid `Part` применяется к каждому solid'у.
- **Seam-рёбра** (швы поверхностей — артефакт параметризации) **не участвуют**: движок их не обходит
  и не скругляет (OCCT не умеет).

### Выражения (Rhai)

`expression` — исходник на Rhai; контекст — переменная `edge`:

| Свойство | Тип | Смысл |
|---|---|---|
| `edge.length` | `f64` | длина ребра |
| `edge.curve_type` | `string` | `"line"` \| `"arc"` \| `"spline"` |
| `edge.is_vertical` | `bool` | параллельно оси Z |
| `edge.is_horizontal` | `bool` | лежит в плоскости XY |
| `edge.direction` | `Normal3` | направление (для line) / касательная |
| `edge.radius` | `f64` | радиус дуги; иначе 0 |
| `edge.start`, `edge.end` | `Point3` | начало / конец |

Результат — число; `≤ 0` → пропуск; не число / NaN → ошибка. Лимит `max_operations` = 10 000; IO не
регистрируется. Тернарного `? :` нет — `if cond { a } else { b }`. Seam-рёбра в контекст не попадают.

### Общие правила обработки

- Ссылки — только назад; канонизация — в типах; сахар — только в DSL; одна вещь — один способ.
- **Проверяем всё и падаем рано**: после каждой операции OCCT — `IsDone()` и валидность
  (`BRepCheck_Analyzer`); проблема → явная ошибка с контекстом.
- **После каждого узла — `unify`** (`ShapeUpgrade_UnifySameDomain`): грани на одной поверхности и рёбра
  на одной кривой сливаются. Геометрия не меняется, но BRep каноничен — в частности, `fillet` видит
  целые рёбра, а не нарезанные булевыми куски.
- Допуски — OCCT-дефолты; параметры экспорта — в конфиге экспорта, не в модели.

## Реализовано

- **Примитивы**: `box`, `sphere`, `cylinder`, `cone`, `torus`, `wedge`, `halfspace`.
- **Генерация тел**: `extrude`, `revolve`, `sweep` (`follow`/`rigid`), `loft` (`ruled`).
- **Булевы**: `fuse`, `cut`, `common`.
- **Трансформации**: `translate`, `rotate`, `mirror`, `scale`, `matrix`.
- **Fillet / chamfer** с `radius: all | expression` (Rhai), пропуск швов.
- **Кривые**: `line`, `arc`, `spline`, `helix`.
- **Экспорт**: STL (бинарный), PNG (собственный z-буфер-растеризатор без OpenGL — headless).

Интеграционные проверки: бутылка из туториала OCCT (`bottle.json`), метрическая резьба
(`thread.json` — `sweep` трапеции по `helix`), «золотые» тесты по геометрическим свойствам.

## Архитектура

### Крейты (Cargo workspace в `processor/`)

```
processor/            # самостоятельный Cargo workspace
  crates/
    cache/   vg3-cache   — кэш: Cache/Codec, Key, Noop/Memory/Disk. Зависит только от blake3.
    model/   vg3-model   — IR: Node/Model + parse + канонические типы. Зависит только от serde.
    engine/  vg3-engine  — Node->Part (OCCT через cxx), BrepCodec, evaluate, экспорт STL/PNG.
                           Зависит от vg3-model и vg3-cache. Здесь же native/ и build.rs.
    cli/     vg3         — бинарь: аргументы, конфиги, сборка кэша. Зависит от всех трёх.
    schema/  vg3-schema  — генератор JSON Schema из vg3-model (бинарь, не входит в конвейер).
```

Граф: `vg3-cache -> {}`, `vg3-model -> {}`, `vg3-engine -> {cache, model}`, `vg3 -> {cache, model, engine}`,
`vg3-schema -> {model}`. То есть `vg3-cache` **не может** упомянуть `Node`/`Part` — это гарантируется
компилятором, а не соглашением.

### Доменная модель (`model`)

Доменная модель и её JSON-представление живут **вместе** (serde-атрибуты прямо на типах):

- `Model { version, parts: Vec<Node>, export: Vec<Export> }` — верхний уровень.
- `Node` — узел IR: примитивы, генерация тел, булевы, трансформации, `fillet`.
- Операнд — всегда `usize` (индекс назад).
- `Profile`/`Path`, `Curve2`/`Curve3`.
- Канонические значения: `Scalar`, `Angle`, `Point2/3`, `Vector3`, `Normal3`.

### OCCT-процессор (`engine`)

`evaluate`:

1. **Валидирует ссылки** по всей арене (один `try_map`): каждый операнд — `index < current`.
2. **Merkle-проход**: ключ узла — `H(версия ‖ узел ‖ ключи операндов…)`, bottom-up.
3. **Строит корни**: операнды берутся из кэша по ключу или строятся рекурсивно, затем
   `Node<Part>::evaluate` применяет операцию; результат кладётся в кэш.
4. После **каждой** операции — `IsDone()`, `BRepCheck_Analyzer`, инвариант «только `Solid`».
5. Результат каждого узла нормализуется `unify`.

`Part` — построенная сущность (`Rc` над нативным шейпом; дешёвый клон). Умеет `solid_count()`,
`face_count()`, `volume()`, `bounding_box()`. Не путать с IR-узлом `Node` (описанием).

### Кэш

**Трейт `Cache<K, V>`**: `get`/`put` + provided `wrap_with`; свободная функция `get_or_put(cache, key,
compute)`. Объектно-безопасен. Реализации — `Noop`, `Memory<K, V>` и `store::Disk<K, V, C>`. Склейка:
`back.wrap_with(front)` (CLI: `disk.wrap_with(memory)`).

Кэш **не знает домена**. `Disk` работает с байтами; значения превращает **`Codec<T>`** (`encode`/`decode`).
Домен живёт в `engine`: `BrepCodec: Codec<Part>` (BREP через `BRepTools`) и Merkle-обход (`key_of`).
Ключ — `Fingerprinter::of(value)` (BLAKE3); версия (vg3 + OCCT) подмешивается, чтобы кэш не переиспользовался
при смене семантики.

`BrepCodec` **публичен**, поэтому кэш собирается снаружи:

```rust
let cache = Disk::new(dir, BrepCodec).wrap_with(Memory::default());
let outputs = vg3_engine::evaluate(&model, &mut cache)?;
```

### Нативный слой (`native` + `sys`)

- `sys.rs` — `cxx::bridge`: объявления и opaque-типы (`Shape`, `WireBuilder`, …). Приватный.
- `native/occt.{h,cpp}` — C++-слой, **по одной функции на операцию OCCT** (`BRepPrimAPI_*`,
  `BRepAlgoAPI_*`, `BRepFilletAPI_*`, `BRepOffsetAPI_*`, `BRepBuilderAPI_*`, `StlAPI_Writer`).
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
  "parts": [
    { "type": "box", "width": 20, "length": 20, "height": 5 },
    { "type": "transform", "target": 0,
      "ops": [ { "type": "translate", "value": { "dx": 0, "dy": 0, "dz": 5 } } ] },
    { "type": "cylinder", "radius": 5, "height": 10 },
    { "type": "fuse", "parts": [1, 2] },
    { "type": "fillet", "kind": "fillet", "target": 3,
      "radius": { "type": "expression",
                  "expression": "if edge.is_vertical { 2.0 } else { 0.0 }" } }
  ],
  "export": [ { "index": 4, "name": "plate", "color": { "r": 0.35, "g": 0.6, "b": 0.95 } } ]
}
```

Переиспользование (шестерня): узел-зуб используется дважды — на него ссылаются два `transform`;
переиспользуемый узел занимает одну позицию, сколько бы раз на неё ни ссылались.

## Статус

Rust-движок реализован целиком. Kotlin-фронтенд — в `kt/` ([kt/README.md](kt/README.md)).

Дальше: STEP-экспорт.
