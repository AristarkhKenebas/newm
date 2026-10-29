# newm в других системах

[English](../../en/install/other.md) · **Русский**

Вашей системы нет в списке? newm не нужно ничего особенного: ядро Linux с DRM, glibc,
Java 27 и несколько распространённых библиотек. Здесь сказано, какие именно и как найти их
имена в вашей системе.

## Библиотеки

При сборке newm их не связывает: он открывает их по имени файла при запуске. Поэтому важно
только, чтобы эти файлы были — из любого пакета.

| Файл | Зачем | Нужен |
|---|---|---|
| `libEGL.so.1`, `libGLESv2.so.2` | рисование на видеокарте (libglvnd или Mesa) | да |
| `libgbm.so.1` | буферы видеокарты (Mesa; драйвер NVIDIA приносит свою часть GBM) | да |
| `libdrm.so.2` | мониторы | да |
| `libinput.so.10` | клавиатуры, мыши, тачпады | да |
| `libudev.so.1` | подключение и отключение устройств (systemd, eudev или libudev-zero) | да |
| `libseat.so.1` | экран и ввод без root (из seatd) | да |
| `libxkbcommon.so.0` и данные xkeyboard-config | раскладки клавиатуры | да |
| `libGL.so.1`, `libX11.so.6`, `libfontconfig.so.1` | Skiko, который рисует панель, лаунчер и экран блокировки newm | да |
| какие-нибудь шрифты | текст | да |
| `libpam.so.0` и `/etc/pam.d/login` | пароль на экране блокировки | для экрана блокировки |
| Xwayland 23.1 или новее, `libxcb.so.1`, `libxcb-composite.so.0`, `libxcb-xfixes.so.0`, `libxcb-render.so.0` | программы X11 | для программ X11 |

Проверьте, что у вас есть:

```sh
for f in libEGL.so.1 libGLESv2.so.2 libgbm.so.1 libdrm.so.2 libinput.so.10 libudev.so.1 \
         libseat.so.1 libxkbcommon.so.0 libGL.so.1 libX11.so.6 libfontconfig.so.1 libpam.so.0 \
         libxcb.so.1 libxcb-composite.so.0 libxcb-xfixes.so.0 libxcb-render.so.0; do
    ldconfig -p | grep -q "$f" && echo "ok       $f" || echo "MISSING  $f"
done
```

Если чего-то нет, newm и сам об этом скажет при запуске из текстовой консоли:
`Missing system libraries: ...`.

## Как найти имена пакетов

Спросите менеджер пакетов, в каком пакете файл:

| Система | Команда |
|---|---|
| Arch и родственные | `pacman -F libseat.so.1` (после `sudo pacman -Fy`) |
| Debian, Ubuntu | `apt-file search libseat.so.1` (после `sudo apt install apt-file && sudo apt-file update`) |
| Fedora, родственные RHEL | `dnf provides '*/libseat.so.1'` |
| openSUSE | `zypper search --provides 'libseat.so.1()(64bit)'` |
| Void | `xlocate libseat.so.1` (из `xtools`) |
| Alpine | `apk search so:libseat.so.1` |
| Gentoo | `e-file libseat.so.1` (из `app-portage/pfl`) |

