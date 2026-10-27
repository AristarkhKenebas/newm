# newm в openSUSE Tumbleweed

[English](../../en/install/opensuse.md) · **Русский**

Эта страница для openSUSE Tumbleweed (и Slowroll, который получает пакеты Tumbleweed
позже). В Leap библиотеки и Java слишком старые для newm.

## 1. Что нужно newm

```sh
sudo zypper install git-core curl java-26-openjdk-devel \
    Mesa Mesa-dri Mesa-libEGL1 libgbm1 libglvnd libdrm2 libinput10 libudev1 libseat1 \
    libxkbcommon0 xkeyboard-config xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 \
    libxcb-render0 libX11-6 pam libfontconfig1 libfreetype6 dejavu-fonts
```

- `java-26-openjdk-devel`: сейчас в Tumbleweed есть Java 26, и для запуска newm её
  достаточно. Когда появится `java-27-openjdk-devel`, ставьте его.
- `Mesa*`, `libgbm1`, `libglvnd`: драйвер видеокарты для AMD, Intel и других.
- `libseat1`: экран и ввод без root. Работает через systemd-logind: запускать службу не
  нужно.
- `xwayland` и пакеты `libxcb*` — для программ X11.
- `pam`: пароль на экране блокировки.

## 2. Java

Сборка компилирует с JDK 27. Если установлена только Java 26, Gradle сам скачает JDK 27 для
сборки (в `~/.gradle/jdks`); работать newm будет на вашей Java 26. Чтобы поставить Java 27
себе, см. [общую страницу](../install.md#java-27).

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
sudo zypper install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `pulseaudio-utils`: только ради `pactl`, для окна громкости. Работает и с PipeWire.
- Окну Wi-Fi нужно, чтобы сетью управлял NetworkManager (какой из способов включён, видно в
  YaST: «Параметры сети», «Общие параметры»).

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

Или выйдите из сеанса и выберите «newm» на экране входа (SDDM, GDM).

Если экрана входа нет, небольшой вариант — greetd с tuigreet:

```sh
sudo zypper install greetd tuigreet
```

В `/etc/greetd/config.toml` оставьте строку `user`, которая там есть, и задайте команду:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

openSUSE запускает экран входа через свою службу `display-manager`, так что перейти на
greetd — это больше, чем просто включить его: следуйте документации openSUSE. Запуск newm из
текстовой консоли работает всегда.

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: Mesa, из пакетов выше.
- **NVIDIA**: проприетарный драйвер 545 или новее из репозитория NVIDIA для openSUSE (см. вики
  openSUSE, «SDB:NVIDIA drivers»), с kernel modesetting: добавьте `nvidia-drm.modeset=1` в
  командную строку ядра (YaST «Загрузчик» или `/etc/default/grub` и
  `sudo grub2-mkconfig -o /boot/grub2/grub.cfg`) и перезагрузитесь.
  `cat /sys/module/nvidia_drm/parameters/modeset` должен показать `Y`. Драйверы 560 и новее
  включают его сами; тогда параметр ядра не нужен.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo zypper install java-26-openjdk-devel`.
- `Could not open the seat`: запускайте из текстовой консоли, где вы вошли, а не через
  `su` или `sudo`.

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
