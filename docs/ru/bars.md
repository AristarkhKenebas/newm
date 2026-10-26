# Панели

[English](../en/bars.md) · **Русский**

У newm есть своя панель (бар), но любая другая программа-панель работает так же хорошо.
Можно и вовсе обойтись без панели. Окна всегда освобождают место под панель, какая бы она ни была.

## Собственная панель newm

Она включена с самого начала. В `config.kts`:

```kotlin
panels {
    topBar {
        enabled = true
        height = 28
        fontSize = 12
        font = "JetBrains Mono"
        background = "#ECEFF4B0"        // полупрозрачный: сквозь него видны размытые обои
        foreground = "#2E3440"
        blur = true
        texts { listOf(wifi(), cpu(), memory(), temperature(), battery(), date(), time()) }
    }
}
```

Панель показывает тексты, которые возвращает `texts`, и запрашивает их каждую секунду.
Готовые элементы:

- `wifi()`, `cpu()`, `memory()`, `temperature()`, `battery()`, `user()`;
- `date()` и `time()`, которые принимают шаблон: `time("HH:mm")`, `date("EEE d MMM")`.

Подойдёт и любой код на Kotlin, который возвращает текст.

`bottomBar { }` — то же самое, только внизу. В вашей папке файл `bars/classic.kts` — это
панель оригинального newm, с верхней и нижней панелями, готовая к вставке.

## Без панели

```kotlin
panels {
    topBar { enabled = false }
}
```

## waybar или любая другая панель

Выключите собственную панель newm и пусть newm запускает вашу:

```kotlin
panels {
    topBar { enabled = false }
    bar { cmd = "waybar" }
}
```

- newm перезапускает панель, если она упадёт.
- `visibleFullscreen = false` скрывает её, пока какое-то окно открыто во весь экран.

Панель можно запустить и в `onStartup { launch("waybar") }` или из ваших собственных скриптов.
Работает любая панель, которая использует протокол layer-shell: waybar, yambar, eww, ags, sfwbar,
ironbar и другие.

**waybar в стиле newm**: такая есть в вашей папке, в `bars/waybar/`. Скопируйте её в папку waybar:

```sh
mkdir -p ~/.config/waybar && cp ~/.config/newm/bars/waybar/* ~/.config/waybar/
```

Панели у левого или правого края тоже работают: окна освобождают место с этой стороны.

## Другие панели

Уведомления (mako, dunst, swaync), программы для обоев (swaybg, swww), доки и экранные
клавиатуры работают точно так же: запускайте их в `onStartup { }`. Если вы используете
программу для обоев, укажите `background { path = "" }`, чтобы newm не рисовал свои
обои.
