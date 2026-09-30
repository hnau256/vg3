# vg3

*vector graphics 3* — инструмент для генерации твёрдотельных 3D-моделей кодом и их экспорта в STEP/STL.

`vg3` — тонкая обёртка над [OpenCASCADE Technology (OCCT)](https://dev.opencascade.org/): модель
описывается типизированным JSON (IR), лёгкий нативный движок строит OCCT-шейпы и экспортирует их.
Фронтенд (на Kotlin) генерирует IR; геометрия считается в движке.

## Зачем

Существующие code-CAD инструменты (ZenCAD, CadQuery) завязаны на Python и/или прячут и искажают OCCT.
`vg3` делает ставку на:

- **OCCT-идиоматичность** — операции и типы следуют OCCT, без выдуманных абстракций.
- **Строгость** — типизированный IR; невозможные состояния невыразимы; канонизация — в типах.
- **Минимализм и предсказуемость** — маленький инструмент, который делает одно дело хорошо.

## Архитектура

`vg3` — это **библиотека + лёгкая CLI-обёртка**. Вся логика живёт в библиотеке; CLI лишь оркестрирует
три явных шага и не содержит собственной геометрии.

```
Kotlin-фронтенд (типизированный DSL)
        │  сериализация
        ▼
   JSON IR (см. FORMAT.md)
        │
        │  ┌──────────────────────────── конвейер vg3 ────────────────────────────┐
        └─▶│  1. parse       2. evaluate              3. export                     │
           │  str -> Model   Model -> Vec<Output>     Vec<Output> -> bytes        │
           └───────────────────────────────────────────────────────────────────────┘
        │
        ▼
   STL / PNG (STEP — в планах)
```

### Три шага (единый поток)

| Шаг | Сигнатура | Модуль | Что делает |
|---|---|---|---|
| 1. parse | `parse(&str) -> Result<Model>` | `model` | JSON → доменное дерево; канонизация в типах |
| 2. evaluate | `evaluate(&Model, &mut Cache) -> Result<Vec<Output>>` | `engine` | обход дерева, вызовы OCCT, построение шейпов |
| 3. export | `export(&[Output], Format, &Path, &ExportOptions) -> Result<()>` | `export` | STL / рендер PNG |

Поток **односторонний**: движок только читает IR и никогда не сериализует его обратно. Экспортируется
то, что перечислено в `export` (в этом порядке): `Output` — это построенный `Part` вместе с `name` и
опциональным `color`. Экспортируемый узел может быть и промежуточным.

### Крейты (Cargo workspace)

Проект разбит на **4 независимых крейта** — в Rust это workspace-пакеты (аналог Gradle-проектов).
Зависимости объявлены в `Cargo.toml`, и **компилятор физически не даёт** кэшу сослаться на доменные типы:

```
# репозиторий vg3
processor/            # Rust-движок: самостоятельный Cargo workspace
  Cargo.toml
  Cargo.lock
  crates/
    cache/   vg3-cache   — кэш: Cache/Codec, Key, Noop/Memory/Disk/Layered. Зависит только от blake3.
    model/   vg3-model   — IR: Node/Model + parse + канонические типы. Зависит только от serde.
    engine/  vg3-engine  — Node->Part (OCCT через cxx), BrepCodec, evaluate, экспорт STL/PNG.
                           Зависит от vg3-model и vg3-cache. Здесь же native/ и build.rs.
    cli/     vg3         — бинарь: аргументы, конфиги, сборка кэша. Зависит от всех трёх.
    schema/  vg3-schema  — генератор JSON Schema из vg3-model (бинарь, не входит в конвейер).
scheme/               # сюда vg3-schema пишет vg3.schema.json (файл не коммитится)
kt/                   # Kotlin-фронтенд (в разработке)
```

Граф зависимостей (`cargo tree`): `vg3-cache -> {}`, `vg3-model -> {}`,
`vg3-engine -> {cache, model}`, `vg3 -> {cache, model, engine}`, `vg3-schema -> {model}`. То есть
`vg3-cache` **не может** упомянуть `Node`/`Part` — это гарантируется, а не соглашение. Кэш выносится в
отдельную библиотеку как есть.

### Доменная модель (`model`)

Доменная модель и её JSON-представление живут **вместе** (serde-атрибуты прямо на типах). Это осознанный
компромисс: меньше слоёв, быстрее старт.

- `Model { version, parts: Vec<Node>, export: Vec<Export> }` — верхний уровень; `parts` — плоская
  **арена** (все узлы в топологическом порядке, ссылки — только назад); `export` — явный список
  выводимого (`index`/`name`/`color`).
- `Node` — узел IR: примитивы, генерация тел, булевы, трансформации, `fillet`.
- Операнд — всегда `usize`: индекс назад в `parts`; inline-объектов нет.
- `Profile`/`Path` и `Curve2`/`Curve3` (`line`/`arc`/`spline`).
- Канонические скаляры/геометрия: `Scalar`, `Angle`, `Point2`, `Point3`, `Vector3`, `Normal3`.

### Схема IR (`scheme/`)

`scheme/vg3.schema.json` — JSON Schema (draft 2020-12) для IR, **генерируется из типов `model`**
(`schemars`; те же serde-атрибуты, что и десериализация) — не пишется руками и не расходится с
форматом. Дерево — `oneOf` по `type` (`const`-дискриминатор), структуры — `additionalProperties:false`,
дефолты (`right_handed`, `ruled`) сохранены, операнды — целые `usize`.

Файл — **вычисляемый артефакт**, в git не хранится (`.gitignore`; папка `scheme/` остаётся пустой через
`.gitkeep`). Генерация (из `processor/`):

```sh
cargo run -p vg3-schema            # -> scheme/vg3.schema.json
```

Gradle-сборка (этап 3, `kt/`) сама запускает `vg3-schema` и забирает файл оттуда, поэтому схема и
Kotlin-классы всегда строятся из одного ревиза Rust-модели и не могут разойтись. Схема — контракт, но
**не** enforcement инвариантов: конечность `Scalar`, единичность `Normal3` выражаются только в типах,
а не в JSON Schema.

### Канонизация — в типах

Канонизация выполняется **при десериализации, в самих типах** (`TryFrom`/`Deserialize`), а не отдельным
проходом по дереву. После `parse` дерево всегда каноническое.

- `Scalar` — конечное число, `−0 → +0`.
- `Angle` — конечное число, `−0 → +0`; **не** приводится mod 2π (иначе полный оборот `revolve` на 2π
  схлопнулся бы в 0).
- `Normal3` — нормируется в единичный, знак **сохраняется**, нулевой вектор → ошибка.
- `Point2/3`, `Vector3` — компоненты суть `Scalar`.

Все типы реализуют `Hash` по каноническому значению — из этого строится Merkle-ключ кэша (см. «Кэш»).

### OCCT-процессор (`engine`)

`evaluate`:

1. **Валидирует ссылки** по всей арене (один `try_map`): каждый операнд — `index < current`.
   Проверяются все узлы, а не только экспортируемые.
2. **Merkle-проход**: ключ узла — `H(версия ‖ узел ‖ ключи операндов…)`, bottom-up (ссылки назад ⇒
   ключи операндов уже готовы). Структуру обходит тот же `try_map` (`U = Key`).
3. **Строит корни** (`build`): операнды берутся из кэша по ключу или строятся рекурсивно, затем
   `Node<Part>::evaluate` применяет операцию. Результат кладётся в кэш.
4. После **каждой** операции OCCT — проверка: `IsDone()` и валидность (`BRepCheck_Analyzer`), а также
   инвариант **«`Part` состоит только из `Solid`»** (`is_solids_only`). При проблеме — явная ошибка.
5. Результат **каждого** узла нормализуется `unify` (`ShapeUpgrade_UnifySameDomain`): грани на одной
   поверхности и рёбра на одной кривой сливаются. Solid не меняется, но BRep каноничен — в частности,
   `fillet`/`chamfer` видят целые рёбра, а не нарезанные булевыми куски.

`Part` — построенная сущность (`Rc` над нативным шейпом; дешёвый клон — для переиспользования и кэша).
Умеет `solid_count()`, `face_count()`, `volume()`, `bounding_box()` (используется в тестах). Не путать с
IR-узлом `Node` (описанием).

### Кэш

**Трейт `Cache<K, V>`** (`src/cache.rs`): `get`/`put` + provided `wrap_with`; плюс свободная функция
`get_or_put(cache, key, compute)` — «посчитать и положить», она же несёт поток вычисления. Трейт
объектно-безопасен (`get`/`put` работают и на `dyn`). Реализации — `Noop`,
`Memory<K, V>` и `store::Disk<K, V, C>` (файлы). Склейка: `back.wrap_with(front)` — чтение
`front`→`back`, запись в оба (CLI: `disk.wrap_with(memory)`).

**Кэш не знает домена** и выносится в отдельную библиотеку. `Disk` работает с **байтами**: значения
превращает **`Codec<T>`** — это и есть «iso» (`encode`/`decode`, пара (де)сериализаций одним объектом;
в std готового нет — есть только трейды-обёртки вроде `monocle`, но здесь достаточно своего пары
методов). Ключ лишь отдаёт байты для имени файла (`AsRef<[u8]>`).

**Домен живёт в `engine`**: `BrepCodec: Codec<Part>` (BREP через нативные потоки `BRepTools`) и
Merkle-обход узла (`key_of`, знает `Node`). Ключ — `Fingerprinter::of(value)` (generic BLAKE3);
версия (vg3 + OCCT) подмешивается, чтобы кэш не переиспользовался при смене семантики.

`BrepCodec` **публичен**, поэтому кэш собирается снаружи, и движку передаётся уже готовый кэш:

```rust
let cache = Disk::new(dir, BrepCodec).wrap_with(Memory::default()); // или Memory::default()
let outputs = vg3_engine::evaluate(&model, &mut cache)?;
```

Так (де)сериализация остаётся в движке, кэш — просто носитель, а `evaluate` не знает ни про диск,
ни про BREP-кодек.

### Нативный слой (`native` + `sys`)

- `sys.rs` — `cxx::bridge`: объявления функций и opaque-типы (`Shape`, `WireBuilder`, `CompoundBuilder`,
  `LoftBuilder`). Приватный, наружу не торчит.
- `native/occt.{h,cpp}` — C++-слой, **по одной функции на операцию OCCT**
  (`BRepPrimAPI_*`, `BRepAlgoAPI_*`, `BRepPrimAPI_MakePrism/MakeRevol`, `BRepOffsetAPI_MakePipeShell`,
  `BRepOffsetAPI_ThruSections`, `BRepFilletAPI_MakeFillet/MakeChamfer`, `BRepBuilderAPI_*`, `StlAPI_Writer`).
- Логика — в OCCT; C++-слой транслирует Rust-вызовы в OCCT и конвертирует ошибки в исключения, которые
  cxx превращает в `Result`.
- Сборка (`build.rs`) линкует OCCT (путь из `OCCT_DIR` или Homebrew) и компилирует мост.

### Выражения (Rhai)

Радиус `fillet` может быть выражением на **Rhai** с переменной `edge` (`length`, `curve_type`,
`is_vertical`, `is_horizontal`, `direction`, `radius`, `start`, `end`). Векторы/точки — объекты с полями.
Лимит `max_operations` = 10 000, IO не регистрируется. Результат — число; `≤ 0` → ребро пропускается;
не число / NaN → ошибка.

**Seam-рёбра не участвуют**: шов поверхности (артефакт параметризации) не попадает в контекст и не
скругляется (OCCT не умеет скруглять швы).

### CLI (`main`)

Три входа — IR‑модель, **конфиг экспорта** (обязателен) и необязательный **конфиг работы**. Каждый
задаётся либо файлом, либо inline-JSON; `-` как путь означает stdin, а модель без `--model*` читается
из stdin (пайп из фронтенда без временных файлов):

```
vg3 [--model-file <PATH> | --model-json <JSON>]
    (--export-config-file <PATH> | --export-config-json <JSON>)
    [--run-config-file <PATH> | --run-config-json <JSON>]
```

```sh
vg3 --model-file bottle.json --export-config-file export.json
vg3 --export-config-json '{ "format": "png", "output": { "type": "single", "filename": "bottle.png" } }' < bottle.json
```

`main`:

```rust
let model = vg3::model::parse(&model_source)?;              // 1. str   -> Model
let run   = RunConfig::from_json(&run_source)?;             //     конфиг работы
let outputs = vg3::engine::evaluate(&model, &mut cache)?;   // 2. Model -> Vec<Output>
let config = ExportConfig::from_json(&export_source)?;      //     конфиг экспорта
config.export(&outputs)?;                                   // 3. Outputs -> файлы
```

### Конфиг экспорта

Размеченное объединение по `format`; у каждого формата свои параметры. Раскладка вывода — общий для
всех форматов объект `output`. Десериализуется типизированно (канонизация через `Scalar`, лишние
поля — ошибка).

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
дисковый кэш). По умолчанию **включён** и пишет в
системную папку кэша (`~/Library/Caches/vg3` на macOS, `~/.cache/vg3` на Linux, `%LOCALAPPDATA%\vg3`
на Windows — не в `~`).

