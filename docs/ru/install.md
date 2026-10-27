# Установка

[English](../en/install.md) · **Русский**

- [NixOS](#nixos) (и [home-manager](#home-manager))
- [Arch и другие дистрибутивы](#arch-и-другие-дистрибутивы)
- [Из исходников, вручную](#из-исходников-вручную)
- [Запуск](#запуск) и [первый раз](#первый-раз)

## NixOS

У flake newm есть пакет, модуль NixOS и модуль home-manager.

### Добавить в систему

В вашем `flake.nix`:

```nix
{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    newm.url = "github:AristarkhKenebas/newm";
  };

  outputs = { nixpkgs, newm, ... }: {
    nixosConfigurations.laptop = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        ./configuration.nix
        newm.nixosModules.default
        { programs.newm.enable = true; }
      ];
    };
  };
}
```

Или, если ваш `configuration.nix` получает входы flake:

```nix
{ newm, ... }:
{
  imports = [ newm.nixosModules.default ];
  programs.newm.enable = true;
}
```

Затем `sudo nixos-rebuild switch`. Первая сборка занимает несколько минут: newm собирается
Gradle.

Задавать `programs.newm.package` не нужно: модуль сам берёт newm из flake.

### Что делает модуль

- устанавливает команду `newm`;
- добавляет «newm» в список сеансов экрана входа (SDDM, GDM, ly, tuigreet у greetd, ...);
- включает Xwayland (для программ X11), polkit и порталы рабочего стола (`xdg-desktop-portal`
  с частями gtk и wlr: окна выбора файлов, демонстрация экрана);
- с `programs.newm.settings` пишет `/etc/newm/config.json` (см. [ниже](#настройки-для-всех)).

Экран блокировки проверяет пароль через службу PAM `login`, которая в NixOS есть всегда.

### Что стоит добавить

Панель newm пользуется несколькими программами и службами. Без них newm работает, но эта
часть панели пуста или ничего не делает:

```nix
{ pkgs, ... }:
{
  environment.systemPackages = with pkgs; [
    foot            # терминал (или alacritty, kitty, ...: newm откроет первый найденный)
    pulseaudio      # только ради `pactl`: окно громкости и выбор динамиков
    brightnessctl   # ползунок яркости
    playerctl       # клавиши «играть / дальше / назад»
  ];
  hardware.graphics.enable = true;               # драйверы видеокарты (с большинством окружений и так включено)
  services.pipewire = { enable = true; pulse.enable = true; wireplumber.enable = true; };  # звук; клавиши громкости используют wpctl
  networking.networkmanager.enable = true;       # окно Wi-Fi
  hardware.bluetooth.enable = true;              # окно Bluetooth
  services.power-profiles-daemon.enable = true;  # режимы питания в окне батареи
}
```

Экран входа не обязателен. Без него войдите в текстовой консоли и наберите `newm`. С ним
выберите «newm» в списке сеансов (например,
`services.displayManager.sddm = { enable = true; wayland.enable = true; };`).

**Демонстрация экрана** (браузеры, OBS, видеозвонки) идёт через портал wlr. Модуль делает
портал gtk основным; добавьте это, чтобы демонстрация экрана шла через wlr:

```nix
xdg.portal.config.newm = {
  default = [ "gtk" ];
  "org.freedesktop.impl.portal.ScreenCast" = [ "wlr" ];
  "org.freedesktop.impl.portal.Screenshot" = [ "wlr" ];
};
```

**Раскладки клавиатуры**: newm не читает `services.xserver.xkb`. Если в конфигурации newm
раскладки нет, он берёт раскладку вашего языка рядом с английской (с `LANG=ru_RU.UTF-8`:
«us,ru», переключение Alt+Shift). Чтобы выбрать свои, задайте их в `~/.config/newm/config.kts`:

```kotlin
keyboard { layouts = "us,ru"; options = "grp:win_space_toggle" }
```

### Настройки для всех

```nix
programs.newm = {
  enable = true;
  settings = {
    view = { corner_radius = 24; padding = 8; };
    keyboard = { layouts = "us,ru"; options = "grp:alt_shift_toggle"; };
  };
};
```

`settings` становится файлом `/etc/newm/config.json`. Важно знать две вещи:

- **Это вся конфигурация, а не поправка к стандартной.** В `config.json` только с двумя
  строками выше нет ни клавиш, ни панели. Начните с полного примера
  (`~/.config/newm/examples/config.json`, его пишет первый запуск newm) и меняйте его. Или не
  задавайте `settings` вовсе: тогда каждый пользователь получит `config.kts` с пояснением к
  каждой настройке.
- **Он копируется один раз.** Пользователь без `~/.config/newm` при первом запуске получает
  копию `/etc/newm`. Дальше действует его копия, и последующие изменения `settings` до него не
  доходят. Чтобы снова начать с системной, удалите `~/.config/newm`.

Имена настроек в JSON: [Lua, JSON, Nix](other-formats.md) и [Все настройки](settings.md).

### Попробовать без установки

В NixOS, из текстовой консоли (Ctrl+Alt+F3, войдите):

```sh
nix run github:AristarkhKenebas/newm
```

(Если flakes не включены: `nix --extra-experimental-features 'nix-command flakes' run github:AristarkhKenebas/newm`.)

Пакет приносит свою Java и библиотеки, которые открывает newm. Xwayland, терминал и
помощников панели (см. выше) он не приносит: используется то, что есть в системе.
Logo+Shift+Escape завершает его.

В другом дистрибутиве с Nix `nix run` обычно не находит драйверы видеокарты (их нет в путях
Nix). Там используйте [install.sh](#arch-и-другие-дистрибутивы).

### Как собирается пакет Nix

Пакет собирает newm из этого репозитория с помощью Gradle, без сети, из зависимостей,
перечисленных в `nix/deps.json`. Caulis, библиотека композитора под newm, берётся из Maven
Central, той версии, что записана в `gradle/libs.versions.toml`. (`install.sh` вместо этого
использует исходники Caulis, если они лежат рядом с newm, в `../caulis`; пакет Nix — никогда.)
Если сборка Nix останавливается на «Unresolved reference» в `dev.caulis...`, значит, этому
newm нужна ещё не выпущенная версия Caulis: пожалуйста, создайте issue.

Если сборка останавливается со словами «nix/deps.json (the list of Gradle's downloads) is not
there yet», список для этой версии newm ещё не сделан. Сделайте его в папке newm (нужна сеть
и версия Caulis из `gradle/libs.versions.toml` уже в Maven Central):

```sh
nix build .#default.mitmCache.updateScript && ./result
```

Он запишет `nix/deps.json`; дальше `nix build` работает (с флейком сначала `git add
nix/deps.json`: флейк видит только файлы, которые знает git).

### home-manager

Ваша собственная конфигурация, записанная в Nix:

```nix
{ newm, ... }:
{
  imports = [ newm.homeManagerModules.default ];

  programs.newm = {
    enable = true;
    # Kotlin, как его пишет newm (лучший вариант: все пояснения сохраняются):
    configKts = builtins.readFile ./newm/config.kts;
    # или Lua:   configLua = builtins.readFile ./newm/config.lua;
    # или JSON:  settings = { ... };   (вся конфигурация, как выше)
  };
}
```

- Задавайте только что-то одно из `configKts`, `configLua` и `settings`.
- `package`: не указывайте, если newm ставит модуль NixOS. Без модуля NixOS задайте
  `package = newm.packages.${pkgs.stdenv.hostPlatform.system}.default;`. (Сеанс на экране
  входа добавляет только модуль NixOS.)
- newm подхватывает новый файл, как только home-manager его запишет.

## Arch и другие дистрибутивы

### Что нужно

- **Java 27** для запуска newm (подойдёт и 26). В Arch: `sudo pacman -S jdk-openjdk`. Команда
  `newm` сама находит подходящую Java: `JAVA_HOME`, `java` из PATH или самую новую в
  `/usr/lib/jvm`.
- **Драйвер видеокарты** с OpenGL ES 3: Mesa (AMD, Intel) или NVIDIA 545 и новее.
- **Библиотеки, которые newm открывает во время работы.** В Arch:

  ```sh
  sudo pacman -S --needed mesa libglvnd libdrm libinput libxkbcommon seatd \
      xorg-xwayland libxcb pam fontconfig freetype2 libpng
  ```

  В Debian и Ubuntu (названия немного различаются между выпусками):

  ```sh
  sudo apt install libegl1 libgles2 libgbm1 libdrm2 libinput10 libudev1 libseat1 \
      libxkbcommon0 xwayland libxcb1 libxcb-composite0 libxcb-xfixes0 libxcb-render0 \
      libpam0g libfontconfig1 libfreetype6 libpng16-16
  ```

- **Терминал.** newm открывает `$TERMINAL`, а если его нет — первый найденный из alacritty,
  foot, kitty, wezterm, ghostty, gnome-terminal, konsole, xfce4-terminal и xterm.
- **Для панели и клавиш ноутбука** (всё необязательно; без программы эта часть ничего не
  делает):

  | Что | Arch | Debian / Ubuntu |
  |---|---|---|
  | клавиши громкости (`wpctl`) | `wireplumber` | `wireplumber` |
  | окно громкости (`pactl`) | `libpulse` | `pulseaudio-utils` |
  | ползунок и клавиши яркости | `brightnessctl` | `brightnessctl` |
  | клавиши «играть / дальше / назад» | `playerctl` | `playerctl` |
  | окно Wi-Fi | `networkmanager` (запущенный) | `network-manager` |
  | окно Bluetooth | `bluez` (запущенный) | `bluez` |
  | режимы питания | `power-profiles-daemon` | `power-profiles-daemon` |
  | выбор файлов, демонстрация экрана | `xdg-desktop-portal-gtk`, `xdg-desktop-portal-wlr` | то же |

Снимки экрана и история буфера обмена у newm свои: grim и менеджер буфера не нужны.

### Сборка и установка

```sh
git clone https://github.com/AristarkhKenebas/newm
cd newm
./install.sh            # для всех: /usr/local (спросит пароль)
./install.sh --user     # или только для вас: ~/.local
```

Скрипт:

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

## Из исходников, вручную

```sh
./gradlew installDist                       # build/install/newm/bin/newm
./gradlew installDist -Pcaulis.local=true   # то же, с исходниками ../caulis
build/install/newm/bin/newm                 # запустить (см. «Запуск»)
build/install/newm/bin/newm install-session # по желанию: пункт на экране входа
```

Для сборки Gradle сам скачает JDK 27, если его нет. Для запуска нужна установленная Java 26
или новее (см. выше).

## Запуск

- **Из текстовой консоли** (Ctrl+Alt+F3, войдите): наберите `newm`. В первый раз это лучший
  способ: если что-то не так, вы увидите, что пишет newm.
- **С экрана входа** (SDDM, GDM, ly, greetd): выберите «newm».
- **Внутри вашего рабочего стола, чтобы попробовать**: запустите `newm` в терминале. Он
  откроется окном, и в вашем рабочем столе ничего не изменится. (Клавиши вроде Logo может
  перехватывать ваш рабочий стол.)

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
