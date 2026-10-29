# newm в NixOS

[English](../../en/install/nixos.md) · **Русский**

У flake newm есть пакет, модуль NixOS и модуль home-manager. Пакет приносит свою Java 27 и
все библиотеки, которые открывает newm, так что вручную ставить больше ничего не нужно.

- [С flakes](#с-flakes)
- [Что делает модуль](#что-делает-модуль)
- [Что стоит добавить](#что-стоит-добавить)
- [Настройки для всех](#настройки-для-всех)
- [home-manager](#home-manager)
- [Без flakes](#без-flakes)
- [Попробовать без установки](#попробовать-без-установки)
- [Видеокарта](#видеокарта)
- [Как собирается пакет Nix](#как-собирается-пакет-nix)
- [Если что-то пошло не так](#если-что-то-пошло-не-так)

## С flakes

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

Задавать `programs.newm.package` не нужно: модуль сам берёт newm из flake. Пакеты есть для
`x86_64-linux` и `aarch64-linux`.

### Какие `follows` безопасны

У flake newm три входа:

| Вход | Что newm из него берёт | Следовать за вашим nixpkgs? |
|---|---|---|
| `nixpkgs` | библиотеки, которые newm открывает во время работы (Mesa, libinput, ...) | **можно**, и даже хорошо: тогда у newm та же Mesa, что у системы |
| `caulis` | Java 27 и список этих библиотек | оставьте как есть (он уже следует за `nixpkgs` newm) |
| `nixpkgs-gradle` | Gradle, которым newm **собирается** | **никогда**: он закреплён нарочно |

```nix
inputs.newm = {
  url = "github:AristarkhKenebas/newm";
  inputs.nixpkgs.follows = "nixpkgs";            # можно
  # inputs.nixpkgs-gradle.follows = "nixpkgs";   # нельзя: сборка остановится
};
```

newm собирается без сети из списка загрузок Gradle (`nix/deps.json`), а в этом списке файлы
для одной версии Gradle (`nix/deps-gradle-version`). Другой Gradle просит другие файлы
(например, сборку плагина Kotlin для этого Gradle), и сборка упала бы с «Could not find
kotlin-gradle-plugin-...-gradleNNN.jar». Поэтому newm всегда собирается Gradle из своего
`nixpkgs-gradle`, за каким бы nixpkgs ни следовало остальное. Если этот Gradle всё же
подменить, сборка сразу остановится со словами «nix/deps.json ... was made for Gradle X,
but this build got Gradle Y» и скажет, что делать.

## Что делает модуль

- устанавливает команду `newm`;
- добавляет «newm» в список сеансов экрана входа (SDDM, GDM, ly, tuigreet у greetd, ...);
- включает Xwayland (для программ X11), polkit и порталы рабочего стола (`xdg-desktop-portal`
  с частями gtk и wlr: окна выбора файлов, демонстрация экрана), причём для демонстрации
  экрана и снимков выбран wlr;
- передаёт newm `services.xserver.xkb` (раскладку, вариант, опции), если они заданы, а в
  конфигурации newm раскладки нет;
- с `programs.newm.settings` пишет `/etc/newm/config.json` (см. [ниже](#настройки-для-всех)).

Экран блокировки проверяет пароль через службу PAM `login`, которая в NixOS есть всегда.

## Что стоит добавить

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
выберите «newm» в списке сеансов. Например, SDDM:

```nix
services.displayManager.sddm = { enable = true; wayland.enable = true; };
```

или greetd с tuigreet:

```nix
services.greetd = {
  enable = true;
  settings.default_session.command = "${pkgs.tuigreet}/bin/tuigreet --time --remember --cmd newm";
};
```

Или сам newm как экран входа: `programs.newm.greeter.enable = true;` настраивает greetd с newm в роли приветствия, см. [newm как экран входа](../greeter.md#nixos).

(В старых nixpkgs пакет называется `pkgs.greetd.tuigreet`.)

**Раскладки клавиатуры**: если в конфигурации newm раскладки нет, newm берёт
`services.xserver.xkb`, когда он задан, а иначе — раскладку вашего языка рядом с английской
(с `LANG=ru_RU.UTF-8`: «us,ru», переключение Alt+Shift). Чтобы выбрать свои, задайте их в
`~/.config/newm/config.kts`:

```kotlin
keyboard { layouts = "us,ru"; options = "grp:win_space_toggle" }
```

## Настройки для всех

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

Имена настроек в JSON: [Lua, JSON, Nix](../other-formats.md) и [Все настройки](../settings.md).

## home-manager

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

## Без flakes

newm опубликован как flake, но конфигурация без flakes всё равно может его загрузить через
`builtins.getFlake`. Для этого в самом Nix должна быть включена возможность `flakes` (больше
ничего в вашей системе это не меняет):

```nix
# configuration.nix
{ ... }:
let
  newm = builtins.getFlake "github:AristarkhKenebas/newm";
in
{
  nix.settings.experimental-features = [ "nix-command" "flakes" ];
  imports = [ newm.nixosModules.default ];
  programs.newm.enable = true;
}
```

В первый раз запустите `sudo nixos-rebuild switch`, передав эту возможность в командной
строке, ведь она ещё не включена:

```sh
sudo NIX_CONFIG="experimental-features = nix-command flakes" nixos-rebuild switch
```

`getFlake` при каждой загрузке берёт самый новый newm; чтобы остаться на одной версии,
укажите коммит: `github:AristarkhKenebas/newm/<коммит>`.

## Попробовать без установки

В NixOS, из текстовой консоли (Ctrl+Alt+F3, войдите):

```sh
nix run github:AristarkhKenebas/newm
```

(Если flakes не включены: `nix --extra-experimental-features 'nix-command flakes' run github:AristarkhKenebas/newm`.)

Пакет приносит свою Java и библиотеки, которые открывает newm. Xwayland, терминал и
помощников панели (см. выше) он не приносит: используется то, что есть в системе.
Logo+Shift+Escape завершает его.

В другом дистрибутиве с Nix `nix run` обычно не находит драйверы видеокарты (их нет в путях
Nix). Там используйте [страницу установки](../install.md) своего дистрибутива.

## Видеокарта

- **AMD, Intel**: достаточно `hardware.graphics.enable = true;`.
- **NVIDIA**: проприетарный драйвер 545 или новее, с kernel modesetting:

  ```nix
  services.xserver.videoDrivers = [ "nvidia" ];
  hardware.nvidia.modesetting.enable = true;
  hardware.nvidia.open = true;   # открытые модули ядра (Turing и новее); false для старых карт
  ```

- **Виртуальная машина**: дайте ей виртуальную видеокарту с 3D (virtio с 3D-ускорением).

## Как собирается пакет Nix

Пакет собирает newm из этого репозитория с помощью Gradle, без сети, из зависимостей,
перечисленных в `nix/deps.json`. Caulis, библиотека композитора под newm, берётся из Maven
Central или из собственного Maven-репозитория Caulis на GitHub Pages
(`https://aristarkhkenebas.github.io/caulis/maven`, те же выпуски — на случай, когда версии ещё
нет в Central), той версии, что записана в `gradle/libs.versions.toml`. `nix/deps.json`
записывает каждый файл вместе с адресом, откуда он скачан, поэтому без сети работают оба. (`install.sh` вместо этого
использует исходники Caulis, если они лежат рядом с newm, в `../caulis`; пакет Nix — никогда.)
Если сборка Nix останавливается на «Unresolved reference» в `dev.caulis...`, значит, этому
newm нужна ещё не выпущенная версия Caulis: пожалуйста, создайте issue.

Если сборка останавливается со словами «nix/deps.json (the list of Gradle's downloads) is not
there yet», список для этой версии newm ещё не сделан. Сделайте его в папке newm (нужна сеть
и выпущенная версия Caulis из `gradle/libs.versions.toml` — в Maven Central или в её
репозитории на GitHub Pages):

```sh
nix run .#update-deps
```

Он сделает `nix/deps.json` закреплённым Gradle flake (вход `nixpkgs-gradle`) и запишет версию
этого Gradle в `nix/deps-gradle-version`; чтобы перейти на другой Gradle, поменяйте ревизию
`nixpkgs-gradle` во `flake.nix` и запустите снова. Дальше `nix build` работает (с флейком
сначала `git add nix/deps.json nix/deps-gradle-version`: флейк видит только файлы, которые
знает git).

Если сборка останавливается со словами «nix/deps.json ... was made for Gradle X, but this
build got Gradle Y», вход `nixpkgs-gradle` у newm заставили следовать за другим nixpkgs или
подменили его Gradle: см. [Какие `follows` безопасны](#какие-follows-безопасны).

## Если что-то пошло не так

- Запустите newm из текстовой консоли (Ctrl+Alt+F3, войдите, `newm`): он скажет, чего не хватает.
- Журнал: `~/.local/state/newm/newm.log` (предыдущий запуск: `newm.log.old`).
- «No GPU was found»: `hardware.graphics.enable = true;`.
- Нет клавиш или панели с `programs.newm.settings`: этот файл — вся конфигурация
  (см. [выше](#настройки-для-всех)).

Подробнее — в [Если что-то пошло не так](../troubleshooting.md).
