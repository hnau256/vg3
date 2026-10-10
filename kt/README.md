# vg3 — Kotlin-фронтенд

Типизированный Kotlin-DSL для построения моделей `vg3`: он собирает IR (плоскую арену узлов),
сериализует его и запускает ядро `vg3` для экспорта в STL/STEP/PNG.

Про ядро (Rust-движок + CLI) — в [корневом README](../README.md).

## Модули

```
kt/
  ktcad/        runtime-библиотека: сгенерированный ir, доменный Solid, Vg3, Format
  gen-schema/   кодогенератор: JSON Schema -> Kotlin ir-классы (универсальный)
  gen-solid/    кодогенератор: KSP-процессор, ir.Body -> Solid, ir.Sketch -> Region (+ мапперы, фабрики)
```

`gen-schema` и `gen-solid` — внутренние инструменты сборки библиотеки, **не публикуются**.

## Установка (для пользователя библиотеки)

### 1. OCCT

```sh
brew install opencascade        # или пакет вашей ОС
```

Если OCCT не в стандартном месте — задайте `OCCT_DIR` при сборке движка (шаг 2).

### 2. Rust toolchain и сборка ядра

```sh
cd processor
cargo build                     # бинарь: processor/target/debug/vg3
```

### 3. `vg3` в `PATH`

```sh
export PATH="$PWD/target/debug:$PATH"
```

Либо не трогать `PATH`, а задать переменную окружения при запуске: `VG3_BIN=/path/to/vg3`.
Библиотека ищет `VG3_BIN`, иначе — `vg3` в `PATH`.

### 4. Публикация библиотеки в локальный Maven

Сборка `kt` сама генерирует схему (`cargo run -p vg3-schema`) и Kotlin-классы (KSP), поэтому
Rust-toolchain должен быть доступен:

```sh
cd kt
./gradlew :ktcad:publishToMavenLocal
```

## Использование в своём проекте

В `build.gradle.kts` проекта-потребителя:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.hnau.ktcad:ktcad-ktcad:1.0.0")
}
```

`kotlinx-serialization` приходит транзитивно и уже сгенерирован — **плагин сериализации не нужен**.
Нужен только Kotlin/JVM.

### Пример

```kotlin
import org.hnau.ktcad.*

