# Разработка newm

[English](../en/development.md) · **Русский**

newm написан на Kotlin, на основе библиотеки для композиторов
[Caulis](https://github.com/AristarkhKenebas/caulis).

```
src/main/kotlin/newm/   сам newm: раскладка, жесты, оверлеи, панель, экран блокировки, лаунчер
templates/              что newm кладёт в ~/.config/newm (config.kts, examples, bars)
nix/                    пакет (package.nix, deps.json), модули NixOS и home-manager
docs/                   эта документация (en, ru)
dev/newm-dev            newm из исходников, для работы над newm внутри newm
legacy/                 оригинальный newm на Python, для справки
```

## Сборка и тесты

```sh
./gradlew installDist     # build/install/newm/bin/newm
./gradlew test
```

Если исходники Caulis лежат рядом с этой папкой (`../caulis`), добавьте `-Pcaulis.local=true`,
чтобы собрать newm с ними. `./install.sh` и `dev/newm-dev` делают это сами.

Чтобы попробовать newm, не покидая свой рабочий стол, запустите его в терминале на отдельной
сессии D-Bus (тогда он не трогает уведомления, трей и медиаклавиши вашего стола); newm
откроется как обычное окно:

```sh
dbus-run-session -- build/install/newm/bin/newm
```

Без окна вообще (headless: для скриптов и замеров) — тоже со своим домом и шиной:

```sh
env -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS CAULIS_MODE=headless HOME=$(mktemp -d) \
    dbus-run-session -- build/install/newm/bin/newm
```

(Вложенный или headless newm никогда не закрывает ваш демон уведомлений, даже на вашей шине,
но имена на ней он всё равно занял бы.) `./gradlew test` сам держит тесты вдали от шин стола.

Пакет Nix собирается без сети из зависимостей, перечисленных в `nix/deps.json`. После
изменения зависимостей (новая версия Caulis в `gradle/libs.versions.toml`) создайте его
заново: `nix run .#update-deps` (он берёт закреплённый Gradle flake, вход `nixpkgs-gradle`,
и записывает его версию в `nix/deps-gradle-version`).

## Работа над newm внутри newm

`dev/newm-dev` запускает newm из исходников как ваш сеанс, поэтому менять newm можно прямо
во время работы в нём:

```sh
dev/newm-dev install-session     # один раз: «newm (dev)» на экране входа
```

Войдите в «newm (dev)» или запустите `dev/newm-dev` из текстовой консоли. Затем, уже внутри:

- **`dev/newm-dev rebuild`** собирает newm и, если сборка прошла успешно, перезапускает newm
  с новой сборкой. Удобная клавиша для этого:

  ```kotlin
  keys { "L-S-r" { launch("foot --hold ~/projects/newm/dev/newm-dev rebuild") } }
  ```

- **Сборка с ошибками ничего не меняет.** Работающий newm остаётся, а ошибки видны
  в терминале.
- **Новая сборка, которая падает при запуске, откатывается.** Снова запускается предыдущая
  сборка, и карточка-уведомление сообщает вам об этом. Журнал упавшей сборки — в
  `~/.local/state/newm/newm.log.old`.
- **Горячая замена без перезапуска.** К newm можно подключить отладчик по адресу `localhost:5005`
  (IntelliJ: *Run → Attach to Process*). Затем *Reload Changed Classes* подставляет изменённые
  тела методов в работающий newm, а ваши окна остаются на своих местах. Изменения в структуре
  классов (новые поля или методы) требуют пересборки.
- **Конфигурация перечитывается при сохранении**, как всегда.
- Изменения Caulis в `../caulis` собираются вместе с newm.

Сборки хранятся в `~/.cache/newm-dev`: `good` — та, что работает сейчас, `good.prev` — предыдущая.
Собственный журнал скрипта — `~/.cache/newm-dev/newm-dev.log`.

Перезапуск закрывает открытые программы, как и выход из сеанса; `newm cmd restart keep`
потом открывает их снова, каждую на своём месте. Горячая замена их сохраняет.

Чтобы увидеть изменение newm в работе, `newm cmd debug` выводит раскладку окон, а
`newm cmd log <область> 3` делает одну область журнала подробной прямо во время работы (см.
[Тестирование newm](testing.md#подробнее-в-журнале)).
