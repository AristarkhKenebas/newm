# newm в Alpine Linux

[English](../../en/install/alpine.md) · **Русский**

**Не проверено.** В Alpine вместо glibc используется musl. Java, Gradle, Caulis и
библиотеки, которые открывает newm, под musl есть, но собственные окна newm (панель,
лаунчер, экран блокировки) рисует Skiko, а его родная часть собрана только под glibc.
Пакет `gcompat` в Alpine позволяет запускать некоторые программы для glibc на musl; хватит
ли его для Skiko, пока никто не пробовал. Если попробуете, пожалуйста,
[создайте issue](https://github.com/AristarkhKenebas/newm/issues) с тем, что получилось.
Ниже — то, что, по нашим ожиданиям, понадобится. Имена пакетов сверены со списками Alpine 3.24.

## 1. Что нужно newm

`setup-desktop` в Alpine (или `setup-devd udev`) настраивает eudev, который нужен newm для
устройств. Затем, с включённым репозиторием `community`:

```sh
doas apk add git curl coreutils gcompat libstdc++ \
    mesa-dri-gallium mesa-egl mesa-gles mesa-gbm mesa-gl libdrm libinput-libs eudev-libs \
    libseat libxkbcommon xkeyboard-config xwayland libxcb libx11 \
    linux-pam fontconfig freetype font-dejavu dbus
```

- `mesa-*`: драйвер видеокарты для AMD, Intel и других.
- `libseat`: экран и ввод без root. В Alpine она собрана для работы с seatd или elogind.
- `xwayland`, `libxcb`: для программ X11.
- `linux-pam`: пароль на экране блокировки (приносит службу PAM `login`).
- `gcompat`, `libstdc++`: для Skiko (см. выше).
- `coreutils`: скрипт запуска newm использует ключи `ls` и `sort`, которых в busybox может не
  быть.

## 2. Место (seat)

Выберите что-то одно.

**seatd** (обычный путь в Alpine):

```sh
doas apk add seatd seatd-openrc
doas rc-update add seatd
doas rc-service seatd start
doas adduser "$USER" seat
```

**elogind**:

```sh
doas apk add elogind elogind-openrc
doas rc-update add elogind
doas rc-service elogind start
```

newm нужен ещё и `XDG_RUNTIME_DIR`. elogind его задаёт. С seatd это может сделать
`pam-rundir` (`doas apk add pam-rundir`, а где его включить — в вики Alpine, «Wayland»).

Выйдите и войдите снова; `echo $XDG_RUNTIME_DIR` должен показать папку.

## 3. Java 27

Пакета Java 26 или 27 в Alpine пока нет. У Temurin есть сборка под musl (`alpine-linux`).
Положите её в `/usr/lib/jvm`, где ищет команда `newm`:

```sh
doas mkdir -p /usr/lib/jvm/temurin-27
curl -L https://api.adoptium.net/v3/binary/latest/27/ga/alpine-linux/x64/jdk/hotspot/normal/eclipse \
  | doas tar -xz -C /usr/lib/jvm/temurin-27 --strip-components=1
export JAVA_HOME=/usr/lib/jvm/temurin-27     # для сборки ниже
```

На ARM напишите `aarch64` вместо `x64`.

## 4. Сборка и установка newm

`install.sh` вызывает `sudo`. В Alpine либо поставьте `sudo`, либо соберите и установите
вручную:

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./gradlew installDist
doas mkdir -p /usr/local/lib/newm
doas cp -r build/install/newm/. /usr/local/lib/newm/
doas ln -sf /usr/local/lib/newm/bin/newm /usr/local/bin/newm
```

`newm install-session` (пункт на экране входа и `/etc/newm`) тоже использует `sudo`, а если
его нет — `pkexec`. Запустите его от root: `doas newm install-session`.

## 5. Что стоит добавить

```sh
doas apk add foot pipewire pipewire-pulse wireplumber pulseaudio-utils brightnessctl \
    playerctl networkmanager bluez power-profiles-daemon \
    xdg-desktop-portal xdg-desktop-portal-gtk xdg-desktop-portal-wlr
```

PipeWire в Alpine работает от вашего пользователя; вики Alpine («PipeWire») показывает, как
запускать его вместе с сеансом. Это может делать `autostart { }` newm в
`~/.config/newm/config.kts`.

Для демонстрации экрана скажите порталу использовать wlr, в
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Запуск

Сеансовую шину D-Bus в Alpine за вас никто не запускает, а панели newm она нужна. Из
текстовой консоли:

```sh
dbus-run-session newm
```

Для экрана входа (`greetd` с `greetd-tuigreet` и `greetd-openrc` или SDDM) так же поставьте
`dbus-run-session` перед `newm`, как показано на странице [Void](void.md#запуск).

## Если что-то пошло не так

- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- Ошибки про `libskiko`, `libc.so.6`, `ld-linux` или недостающие символы в журнале: Skiko не
  загружается на musl. Это и есть открытый вопрос выше; пожалуйста, сообщите.
- `Could not open the seat`: seatd или elogind не запущен, или вы не в группе `seat`.
- `XDG_RUNTIME_DIR is not set`: см. [шаг 2](#2-место-seat).

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
