# newm в Fedora

[English](../../en/install/fedora.md) · **Русский**

Эта страница для Fedora Workstation, спинов Fedora (KDE, Sway, ...) и Nobara. Команды
проверены по Fedora 43 и 44.

## 1. Что нужно newm

```sh
sudo dnf install git curl java-latest-openjdk-devel \
    mesa-dri-drivers mesa-libEGL mesa-libgbm libglvnd-egl libglvnd-gles libglvnd-glx \
    libdrm libinput systemd-libs libseat libxkbcommon xkeyboard-config \
    xorg-x11-server-Xwayland libxcb libX11 pam fontconfig freetype dejavu-sans-fonts
```

- `java-latest-openjdk-devel` — это Java 27 в Fedora (самая новая Java, обновляется с
  выходом новых).
- `mesa-*` и `libglvnd-*`: драйвер видеокарты для AMD, Intel и других.
- `libseat`: экран и ввод без root. Работает через systemd-logind: запускать службу не нужно.
- `xorg-x11-server-Xwayland` и `libxcb` — для программ X11.
- `pam`: пароль на экране блокировки.

На рабочем столе большая часть этого уже установлена.

## 2. Java

`java-latest-openjdk` ставится рядом с Java Fedora по умолчанию, в `/usr/lib/jvm`; команда
`newm` сама находит её там. Менять Java по умолчанию через `alternatives` не нужно.

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
sudo dnf install foot wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- `pulseaudio-utils`: только ради `pactl`, для окна громкости. Работает и с PipeWire, звуком
  Fedora.
- **Режимы питания**: в Fedora Workstation работает `tuned-ppd`, который отвечает newm так же,
  как power-profiles-daemon. Оставьте его. (В спине без него: `sudo dnf install
  power-profiles-daemon`; вместе их поставить нельзя.)

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

Или выйдите из сеанса и выберите «newm» на экране входа (GDM: кнопка с шестерёнкой; SDDM:
список сеансов).

Если экрана входа нет, небольшой вариант — greetd с tuigreet:

```sh
sudo dnf install greetd tuigreet
```

В `/etc/greetd/config.toml` оставьте строку `user`, которая там есть, и задайте команду:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

```sh
sudo systemctl disable gdm       # или sddm: ваш нынешний экран входа
sudo systemctl enable greetd
```

Или сам newm как экран входа, со своим экраном блокировки: [newm как экран входа](../greeter.md).

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: Mesa, из пакетов выше.
- **NVIDIA**: проприетарный драйвер 545 или новее из RPM Fusion (`akmod-nvidia`; в Nobara он
  уже есть). Нужен kernel modesetting:
  `sudo grubby --update-kernel=ALL --args="nvidia-drm.modeset=1"`, затем перезагрузка.
  `cat /sys/module/nvidia_drm/parameters/modeset` должен показать `Y`. Драйверы 560 и новее
  включают его сами; тогда параметр ядра не нужен.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `newm needs Java 26 or newer`: `sudo dnf install java-latest-openjdk-devel`.
- `Could not open the seat`: запускайте из текстовой консоли, где вы вошли, а не через
  `su` или `sudo`.

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
