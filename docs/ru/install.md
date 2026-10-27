# Установка

[English](../en/install.md) · **Русский**

newm работает в любом Linux с драйвером видеокарты, glibc и Java 27. Выберите свою систему —
там точные команды; на этой странице то, что везде одинаково.

| Ваша система | Страница |
|---|---|
| NixOS (flake, home-manager, без flakes) | [NixOS](install/nixos.md) |
| Arch, EndeavourOS, Manjaro, CachyOS | [Arch](install/arch.md) |
| Debian 13+, Ubuntu 24.04+, Linux Mint, Pop!_OS | [Debian и Ubuntu](install/debian-ubuntu.md) |
| Fedora, Nobara | [Fedora](install/fedora.md) |
| openSUSE Tumbleweed | [openSUSE](install/opensuse.md) |
| Void Linux | [Void](install/void.md) |
| Gentoo (OpenRC или systemd) | [Gentoo](install/gentoo.md) |
| Alpine Linux (musl: не проверено) | [Alpine](install/alpine.md) |
| Что-то другое | [Другие системы](install/other.md) |

На этой странице:

- [Что нужно](#что-нужно)
- [Java 27](#java-27)
- [Сборка и установка](#сборка-и-установка)
- [Из исходников, вручную](#из-исходников-вручную)
- [Запуск](#запуск) и [первый раз](#первый-раз)

## Что нужно

- **Java 27.** Для сборки нужен JDK 27; для запуска — Java 26 или новее. См. [ниже](#java-27).
- **Драйвер видеокарты** с OpenGL ES 3 и GBM: Mesa (AMD, Intel и другие) или NVIDIA 545
  и новее.
- **Место (seat).** newm открывает экран и устройства ввода через libseat, без root. libseat
  обращается к systemd-logind, elogind или seatd. В дистрибутиве с systemd настраивать
  ничего не нужно.
- **Библиотеки, которые newm открывает во время работы.** При сборке newm их не связывает;
  он ищет их по имени при запуске. Точные имена пакетов — на странице каждого
  дистрибутива; полный список файлов — в [Других системах](install/other.md#библиотеки).
- **glibc.** Собственные окна newm (панель, лаунчер, экран блокировки) рисует Skiko, а он
  собран только под glibc. На musl (Alpine, Void musl) newm не проверялся.
- **Шрифты.** Любые, что есть в системе; newm берёт их через fontconfig.
- **Терминал.** newm открывает `$TERMINAL`, а если его нет — первый найденный из alacritty,
  foot, kitty, wezterm, ghostty, gnome-terminal, konsole, xfce4-terminal и xterm.
- **Для панели и клавиш ноутбука** (всё необязательно; без программы эта часть ничего не
  делает):

  | Что | Программа |
  |---|---|
  | клавиши громкости | `wpctl` (WirePlumber) |
  | окно громкости, список динамиков | `pactl` (утилиты PulseAudio; работают и с PipeWire) |
  | ползунок и клавиши яркости | `brightnessctl` |
  | клавиши «играть / дальше / назад» | `playerctl` |
  | окно Wi-Fi | NetworkManager, запущенный |
  | окно Bluetooth | BlueZ, запущенный |
  | режимы питания | power-profiles-daemon (или то, что занимает его имя в D-Bus, например tuned-ppd) |
  | выбор файлов, демонстрация экрана | `xdg-desktop-portal` с `xdg-desktop-portal-gtk` и `xdg-desktop-portal-wlr` |

Снимки экрана и история буфера обмена у newm свои: grim и менеджер буфера не нужны.

## Java 27

Сначала загляните на страницу своего дистрибутива: во многих JDK 27 (или 26) есть пакетом.

Если в вашем нет, возьмите сборку Eclipse Temurin. Положите её в `/usr/lib/jvm`: команда
`newm` ищет там сама, в том числе когда её запускает экран входа.

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
/usr/lib/jvm/temurin-27/bin/java -version
```

На ARM (aarch64) напишите `aarch64` вместо `x64`. Чтобы потом обновить, удалите папку и
выполните те же команды снова.

[SDKMAN](https://sdkman.io) тоже подойдёт (`sdk list java`, затем `sdk install java` с
версией `27...-tem`). Он кладёт Java в домашнюю папку, и экран входа может её не найти.
Тогда задайте `JAVA_HOME` там, где его прочитает ваш сеанс, или используйте архив выше.

Как команда `newm` выбирает Java: `JAVA_HOME`, если там 26 или новее; иначе `java` из PATH,
если она достаточно новая; иначе самую новую в `/usr/lib/jvm`. Если нет ни одной, она
пишет «newm needs Java 26 or newer».

Для сборки Gradle работает на любом JDK от 17 до 27, а компилирует JDK 27. Он находит его в
`JAVA_HOME` или `/usr/lib/jvm` или скачивает сам.

## Сборка и установка

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (спросит пароль)
./install.sh --user     # или только для вас: ~/.local
```

Если `java` нет в PATH, сначала скажите сборке, где Java, например
`export JAVA_HOME=/usr/lib/jvm/temurin-27`.

`install.sh`:

- собирает newm (в первый раз Gradle скачивает нужное, несколько минут);
- кладёт команду `newm` в PATH (`/usr/local/bin/newm` или `~/.local/bin/newm`);
- запускает `newm install-session`: добавляет newm в список сеансов экрана входа
  (`/usr/share/wayland-sessions/newm.desktop`) и кладёт стартовую конфигурацию в `/etc/newm`
  (файлы, которые там уже есть, остаются).

Если рядом с newm лежат исходники Caulis (`../caulis`), `install.sh` собирает с ними. Это
нужно, когда сборка говорит, что чего-то в `dev.caulis...` нет: этому newm нужна Caulis новее
выпущенной.

```sh
git clone https://github.com/AristarkhKenebas/caulis ../caulis
./install.sh
```

Обновить потом: `git pull && ./install.sh`.

**Демонстрация экрана** идёт через портал wlr. Скажите это порталу в
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

newm ставит `XDG_CURRENT_DESKTOP=newm`, поэтому портал читает именно этот файл, а своё
окружение порталам newm передаёт сам.

## Из исходников, вручную

```sh
./gradlew installDist                       # build/install/newm/bin/newm
./gradlew installDist -Pcaulis.local=true   # то же, с исходниками ../caulis
build/install/newm/bin/newm                 # запустить (см. «Запуск»)
build/install/newm/bin/newm install-session # по желанию: пункт на экране входа
```

## Запуск

- **Из текстовой консоли** (Ctrl+Alt+F3, войдите): наберите `newm`. В первый раз это лучший
  способ: если что-то не так, вы увидите, что пишет newm.
- **С экрана входа** (SDDM, GDM, ly, greetd): выберите «newm».
- **Внутри вашего рабочего стола, чтобы попробовать**: запустите `newm` в терминале. Он
  откроется окном, и в вашем рабочем столе ничего не изменится. (Клавиши вроде Logo может
  перехватывать ваш рабочий стол.)

В системе без systemd запускайте `dbus-run-session newm`, если ваш вход в систему ещё не
даёт сеансовую шину D-Bus (см. страницу [Void](install/void.md#запуск)).

Выйти: **Logo+Shift+Escape** или «Выйти» в меню питания на панели.

Другие команды:

```sh
newm --version          # какая версия newm
newm help               # что умеет команда newm
newm install-session    # добавить newm на экран входа (в NixOS не нужно)
newm cmd <команда>      # обратиться к запущенному newm (см. «Все настройки»)
```

## Первый раз

newm записывает папку вашей конфигурации, `~/.config/newm/` (из `/etc/newm`, если она есть,
иначе из своих шаблонов):

- `config.kts`: все настройки, с пояснением к каждой. Открывайте в любом редакторе, пока newm
  работает. Каждое сохранение newm сразу применяет.
- `README.md`: какой файл папки читает newm.
- `examples/`: конфигурация на Lua и в JSON.
- `bars/`: панели на выбор (см. [Панели](bars.md)).

Карточка «Welcome to newm» подскажет первые клавиши. Самые важные:

- **Logo+/**: все клавиши с описанием;
- **Logo+Return**: терминал;
- **Logo+d**: лаунчер;
- **Logo** отдельно: все окна сразу;
- **Logo+q**: закрыть окно;
- **Logo+Shift+Escape**: выйти из сеанса.

«Logo» — клавиша с логотипом Windows или Super. Дальше: [Каждый день](using.md). Пробуете
newm для кого-то? В [Тестирование newm](testing.md) есть список проверок.

Про значения по умолчанию: через 5 минут без действий экран блокируется, через 5½ экраны
гаснут, через 10 компьютер засыпает (`energy { }` в
[конфигурации](configuration.md#блокировка-и-сон-при-бездействии)).
