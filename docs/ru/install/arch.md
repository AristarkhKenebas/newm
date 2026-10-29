# newm в Arch Linux

[English](../../en/install/arch.md) · **Русский**

Эта страница для Arch Linux и систем на его пакетах: EndeavourOS, Manjaro и CachyOS.
Команды везде одни и те же.

## 1. Что нужно newm

```sh
sudo pacman -S --needed git jdk-openjdk \
    mesa libglvnd libdrm libinput systemd-libs seatd libxkbcommon xkeyboard-config \
    xorg-xwayland libxcb libx11 pam fontconfig freetype2 ttf-dejavu
```

- `jdk-openjdk` — это Java 27 в Arch.
- `seatd` приносит libseat. В Arch она работает через systemd-logind, поэтому запускать
  службу seatd **не нужно**.
- `xorg-xwayland` и `libxcb` — для программ X11.
- `pam` — для пароля на экране блокировки.
- `ttf-dejavu` — просто шрифт; подойдут любые.

**Manjaro**: стабильная ветка получает пакеты на несколько недель позже Arch. Если
`pacman -Si jdk-openjdk` показывает версию старше 26, возьмите Java по
[общей странице](../install.md#java-27).

## 2. Java

Основной Java можно оставить другую (`archlinux-java status`). Команда `newm` сама найдёт
Java 27 в `/usr/lib/jvm`.

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
sudo pacman -S --needed foot wireplumber libpulse brightnessctl playerctl \
    networkmanager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
sudo systemctl enable --now NetworkManager bluetooth power-profiles-daemon
```

- `wireplumber`: клавиши громкости (`wpctl`). Нужен запущенный PipeWire, как на большинстве
  рабочих столов.
- `libpulse`: `pactl`, для окна громкости.
- Включайте NetworkManager, только если сетью не управляет что-то другое (один iwd,
  systemd-networkd).

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

Или выберите «newm» на экране входа (SDDM, GDM, ly, greetd). Если экрана входа нет, небольшой
вариант — greetd с tuigreet:

```sh
sudo pacman -S greetd greetd-tuigreet
```

В `/etc/greetd/config.toml` оставьте строку `user`, которая там есть, и задайте команду:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
user = "greeter"
```

```sh
sudo systemctl enable greetd     # сначала отключите прежний экран входа, если он есть
```

Или сам newm как экран входа, со своим экраном блокировки: [newm как экран входа](../greeter.md).

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: достаточно `mesa`. (Драйверы Vulkan newm не нужны.)
- **NVIDIA**: драйвер 545 или новее: `nvidia-open` (или `nvidia-open-dkms`) и `nvidia-utils`,
  в котором есть часть GBM, через которую рисует newm. Нужен kernel modesetting: добавьте
  `nvidia-drm.modeset=1` в командную строку ядра (в записи загрузчика) и перезагрузитесь.
  `cat /sys/module/nvidia_drm/parameters/modeset` должен показать `Y`. Драйверы 560 и новее
  включают его сами; тогда параметр ядра не нужен.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo pacman -S jdk-openjdk`.
- `Could not open the seat`: запускайте из текстовой консоли, где вы вошли, а не через
  `su` или `sudo`.

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
