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
  Возвращают `Solid`; `extrude`/`revolve`/`sweep` принимают `Region` (эскиз).
- **`Solid`** — immutable доменный узел; операнды — другие `Solid` (не индексы).
- **Булевы:** `+` (fuse), `-` (cut), `*` (common), а также `fuse(parts)`, `cut(base, tools)`,
  `common(parts)` — sugar над единым узлом `bool`.
- **`offset(distance)`** — утолщение (положительное) / утоньшение (отрицательное) тела.
- **Трансформации** (возвращают новый `Solid`): `translate`, `up`/`down`, `left`/`right`, `forward`/`back`,
  `scale`/`scaleX/Y/Z`, `rotate(axis, angle, center?)`/`rotateX/Y/Z`, `mirrorXY/XZ/YZ`/`mirror(normal, center?)`.
  Оси: `up=+Z, right=+X, forward=+Y` (и минусы). Углы — радианы (`Math.toRadians(deg)`).
- **Контуры** — 2D живёт в домене `Region`, 3D — в `Path(start, segment…)`; сегменты: `lineTo`/`lineRel`,
  `arcTo`/`arcRel`, `splineTo` (абсолютные `To`, относительные `Rel` — фабрика сама ведёт текущую
  точку). 2D-контур: `contour(start, segment…)`; готовые: `rect(width, height)`, `circle(radius)`,
  `polygon(first, second, vararg)`; 3D: `polyline(...)`, `polygon(...)`, `Path.close()`. Контур всегда
  имеет ≥1 ребро, поэтому пустой контур невыразим.
- **`Region`** — immutable доменный 2D-узел (эскиз): `rect`/`circle`/`polygon`/`contour` + булевы
  `union`/`cut`/`intersect` (`+`/`-`/`*`) и трансформации `translate(dx, dy)`, `rotate(angle, center?)`,
  `mirror(normal, center?)`, `scale(x, y)`. Эскизы **не экспортируются** — только служат профилем тел.
- **Построение тел из эскизов:** `Region.extrude(height)`, `Region.revolve(angle)`,
  `Region.sweep(path, mode?)`, `List<Path>.loft(ruled?)`.
- **`Solid.fillet(radius, kind = FILLET)`** / **`Solid.fillet(expression, kind = FILLET)`** /
  **`Solid.fillet(expression, radius, kind = FILLET)`** — скругление (или `kind = CHAMFER`): постоянным
  радиусом всем рёбрам, Rhai-выражением на ребро (число), либо булевым предикатом отбора рёбер
  с постоянным радиусом.
- **`polyhedron(faces)`** — многогранник из граней; каждая грань — список точек по порядку, общие точки
  (по `equals`) схлопываются в один индекс автоматически.
- **`Part(name, solid, color?)`** — запись списка `export` модели.
- **`Vg3.export(parts, format)`** — lowering в арену, сериализация, запуск ядра.
- **`Format.Stl(output, tolerance?)`** / **`Format.Png(output, tolerance?, size?, azimuth?, elevation?)`** /
  **`Format.Step(filename)`** (STEP всегда один файл, без `output`).
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
