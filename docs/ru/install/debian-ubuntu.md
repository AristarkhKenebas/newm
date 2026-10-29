# newm в Debian и Ubuntu

[English](../../en/install/debian-ubuntu.md) · **Русский**

Эта страница для Debian 13 (trixie) и новее, Ubuntu 24.04 и новее и систем на их основе:
Linux Mint 22, Pop!_OS 24.04 и других. В более старых выпусках слишком старые Xwayland и
библиотеки.

## 1. Что нужно newm

В Ubuntu и родственных системах часть пакетов лежит в «universe». На рабочих столах он
включён по умолчанию; если `apt` не находит пакет, включите его:
`sudo add-apt-repository universe`.

```sh
sudo apt update
sudo apt install git curl \
    libegl1 libgles2 libgl1 libegl-mesa0 libgl1-mesa-dri libgbm1 libdrm2 \
    libinput10 libudev1 libseat1 libxkbcommon0 xkb-data \
    xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 libxcb-render0 libx11-6 \
    libpam0g libfontconfig1 libfreetype6 fonts-dejavu-core
```

- `libegl-mesa0`, `libgl1-mesa-dri`, `libgbm1`: Mesa, драйвер видеокарты для AMD, Intel и
  других.
- `libseat1`: экран и ввод без root. Работает через systemd-logind: запускать службу не нужно.
- `xwayland` и пакеты `libxcb` — для программ X11.
- `libpam0g`: пароль на экране блокировки.
- `libgl1`, `libx11-6`, `libfontconfig1`: они нужны Skiko, который рисует панель, лаунчер и
  экран блокировки newm.

На рабочем столе большая часть этого уже установлена.

## 2. Java 27

В каком выпуске какая Java:

| Выпуск | Команда |
|---|---|
| Debian 14 (forky, testing), Debian unstable | `sudo apt install openjdk-27-jdk` |
| Ubuntu 26.10 | `sudo apt install openjdk-27-jdk` |
| Ubuntu 26.04 | `sudo apt install openjdk-26-jdk` (для запуска newm достаточно; JDK 27 для сборки скачается сам) |
| Debian 13, Ubuntu 24.04, Mint 22, Pop!_OS 24.04 | пакета нет: используйте Temurin, ниже |

Temurin, сборка Java 27 от Eclipse, в `/usr/lib/jvm` (там ищет команда `newm`):

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # для сборки ниже
```

На ARM напишите `aarch64` вместо `x64`.

## 3. Сборка и установка newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (спросит пароль)
```

`./install.sh --user` ставит в `~/.local`. Скрипт собирает newm, кладёт команду `newm` в
PATH и добавляет newm в список сеансов экрана входа
(`/usr/share/wayland-sessions/newm.desktop`). Подробнее — в [Установке](../install.md#сборка-и-установка).

Обновить потом: `git pull && ./install.sh`.

## 4. Что стоит добавить

Терминал и помощники панели (всё необязательно):

```sh
sudo apt install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    network-manager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `wireplumber`: клавиши громкости (`wpctl`), вместе с PipeWire (звук по умолчанию на
  современных рабочих столах Debian и Ubuntu).
- `pulseaudio-utils`: только ради `pactl`, для окна громкости. Работает и с PipeWire.

Для демонстрации экрана скажите порталу использовать wlr, в
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## 5. Запуск

Из текстовой консоли (Ctrl+Alt+F3, войдите):

```sh
newm
```

Или выйдите из сеанса и выберите «newm» на экране входа. GDM (Ubuntu, Debian с GNOME)
прячет выбор за кнопкой с шестерёнкой; SDDM и экраны на LightDM показывают список сеансов.

Если экрана входа нет, небольшой вариант — greetd с tuigreet:

```sh
sudo apt install greetd tuigreet
```

В `/etc/greetd/config.toml` оставьте строку `user`, которая там есть, и задайте команду:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
user = "_greetd"
```

```sh
sudo systemctl enable greetd     # сначала отключите прежний экран входа, если он есть
```

Или сам newm как экран входа, со своим экраном блокировки: [newm как экран входа](../greeter.md).

(`tuigreet` есть в Debian 13 и в Ubuntu начиная с 26.04. В Ubuntu 24.04 и родственных ей
оставьте тот экран входа, что у вас есть.)

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: Mesa, из пакетов выше.
- **NVIDIA**: проприетарный драйвер 545 или новее (Ubuntu: «Дополнительные драйверы»;
  Debian: пакет `nvidia-driver` из non-free), с kernel modesetting: добавьте
  `nvidia-drm.modeset=1` в командную строку ядра (`GRUB_CMDLINE_LINUX_DEFAULT` в
  `/etc/default/grub`, затем `sudo update-grub`) и перезагрузитесь.
  `cat /sys/module/nvidia_drm/parameters/modeset` должен показать `Y`. Драйверы 560 и новее
  включают его сами; тогда параметр ядра не нужен.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `newm needs Java 26 or newer`: см. [Java 27](#2-java-27).
- `Could not open the seat`: запускайте из текстовой консоли, где вы вошли. Если всё равно
  так, запустите seatd: `sudo apt install seatd && sudo systemctl enable --now seatd`, добавьте
  себя в группу, которую использует seatd (`systemctl cat seatd` покажет её после `-g`), и
  войдите заново.

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