```jsonc
{}                                          // по умолчанию: диск включён, системная папка
{ "cache": { "dir": "/tmp/vg3-cache" } }    // своя папка
{ "cache": { "enabled": false } }           // только память
```

Приоритет папки: `cache.dir` → `VG3_CACHE_DIR` → системная папка.

## Реализовано

- **Примитивы**: `box`, `sphere`, `cylinder`, `cone`, `torus`, `wedge`, `halfspace` (полупространство
  `z ≤ 0`, инструмент для `cut`).
- **Генерация тел**: `extrude`, `revolve` (ось Y, полный оборот = 2π), `sweep` (`follow`/`rigid`),
  `loft` (`ruled`).
- **Булевы**: `fuse`, `cut`, `common`.
- **Трансформации**: `translate`, `rotate`, `mirror`, `scale`, `matrix` (4×4 row-major).
- **Fillet / chamfer** с `radius: all | expression` (Rhai), пропуск швов.
- **Кривые**: `line`, `arc` (через 3 точки), `spline` (интерполяция), `helix` (точная винтовая линия —
  pcurve на цилиндре + `BRepLib::BuildCurves3d`, по ребру на виток).
- **Экспорт**: STL (бинарный), PNG (рендер: триангуляция из OCCT + собственный z-буфер-растеризатор,
  без OpenGL — работает headless; цвет каждой части из `export`, отсутствие цвета — дефолтный).