fun main() {
    val body = box(width = 20.0, length = 20.0, height = 5.0)
    val boss = cylinder(radius = 5.0, height = 10.0)
    val model = fuse(listOf(body, boss))

    Vg3.export(
        parts = listOf(Part(name = "model", solid = model)),
        format = Format.Stl(output = Output.Single("model.stl")),
    )
}
```

`Vg3.export` сериализует модель и конфиг экспорта и запускает `vg3` (`VG3_BIN` или `PATH`).
При ненулевом коде возврата бросается исключение с выводом процесса.

### API

- **Фабрики** (по одному на узел IR): `box`, `sphere`, `cylinder`, `cone`, `torus`, `wedge`, `halfspace`,
  `polyhedron`, `extrude`, `revolve`, `sweep`, `loft`, `bool`, `transform`, `fillet`, `offset`.
  Возвращают `Solid`; `extrude`/`revolve`/`sweep` принимают `Region` (эскиз). `sphere`/`cylinder`/
  `cone`/`torus` принимают необязательный `angle` (сегмент-клин). `box`/`cylinder`/`rect`
  необязательно центрируются по осям: `box` — `centerX`/`centerY`/`centerZ`, `cylinder` — `centerZ`,
  `rect` — `centerX`/`centerY` (`Boolean = false`) — это sugar над `translate`.
- **`Solid`** — immutable доменный узел; операнды — другие `Solid` (не индексы).
- **Булевы:** `+` (fuse), `-` (cut), `*` (common), а также `fuse(parts)`, `cut(base, tools)`,
  `common(parts)` — sugar над единым узлом `bool`. `cut` требует непустой `tools: NonEmptyList<Solid>`.
- **`offset(distance)`** — утолщение (положительное) / утоньшение (отрицательное) тела; есть
  перегрузка с `join: JoinKind` (`ARC`/`TANGENT`/`INTERSECTION`).
- **`thickSolid(offset, expression)`** — полая оболочка толщиной `offset` (отрицательное — внутрь,
  как в OCCT); `expression` — булев Rhai-предикат по граням (`face`, плюс `box`), выбирающий проёмы;
  есть перегрузка с `join`.
- **Трансформации** (возвращают новый `Solid`): `translate`, `up`/`down`, `left`/`right`, `forward`/`back`,
  `scale`/`scaleX/Y/Z`, `rotate(axis, angle, center?)`/`rotateX/Y/Z`, `mirrorXY/XZ/YZ`/`mirror(normal, center?)`.
  Оси: `up=+Z, right=+X, forward=+Y` (и минусы). Углы — радианы (`Math.toRadians(deg)`).
- **Контуры** — 2D живёт в домене `Region`, 3D — в `Path(start, segment…)`; сегменты: `lineTo`/`lineRel`,
  `arcTo`/`arcRel`, `splineTo`, `helix(pitch, height, rightHanded?)` (абсолютные `To`, относительные
  `Rel` — фабрика сама ведёт текущую точку). 2D-контур: `contour(start, segment…)`; готовые:
  `rect(width, height, centerX?, centerY?)`
  (sugar = `polygon` из 4 точек), `circle(radius)` (узел `Region.Circle`, OCCT `gp_Circ`);
  `polygon(first, second, vararg)`; 3D: `polyline(...)`, `polygon(...)`, `Path.close()`. Контур всегда
  имеет ≥1 ребро, поэтому пустой контур невыразим.
- **`Region`** — immutable доменный 2D-узел (эскиз): `circle`/`polygon`/`contour` + булевы
  `fuse`/`cut`/`common` (`+`/`-`/`*`), трансформации `translate(dx, dy)`, `rotate(angle, center?)`,
  `mirror(normal, center?)`, `scale(x, y)`, и `offset2d(distance)`. Скругление углов (имя отражает
  способ выбора радиуса): `fillet2dAll(radius)` (постоянный всем), `fillet2dExpression(expression)`
  (Rhai, переменная `vertex`, радиус по углу) и `fillet2dSelected(expression, radius)` (булев предикат
  отбора углов). Эскизы **не экспортируются** — только служат профилем тел.
- **Построение тел из эскизов:** `Region.extrude(height)`, `Region.revolve(angle)`,
  `Region.sweep(path, mode = FOLLOW, transition = RIGHT_CORNER)` (transition — `TransitionKind` на изломах спины),
  `NonEmptyList<Path>.loft(ruled?, smoothing?, continuity?, parametrization?, maxDegree?, skipCompatibility?)`.
- **`Solid.filletAll(radius, kind = FILLET)`** / **`Solid.filletExpression(expression, kind = FILLET)`** /
  **`Solid.filletSelected(expression, radius, kind = FILLET)`** — скругление (или `kind = CHAMFER`),
  имя отражает способ выбора рёбер: постоянный радиус всем, Rhai-выражение на ребро (число), либо
  булев предикат отбора рёбер с постоянным радиусом.
- **`polyhedron(faces)`** — многогранник из граней (`NonEmptyList<List<Vec3>>`); каждая грань — список
  точек по порядку (движку нужно ≥3), общие точки (по `equals`) схлопываются в один индекс автоматически.
- **`Part(name, solid, color?)`** — запись списка `export` модели. Если `color` не задан, он
  выводится детерминированно из имени по палитре elementary OS (базовые «500»), так что предпросмотр
  (PNG/STEP) всегда разноцветный; явный `color` побеждает.
- **`Vg3.export(parts, format)`** — lowering в арену, сериализация, запуск ядра.
- **`Vg3.model(parts)`** / **`Vg3.json(parts)`** — тот же lowering и сериализация без запуска ядра:
  каноническая IR-модель / её JSON (для инструментов вроде генератора документации).
- **`Format.Stl(output, tolerance?)`** / **`Format.Png(output, tolerance?, size?, azimuth?, elevation?, compression?)`** /
  **`Format.Step(filename)`** (STEP всегда один файл, без `output`) /
  **`Format.Json(filename)`** (отчёт-метаданные о телах: имя, цвет, bbox, объём/площадь, число
  solid/face/edge; всегда один файл).
- Уровень сжатия PNG — параметр `compression` у `Format.Png` (`0..=9`, по умолчанию `6`; `0` — без
  сжатия).
- **`Output.Single(filename)`** / **`Output.Multi(path)`** — раскладка вывода для STL/PNG.

## Как это устроено

Всё, что видит пользователь, — `Solid`/`Region` и фабрики; индексы и `ir.Body`/`ir.Sketch` скрыты внутри.

```
DSL:  box(...) :: Solid              Фабрики (генерируются из ir.Body)
      rect(...) :: Region            Sugar над Region (ir.Sketch)
        │
        ▼
      Solid / Region                 Доменные графы: операнды — Solid / Region
        │  lower(operand, sketch) / lower(operand)
        ▼
      ir.Body / ir.Sketch            Плоские узлы с операндами-индексами
        │
        ▼
      Model{sketches, bodies, export} Сериализация (@Serializable)
        │
        ▼
      vg3 (ядро)
```

- **Кодогенерация.** `ir` (структура IR) генерируется из JSON Schema, а `Solid`/`Region` + мапперы +
  фабрики — KSP из `ir.Body`/`ir.Sketch`. Обе таски выведены из одного источника (Rust-типы
  `vg3-model`), поэтому не расходятся.
- **Arena.** `Vg3` складывает `Solid` и `Region` в две арены с дедупликацией по `equals`: один и тот же
  узел занимает одну позицию (переиспользование → один узел, несколько ссылок). Lowering идёт bottom-up,
  так что операнды всегда получают индекс раньше родителя (`index < current`); тело ссылается на эскиз,
  эскиз на тело — никогда.

## Разработка библиотеки

```sh
cd kt
./gradlew :ktcad:build          # генерация + компиляция + тесты
./gradlew :ktcad:test
```

Артефакты генерации: `ktcad/build/generated/vg3/kotlin` (ir) и `ktcad/build/generated/ksp` (Solid);
в git не хранятся.