Или поищите имя библиотеки на [pkgs.org](https://pkgs.org) или
[Repology](https://repology.org): имена обычно похожи (`seatd` или `libseat`, `libinput` или
`libinput10`, ...).

## Место (seat)

newm открывает видеокарту и устройства ввода через libseat, без root. libseat обращается к
одному из:

- **systemd-logind**: в любом дистрибутиве с systemd. Настраивать ничего не нужно.
- **elogind**: logind без systemd. Запустите его службу (и системную шину D-Bus). Он ещё и
  задаёт `XDG_RUNTIME_DIR` и сообщает newm, что компьютер засыпает, чтобы newm успел
  заблокировать экран.
- **seatd**: небольшой демон. Запустите его службу и добавьте себя в группу, которую он
  использует (`seat`, `_seatd` или `video`, смотря по системе).

Что из этого работает, зависит от того, как в вашей системе собрана libseat. С одним seatd
нужно ещё что-то, что задаёт `XDG_RUNTIME_DIR` (без него newm не запустится): elogind,
turnstile, pam_rundir или способ вашей системы.

## Java 27

См. [Java 27](../install.md#java-27) на общей странице: пакет, если он есть в вашей системе,
иначе архив Temurin в `/usr/lib/jvm`.

## Сборка и установка

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (через sudo)
./install.sh --user     # или только для вас: ~/.local
```

Без `install.sh`: `./gradlew installDist` собирает newm в `build/install/newm/`. Скопируйте
эту папку куда угодно и запускайте её `bin/newm`. Подробнее — в
[Установке](../install.md#сборка-и-установка).

## Файл сеанса

Экраны входа ищут сеансы в `/usr/share/wayland-sessions/`. `newm install-session` (его
запускает `install.sh`) пишет `/usr/share/wayland-sessions/newm.desktop`:

```ini
[Desktop Entry]
Name=newm
Comment=Windows on an endless wall, made for touchpads
Exec=/usr/local/bin/newm
TryExec=/usr/local/bin/newm
Type=Application
DesktopNames=newm
```

Без systemd сеансовую шину D-Bus при входе никто не запускает, а панели newm (трею,
уведомлениям, порталам) она нужна. newm делает это сам: `newm install-session` (его запускает
`install.sh`) записывает сеанс экрана входа как `dbus-run-session newm`, если шину не запускает
пользовательский сеанс systemd, а стартовый скрипт newm запускает newm внутри
`dbus-run-session`, когда шины нет, — и из текстовой консоли тоже. Нужно только, чтобы был
установлен пакет `dbus` (в нём `dbus-run-session`). Решить самому: `newm install-session
--dbus-run-session` (всегда) или `--no-dbus-run-session` (никогда; те же ключи у `install.sh`),
а `NEWM_DBUS=off` в окружении отключает это в стартовом скрипте.

## Экраны входа

- **GDM, SDDM, LightDM, ly**: они читают `/usr/share/wayland-sessions/`; выберите «newm» в
  списке сеансов.
- **greetd**: задайте команду приветствия в `/etc/greetd/config.toml`; строку `user`, которую
  написал ваш пакет, оставьте:

  ```toml
  [default_session]
  command = "tuigreet --time --remember --cmd newm"
  ```

  tuigreet может предлагать и сеансы из `/usr/share/wayland-sessions/` (ключ `--sessions`).
- **Сам newm**: greetd с newm в роли приветствия, см. [newm как экран входа](../greeter.md).
- **Никакого**: войдите в текстовой консоли и наберите `newm`.

## Порталы

Для окон выбора файлов и демонстрации экрана поставьте `xdg-desktop-portal`,
`xdg-desktop-portal-gtk` и `xdg-desktop-portal-wlr` и напишите
`~/.config/xdg-desktop-portal/newm-portals.conf`:

```ini
[preferred]
default=gtk
org.freedesktop.impl.portal.ScreenCast=wlr
org.freedesktop.impl.portal.Screenshot=wlr
```

## Видеокарта

- **Mesa** (AMD, Intel и другие): EGL, GLES и GBM из Mesa — и всё.
- **NVIDIA**: проприетарный драйвер 545 или новее, с его частью GBM, и kernel modesetting
  (`cat /sys/module/nvidia_drm/parameters/modeset` показывает `Y`; драйверы 560 и новее
  включают его сами, старым нужен `nvidia-drm.modeset=1` в командной строке ядра).
- **Виртуальная машина**: виртуальная видеокарта с 3D (virtio с 3D-ускорением).

## Если что-то пошло не так

Запустите newm из текстовой консоли: он простыми словами скажет, чего не хватает. Журнал —
`~/.local/state/newm/newm.log`. Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