### Резьба

`thread.json` — **настоящая метрическая резьба** (M8, шаг 1.25): цилиндр + `sweep` трапециевидного
профиля по `helix`. Свип ставит профиль в начало спины (профиль X — радиаль, Y — вдоль оси),
`mode: follow` (Frenet). Тест `helical_thread_adds_a_ridge_to_the_cylinder` проверяет, что тело —
один solid с объёмом «цилиндр + гребень». (Круговая резьба — тем же способом с круглым профилем.)

### Интеграционные проверки

- **Бутылка из туториала OCCT** (`bottle.json`): скруглённый профиль → `extrude` → `fillet` вертикальных
  рёбер (r=2.5) → горлышко-цилиндр (r=7.5, h=7) с **резьбой** (`sweep` трапеции по `helix`) → `fuse`.
  Тест `opencascade_bottle_builds` (число solid'ов, bbox, объём). Hollow (`shell`) вне v1 (см. §7 FORMAT.md).
- **Резьба** — см. выше.

### Просмотр

`vg3 --model-file model.json --export-config-json '{ "format": "png", "output": { "type": "single", "filename": "model.png" } }'` — рендер в PNG
(ортопроекция, z-буфер, плоскостное затенение). Триангуляцию даёт OCCT, рисует собственный
растеризатор — без OpenGL, работает headless. PNG собирается встроенным энкодером (без зависимостей).

`tools/render_stl.py <file.stl> <out.png>` — то же для произвольного STL (dev-утилита).

## Решения (зафиксировано)

- **`Angle` не mod 2π** — иначе полный оборот невыразим. Цена: `rotate` на `θ` и `θ+2π` — разные ключи
  кэша при одинаковом результате (избыточность, не ошибка).
- **Rhai без `? :`** — тернарного оператора нет; в каноне используется `if cond { a } else { b }`.
- **Швы пропускаются** — seam-рёбра не видны в выражениях и не скругляются.
- **Один файл на все экспортируемые `Part`** — STL пишется как единый soup.
- **STL сейчас, STEP позже** — схема STEP (AP214/…) станет CLI-параметром.
- **Кэш — трейт `Cache`** (Noop/Memory/Disk + `wrap_with`), движок принимает его параметром.
  Merkle-ключ (BLAKE3 + версия) — деталь `get_or_evaluate`. Диск — opt-in через `VG3_CACHE_DIR`.

## Принципы

- **Инструмент, а не фреймворк.** Не решаем за пользователя, что для него хорошо; не диктуем, как
  им пользоваться; не пытаемся быть субъектом. Только предсказуемый инструмент.
- **Unix-way.** Маленькая программа, которая хорошо делает своё дело.
- **Предсказуемость.** Явные ошибки, никаких тихих деградаций. Каждая операция проверяется — статус
  выполнения (`IsDone()`) и валидность результата (`BRepCheck_Analyzer`). Проблему выявляем **как можно
  раньше** и падаем громко, а не получаем молча неверный результат.
- **OCCT-идиоматичность.** Названия и операции — в терминах OCCT.
- **Одно — одним способом.** Никаких альтернативных путей сделать одну вещь.
- **Тонкая Rust-обёртка.** Минимум своего кода; максимум логики — в OCCT.

## Сборка и запуск

Требуется OCCT (например, `brew install opencascade`). Если он не в стандартном месте — задать `OCCT_DIR`.

Rust-движок живёт в `processor/` (самостоятельный Cargo workspace) — команды выполняются оттуда:

```sh
cd processor
cargo build
cargo run -- --model-file crates/engine/tests/fixtures/fillet.json \
    --export-config-json '{ "format": "stl", "output": { "type": "single", "filename": "out.stl" } }'
cargo test
```

## Статус

Rust-движок реализован целиком по [FORMAT.md](FORMAT.md): весь IR, геометрия, экспорт в STL/PNG, кэш
(Merkle-ключ; память + диск), «золотые» тесты по геометрическим свойствам. Kotlin-фронтенд — отдельно,
позже.

Дальше: STEP-экспорт, Kotlin-DSL.
