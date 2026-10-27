# newm в Void Linux

[English](../../en/install/void.md) · **Русский**

В Void нет systemd, поэтому кое-что, что другие системы делают сами, здесь настраивается
вручную: место (seat), `XDG_RUNTIME_DIR` и сеансовая шина D-Bus. Это минут пять.

**glibc или musl?** Берите Void с **glibc**. Собственные окна newm (панель, лаунчер, экран
блокировки) рисует Skiko, а он собран только под glibc; к тому же Java 27 под musl в Void
нет. На musl newm не проверялся; что о нём известно — на странице [Alpine](alpine.md).

## 1. Что нужно newm

```sh
sudo xbps-install -S git curl \
    mesa-dri libglvnd libgbm libdrm libinput eudev-libudev libseat \
    libxkbcommon xkeyboard-config xorg-server-xwayland libxcb libX11 \
    pam-libs fontconfig freetype dejavu-fonts-ttf \
    dbus elogind
```

- `mesa-dri`, `libgbm`, `libglvnd`: драйвер видеокарты для AMD, Intel и других.
- `libseat`: экран и ввод без root. В Void она собрана для работы с elogind или seatd.
- `xorg-server-xwayland` и `libxcb` — для программ X11.
- `pam-libs`: пароль на экране блокировки (служба PAM `login`).
- `dbus`, `elogind`: см. следующий шаг.

## 2. Место (seat): elogind или seatd

newm работает с любым из них. **С elogind проще**: он ещё и задаёт `XDG_RUNTIME_DIR` (без
него newm не запустится) и даёт newm заблокировать экран перед сном компьютера.

**С elogind** (рекомендуется):

```sh
sudo ln -s /etc/sv/dbus /var/service/
sudo ln -s /etc/sv/elogind /var/service/
```

Выйдите и войдите снова. `echo $XDG_RUNTIME_DIR` должен показать `/run/user/<ваш id>`.

**С seatd** (без elogind):

```sh
sudo xbps-install -S seatd
sudo ln -s /etc/sv/dbus /var/service/
sudo ln -s /etc/sv/seatd /var/service/
sudo usermod -aG _seatd "$USER"
```

Тогда `XDG_RUNTIME_DIR` должен задавать кто-то ещё: поставьте `turnstile` и включите его
службу (`/etc/sv/turnstiled`) или создайте папку сами (как — показано в справочнике Void,
«Session and Seat Management»). Без elogind newm не узнаёт, что компьютер вот-вот уснёт, и
не может заранее заблокировать экран.

## 3. Java 27

В Void нет пакета Java 26 или 27. Возьмите Temurin, сборку Java 27 от Eclipse, в
`/usr/lib/jvm` (там ищет команда `newm`):

```sh
sudo mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | sudo tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # для сборки ниже
```

На ARM напишите `aarch64` вместо `x64`.

## 4. Сборка и установка newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (спросит пароль)
```

`./install.sh --user` ставит в `~/.local`. Скрипт собирает newm, кладёт команду `newm` в
PATH и добавляет newm в список сеансов экрана входа
(`/usr/share/wayland-sessions/newm.desktop`). Подробнее — в [Установке](../install.md#сборка-и-установка).

Обновить потом: `git pull && ./install.sh`.

## 5. Что стоит добавить

Терминал и помощники панели (всё необязательно):

```sh
sudo xbps-install -S foot pipewire wireplumber pulseaudio-utils brightnessctl playerctl \
    NetworkManager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

- Службы — это ссылки runit: `sudo ln -s /etc/sv/NetworkManager /var/service/`, так же для
  `bluetoothd` и `power-profiles-daemon`. (Отключите службы `dhcpcd` или `wpa_supplicant`,
  которые будут мешать NetworkManager.)
- **Звук**: PipeWire работает от вашего пользователя, а не как служба. Справочник Void
  («PipeWire») показывает, как сделать, чтобы он запускал WirePlumber и свою часть для
  PulseAudio. Затем пусть newm запускает его вместе с сеансом, в `~/.config/newm/config.kts`:

  ```kotlin
  autostart {
      app("pipewire")
  }
  ```

- `pulseaudio-utils`: только ради `pactl`, для окна громкости. Работает и с PipeWire.

Для демонстрации экрана скажите порталу использовать wlr, в
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Запуск

Без systemd сеансовую шину D-Bus за вас никто не запускает. Панели newm (трей, уведомления,
порталы) она нужна, поэтому запускайте newm внутри `dbus-run-session`. Из текстовой консоли
(Ctrl+Alt+F3, войдите):

```sh
dbus-run-session newm
```

**С экраном входа** сделайте то же в файле сеанса:

```sh
sudo sed -i 's|^Exec=|Exec=dbus-run-session |' /usr/share/wayland-sessions/newm.desktop
```

(`install.sh` каждый раз пишет этот файл заново: после обновления выполните строку снова.)

**greetd** с tuigreet:

```sh
sudo xbps-install -S greetd tuigreet
```

`/etc/greetd/config.toml` (строки `vt` и `user`, что там есть, оставьте; greetd в Void
запускает приветствие от пользователя `_greeter`):

```toml
[default_session]
command = "tuigreet --time --remember --cmd 'dbus-run-session newm'"
user = "_greeter"
```

Отключите службу `agetty` текстовой консоли на `vt` greetd (для `vt = 7` ничего делать не
нужно; для `vt = 1`: `sudo rm /var/service/agetty-tty1`), затем
`sudo ln -s /etc/sv/greetd /var/service/`.

SDDM подключается так же (`/etc/sv/sddm`).

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: `mesa-dri`, из пакетов выше.
- **NVIDIA**: проприетарный драйвер 545 или новее из репозитория `void-repo-nonfree`
  (`nvidia`), с kernel modesetting: добавьте `nvidia-drm.modeset=1` в командную строку ядра
  (`GRUB_CMDLINE_LINUX_DEFAULT` в `/etc/default/grub`, затем `sudo update-grub`) и
  перезагрузитесь. `cat /sys/module/nvidia_drm/parameters/modeset` должен показать `Y`.
  Драйверы 560 и новее включают его сами; тогда параметр ядра не нужен.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `XDG_RUNTIME_DIR is not set`: elogind (или turnstile) не запущен; см.
  [шаг 2](#2-место-seat-elogind-или-seatd).
- `Could not open the seat`: не отвечает ни elogind, ни seatd. Проверьте
  `sudo sv status elogind` (или `seatd`) и, если используете seatd, что вы в группе `_seatd`.
- Нет трея и уведомлений: newm запущен без `dbus-run-session`.

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
