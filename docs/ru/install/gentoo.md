# newm в Gentoo

[English](../../en/install/gentoo.md) · **Русский**

Эта страница для Gentoo на amd64, с OpenRC или systemd. newm нужна glibc (его панель,
лаунчер и экран блокировки рисует Skiko, собранный только под glibc), поэтому используйте
профиль с glibc.

## 1. USE-флаги

Значения по умолчанию в основном подходят. Важны эти:

```sh
# /etc/portage/package.use/newm
media-libs/libglvnd X          # libGL.so.1: нужна Skiko
sys-auth/seatd     systemd     # в профиле с systemd
# sys-auth/seatd   elogind     # в OpenRC с elogind (там рекомендуется)
# sys-auth/seatd   server      # в OpenRC без elogind: демон seatd
```

`sys-auth/seatd` принимает не больше одного из `systemd` и `elogind`. Также задайте
`VIDEO_CARDS` в `/etc/portage/make.conf` под свою видеокарту (например
`VIDEO_CARDS="amdgpu radeonsi"` или `VIDEO_CARDS="intel"`), как для любого рабочего стола.

## 2. Что нужно newm

```sh
sudo emerge --ask --noreplace dev-vcs/git net-misc/curl \
    media-libs/mesa media-libs/libglvnd x11-libs/libdrm dev-libs/libinput virtual/libudev \
    sys-auth/seatd x11-libs/libxkbcommon x11-misc/xkeyboard-config \
    x11-base/xwayland x11-libs/libxcb x11-libs/libX11 \
    sys-libs/pam media-libs/fontconfig media-libs/freetype media-fonts/dejavu
```

- `sys-auth/seatd` приносит libseat: экран и ввод без root.
- `x11-base/xwayland` и `x11-libs/libxcb` — для программ X11.
- `sys-libs/pam`: пароль на экране блокировки (служба PAM `login`).

## 3. Java 27

Java 27 пока в тестовой ветке (`~amd64`). Разрешите её только для этого пакета:

```sh
echo "dev-java/openjdk-bin:27 ~amd64" | sudo tee /etc/portage/package.accept_keywords/newm-java
sudo emerge --ask dev-java/openjdk-bin:27
```

(`dev-java/openjdk:27` собирает её из исходников.) Она появляется как
`/usr/lib/jvm/openjdk-bin-27`, где команда `newm` находит её сама. Менять Java системы через
`eselect java-vm` не нужно. Для сборки:

```sh
export JAVA_HOME=/usr/lib/jvm/openjdk-bin-27
```

## 4. Место (seat)

- **systemd**: ничего делать не нужно; libseat работает через systemd-logind.
- **OpenRC с elogind** (для OpenRC рекомендуется): elogind ещё и задаёт `XDG_RUNTIME_DIR`,
  без которого newm не запустится, и сообщает newm, что компьютер засыпает, чтобы тот успел
  заблокировать экран.

  ```sh
  sudo emerge --ask --noreplace sys-auth/elogind sys-apps/dbus
  sudo rc-update add dbus default
  sudo rc-update add elogind boot
  ```

- **OpenRC с демоном seatd** (`USE=server`):

  ```sh
  sudo rc-update add seatd default && sudo rc-service seatd start
  sudo usermod -aG seat "$USER"
  ```

  Тогда `XDG_RUNTIME_DIR` должен задавать кто-то ещё (например `sys-auth/pam_xdg`).
  Без elogind newm не может заблокировать экран перед сном.

После этого выйдите и войдите снова.

## 5. Сборка и установка newm

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (спросит пароль)
```

`./install.sh --user` ставит в `~/.local`. Скрипт собирает newm, кладёт команду `newm` в
PATH и добавляет newm в список сеансов экрана входа
(`/usr/share/wayland-sessions/newm.desktop`). Подробнее — в [Установке](../install.md#сборка-и-установка).

Обновить потом: `git pull && ./install.sh`.

## 6. Что стоит добавить

Терминал и помощники панели (всё необязательно):

```sh
sudo emerge --ask --noreplace gui-apps/foot media-video/wireplumber media-libs/libpulse \
    media-sound/playerctl net-misc/networkmanager net-wireless/bluez \
    sys-power/power-profiles-daemon \
    sys-apps/xdg-desktop-portal sys-apps/xdg-desktop-portal-gtk gui-libs/xdg-desktop-portal-wlr
```

- `media-libs/libpulse` приносит `pactl`, для окна громкости.
- `brightnessctl` нет в основном дереве Gentoo (он есть в оверлее GURU). Без него ползунок и
  клавиши яркости ничего не делают.
- `gui-apps/foot` на момент написания в тестовой ветке (`~amd64`); подойдёт любой терминал.
- В OpenRC PipeWire работает от вашего пользователя: запускайте его вместе с сеансом,
  например в `~/.config/newm/config.kts`: `autostart { app("gentoo-pipewire-launcher") }`
  (подробности — в вики Gentoo, «PipeWire»).

Для демонстрации экрана скажите порталу использовать wlr, в
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Запуск

Из текстовой консоли (Ctrl+Alt+F3, войдите):

```sh
newm                    # systemd
dbus-run-session newm   # OpenRC: панели newm нужна сеансовая шина D-Bus, а больше её никто не запустит
```

**С экраном входа** в OpenRC добавьте `dbus-run-session` и в файл сеанса:

```sh
sudo sed -i 's|^Exec=|Exec=dbus-run-session |' /usr/share/wayland-sessions/newm.desktop
```

(`install.sh` каждый раз пишет этот файл заново: после обновления выполните строку снова.)

**greetd** с tuigreet (`gui-libs/greetd`, `gui-apps/tuigreet`). В `/etc/greetd/config.toml`
оставьте строку `user`, которая там есть, и задайте команду:

```toml
[default_session]
command = "tuigreet --time --remember --cmd newm"
```

(В OpenRC: `--cmd 'dbus-run-session newm'`.) В systemd: `sudo systemctl enable greetd`.
У пакета greetd в Gentoo нет своей службы OpenRC; вики Gentoo («greetd») показывает, как
запускать его через `display-manager-init`.

Первый запуск запишет `~/.config/newm/config.kts`. **Logo+/** покажет все клавиши.
Дальше: [Каждый день](../using.md).

## Видеокарта

- **AMD, Intel**: Mesa, с `VIDEO_CARDS`, заданным как выше.
- **NVIDIA**: `x11-drivers/nvidia-drivers` 545 или новее, с kernel modesetting. Драйверы 560 и
  новее включают его сами; `cat /sys/module/nvidia_drm/parameters/modeset` должен показать
  `Y`. Если там `N`, добавьте `nvidia-drm.modeset=1` в командную строку ядра.
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением;
  `VIDEO_CARDS="virgl"`).

## Если что-то пошло не так

- Запустите newm из текстовой консоли: он скажет, чего не хватает, прежде чем сдаться.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- `Missing system libraries: libGLESv2.so.2` и подобное: проверьте `media-libs/libglvnd` и
  `media-libs/mesa`. Skiko не может загрузить `libGL.so.1`: `media-libs/libglvnd[X]`.
- `XDG_RUNTIME_DIR is not set`: не запущен elogind (OpenRC); см. [шаг 4](#4-место-seat).
- `Could not open the seat`: не отвечает ни logind, ни elogind, ни seatd; см. [шаг 4](#4-место-seat).

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
