# vg3 — Kotlin-фронтенд

Типизированный Kotlin-DSL для построения моделей `vg3`: он собирает IR (плоскую арену узлов),
сериализует его и запускает ядро `vg3` для экспорта в STL/STEP/PNG.

Про ядро (Rust-движок + CLI) — в [корневом README](../README.md).

## Модули

```
kt/
  ktcad/        runtime-библиотека: сгенерированный ir, доменный Solid, Vg3, Format
  gen-schema/   кодогенератор: JSON Schema -> Kotlin ir-классы (универсальный)
  gen-solid/    кодогенератор: KSP-процессор, ir.Body -> Solid + маппер + фабрики
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
  Возвращают `Solid`.
- **`Solid`** — immutable доменный узел; операнды — другие `Solid` (не индексы).
- **Булевы:** `+` (fuse), `-` (cut), `*` (common), а также `fuse(parts)`, `cut(base, tools)`,
  `common(parts)` — sugar над единым узлом `bool`.
- **`offset(distance)`** — утолщение (положительное) / утоньшение (отрицательное) тела.
- **Трансформации** (возвращают новый `Solid`): `translate`, `up`/`down`, `left`/`right`, `forward`/`back`,
  `scale`/`scaleX/Y/Z`, `rotate(axis, angle, center?)`/`rotateX/Y/Z`, `mirrorXY/XZ/YZ`/`mirror(normal, center?)`.
  Оси: `up=+Z, right=+X, forward=+Y` (и минусы). Углы — радианы (`Math.toRadians(deg)`).
- **Контуры** — `Profile(start, segment…)` / `Path(start, segment…)`; сегменты: `lineTo`/`lineRel`,
  `arcTo`/`arcRel`, `splineTo` (абсолютные `To`, относительные `Rel` — фабрика сама ведёт текущую
  точку). Плюс `circle(radius)` / `circle(center, radius)`, `polygon(...)` (2D и 3D), `polyline(...)`
  (3D), `Path.close()`. Контур всегда имеет ≥1 ребро, поэтому пустой контур невыразим.
- **Построение тел из контуров:** `Profile.extrude(height)`, `Profile.revolve(angle)`,
  `Profile.sweep(path, mode?)`, `List<Path>.loft(ruled?)`.
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

Всё, что видит пользователь, — `Solid` и фабрики; индексы и `ir.Body` скрыты внутри.

```
DSL:  box(...) :: Solid              Фабрики (генерируются из ir.Body)
        │
        ▼
      Solid                           Доменный граф: операнды — Solid
        │  lower(operand: (Solid) -> BodyIndex)
        ▼
      ir.Body                         Плоский узел с операндами-индексами
        │
        ▼
      Model{parts, export}            Сериализация (@Serializable)
        │
        ▼
      vg3 (ядро)
```

- **Кодогенерация.** `ir` (структура IR) генерируется из JSON Schema, а `Solid`/маппер/фабрики — KSP из
  `ir.Body`. Обе таски выведены из одного источника (Rust-типы `vg3-model`), поэтому не расходятся.
- **Arena.** `Vg3` складывает `Solid` в арену с дедупликацией по `equals`: один и тот же `Solid`
  занимает одну позицию (переиспользование → один узел, несколько ссылок). Lowering идёт bottom-up,
  так что операнды всегда получают индекс раньше родителя (`index < current`).

## Разработка библиотеки

```sh
cd kt
./gradlew :ktcad:build          # генерация + компиляция + тесты
./gradlew :ktcad:test
```

Артефакты генерации: `ktcad/build/generated/vg3/kotlin` (ir) и `ktcad/build/generated/ksp` (Solid);
в git не хранятся.
