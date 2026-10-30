package newm.bar

import newm.configuredValue
import java.util.Locale

// language = "auto" (the system's: LANG), "en" or "ru".
private val confLanguage = configuredValue("language", "auto")

/**
 * newm's words in your language: English as written, Russian from [RU] when the system's
 * language is Russian (LC_ALL, LC_MESSAGES, LANG) or the configuration says language = "ru".
 * Words with "%s" in them stand for any text there ("Connected to %s"). Words from outside
 * (a window's title, a network's or a device's name) given through [verbatim] stay as they are.
 */
fun tr(english: String): String {
    val out = translate(english)
    return if (KEEP_START in out || KEEP_END in out) out.filterNot { it == KEEP_START || it == KEEP_END } else out
}

// Around words from outside (Unicode's isolate marks: nothing to see even when left in).
private const val KEEP_START = '\u2068'
private const val KEEP_END = '\u2069'

/**
 * [text] as it is, never translated by [tr]: a window's title, a network's or a device's name,
 * alone or in the place of a "%s" ("Connected to %s"). [tr] takes the marks out again.
 */
fun verbatim(text: String): String = text.lines().joinToString("\n") { "$KEEP_START$it$KEEP_END" }

// Words marked by [verbatim], as a whole (a line of them, or what stands for a "%s").
private fun isVerbatim(text: String): Boolean =
    text.length >= 2 && text.first() == KEEP_START && text.last() == KEEP_END && text.indexOf(KEEP_START, 1) < 0

// The longest text matched against the "%s" sentences.
private const val MAX_TEMPLATE_TEXT = 1000

private fun translate(english: String): String {
    if (english.isEmpty() || language() != "ru" || isVerbatim(english)) return english
    RU[english]?.let { return it }
    // (newm's own sentences are short; a long text is someone else's words, and the patterns'
    // lazy "(.+?)" parts would only spend time going through it.)
    if (english.length <= MAX_TEMPLATE_TEXT) for ((_, regex, russian) in templates) {
        val m = regex.matchEntire(english) ?: continue
        var out = russian
        for (g in m.groupValues.drop(1)) out = out.replaceFirst("%s", translate(g))
        return out
    }
    // Several lines: each on its own.
    if ('\n' in english) return english.lines().joinToString("\n") { translate(it) }
    return english
}

/** The language of newm's words now: "en" or "ru". */
fun language(): String {
    val conf = runCatching { confLanguage() }.getOrDefault("auto").lowercase()
    if (conf != "auto") return if (conf.startsWith("ru")) "ru" else "en"
    return systemLanguage
}

/**
 * The locale of dates (the bar's clock, the calendar, the notifications' times): Russian with
 * newm's Russian words; with English ones the system's, unless that is Russian (English then).
 */
fun dateLocale(): Locale = when {
    language() == "ru" -> RUSSIAN
    Locale.getDefault().language == "ru" -> Locale.ENGLISH
    else -> Locale.getDefault()
}

private val RUSSIAN: Locale = Locale.forLanguageTag("ru")

private val systemLanguage: String by lazy {
    val env = listOf("LC_ALL", "LC_MESSAGES", "LANG").firstNotNullOfOrNull { System.getenv(it)?.takeIf { v -> v.isNotBlank() && v != "C" && v != "POSIX" } }
    val code = env?.substringBefore('_')?.substringBefore('.')?.lowercase() ?: Locale.getDefault().language
    if (code == "ru") "ru" else "en"
}

private val templates: List<Triple<String, Regex, String>> by lazy {
    RU.filterKeys { "%s" in it }.map { (en, ru) ->
        Triple(en, Regex(en.split("%s").joinToString("(.+?)") { Regex.escape(it) }), ru)
    }
}

private val RU: Map<String, String> = mapOf(
    // the bar
    "Programs" to "Программы",
    "No window" to "Нет окна",
    "Desktop" to "Рабочий стол",
    "All windows · the wheel goes through them" to "Все окна · колесо листает их",
    "Lock, sleep, log out, restart, shut down" to "Блокировка, сон, выход, перезагрузка, выключение",
    "Wired connection" to "Проводное подключение",
    "Address %s" to "Адрес %s",
    "Wi-Fi: %s · %s" to "Wi-Fi: %s · %s",
    "connected" to "подключено",
    "Not connected" to "Нет подключения",
    "Keyboard: %s (a click switches)" to "Клавиатура: %s (щелчок переключает)",
    "All windows" to "Все окна",
    "Bluetooth is off" to "Bluetooth выключен",
    "Bluetooth is on, nothing connected" to "Bluetooth включён, ничего не подключено",
    "Sound is off" to "Звук выключен",
    "Sound is unavailable" to "Звук недоступен",
    "Volume %s" to "Громкость %s",
    "Caffeine: the screen stays on" to "Кофеин: экран не гаснет",
    "The screen is kept on: %s" to "Экран не гаснет: %s",
    "%s is playing" to "%s играет",
    "keep-awake is on" to "включён keep-awake",
    // notifications
    "Notifications" to "Уведомления",
    "Notification" to "Уведомление",
    "Notifications go to %s — newm's own are off" to "Уведомления уходят в %s — уведомления newm выключены",
    "now" to "сейчас",
    "Do not disturb" to "Не беспокоить",
    "No cards, the history only" to "Без карточек, только история",
    "No notifications" to "Нет уведомлений",
    "New notifications: %s" to "Новых уведомлений: %s",
    "No new notifications" to "Нет новых уведомлений",
    "Do not disturb is on" to "Режим «Не беспокоить» включён",
    "The middle button: do not disturb" to "Средняя кнопка: не беспокоить",
    // Wi-Fi
    "Wi-Fi" to "Wi-Fi",
    "Scan" to "Искать",
    "NetworkManager is not running." to "NetworkManager не запущен.",
    "Connected" to "Подключено",
    "Connecting…" to "Подключение…",
    "Saved" to "Сохранена",
    "Open network" to "Открытая сеть",
    "Wi-Fi is off." to "Wi-Fi выключен.",
    "Looking for networks…" to "Поиск сетей…",
    "Disconnect" to "Отключить",
    "Connect" to "Подключить",
    "Forget" to "Забыть",
    "Password" to "Пароль",
    // the lock screen
    "Who are you?" to "Кто вы?",
    "That was not it. Again:" to "Неверно. Ещё раз:",
    "Checking…" to "Проверка…",
    "Caps Lock is on" to "Включён Caps Lock",
    "The keyboard layout is %s." to "Раскладка клавиатуры: %s.",
    "Wrong passwords since locking: %s" to "Неверных паролей с блокировки: %s",
    "%s more lock the account" to "ещё %s — и учётная запись заблокируется",
    "Too many wrong passwords: try again in %s" to "Слишком много неверных паролей: попробуйте через %s",
    "Too many wrong passwords: the account is locked for a while." to "Слишком много неверных паролей: учётная запись временно заблокирована.",
    "Too many wrong passwords: the account stays locked until an administrator unlocks it (faillock --reset)." to "Слишком много неверных паролей: учётная запись заблокирована, пока администратор её не разблокирует (faillock --reset).",
    "Checking the password took too long (a fingerprint reader waiting?). Again:" to "Проверка пароля шла слишком долго (ждёт сканер отпечатков?). Ещё раз:",
    "The password cannot be checked (PAM: %s). See newm's log." to "Пароль не проверить (PAM: %s). Подробности в журнале newm.",
    "The password cannot be checked (PAM failed). See newm's log." to "Пароль не проверить (ошибка PAM). Подробности в журнале newm.",
    "PAM cannot start (service %s, error %s). See newm's log." to "PAM не запускается (служба %s, ошибка %s). Подробности в журнале newm.",
    "newm cannot tell who you are (uid %s), so it cannot check a password. See newm's log." to "newm не знает, кто вы (uid %s), и не может проверить пароль. Подробности в журнале newm.",
    "Wi-Fi password" to "Пароль Wi-Fi",
    "For “%s”" to "Для «%s»",
    "Cancel" to "Отмена",
    "At least 8 characters" to "Не меньше 8 символов",
    "5 or 13 characters, or 10 or 26 hex digits" to "5 или 13 символов, или 10 или 26 шестнадцатеричных цифр",
    "Enterprise (802.1X) networks are not supported here" to "Корпоративные сети (802.1X) здесь не поддерживаются",
    "Wi-Fi cannot be switched: %s" to "Не удалось переключить Wi-Fi: %s",
    "Wrong password" to "Неверный пароль",
    "Could not connect (reason %s)" to "Не удалось подключиться (причина %s)",
    "Connected to %s" to "Подключено: %s",
    "Disconnected from %s" to "Отключено: %s",
    "Cable connected" to "Кабель подключён",
    "Wi-Fi connected" to "Wi-Fi подключён",
    "Wi-Fi disconnected" to "Wi-Fi отключён",
    "Disconnected" to "Отключено",
    "Cable disconnected" to "Кабель отключён",
    // Bluetooth
    "Bluetooth" to "Bluetooth",
    "Stop" to "Стоп",
    "Bluetooth is off." to "Bluetooth выключен.",
    "Your devices" to "Ваши устройства",
    "Nearby" to "Рядом",
    "Nearby (looking…)" to "Рядом (поиск…)",
    "No devices yet: the refresh button above looks for them." to "Устройств пока нет: кнопка обновления вверху начнёт поиск.",
    "%s: could not pair" to "%s: не удалось выполнить сопряжение",
    "Disconnecting…" to "Отключение…",
    "Pairing…" to "Сопряжение…",
    "Connected · battery %s" to "Подключено · заряд %s",
    "Click to pair" to "Щёлкните для сопряжения",
    "Bluetooth is blocked (airplane mode?)" to "Bluetooth заблокирован (режим полёта?)",
    // sound
    "Sound" to "Звук",
    "No sound device" to "Нет звукового устройства",
    "Output" to "Вывод",
    "Microphone" to "Микрофон",
    "off" to "выкл",
    // battery
    "Battery" to "Батарея",
    "Fully charged" to "Полностью заряжена",
    "Charging · full in %s" to "Заряжается · до полной %s",
    "Charging" to "Заряжается",
    "Plugged in · held at its charge limit" to "От сети · держится на пороге заряда",
    "%s left" to "Осталось %s",
    "On battery" to "От батареи",
    "Screen" to "Экран",
    "Brightness" to "Яркость",
    "Brightness %s" to "Яркость %s",
    "the wheel changes it" to "колесо меняет",
    "Power mode" to "Режим питания",
    "Percent at the bar" to "Проценты на панели",
    "Power saver" to "Экономия",
    "Balanced" to "Баланс",
    "Performance" to "Мощность",
    "Caffeine" to "Кофеин",
    "Quick actions" to "Быстрые действия",
    "Screenshot" to "Скриншот",
    "Clipboard" to "Буфер",
    "Dark" to "Тёмная",
    "Theme" to "Тема",
    "Light" to "Светлая",
    "On" to "Вкл",
    "%s more" to "ещё %s",
    "The screen stays on" to "Экран не гаснет",
    "Off" to "Выкл",
    "Until turned off" to "Пока не выключу",
    "On until turned off" to "Включён, пока не выключите",
    "On · %s more" to "Включён · ещё %s",
    "%s h %s min" to "%s ч %s мин",
    "%s min" to "%s мин",
    "%s h" to "%s ч",
    "30 min" to "30 мин",
    "1 h" to "1 ч",
    "2 h" to "2 ч",
    // power menu
    "Lock" to "Заблокировать",
    "Suspend" to "Сон",
    "Hibernate" to "Гибернация",
    "Log out" to "Выйти",
    "Restart" to "Перезагрузка",
    "Shut down" to "Выключить",
    "Colors…" to "Цвета…",
    "Screenshot…" to "Снимок экрана…",
    "%s? Click again" to "%s? Нажмите ещё раз",
    "Colors" to "Цвета",
    "Copy code" to "Копировать код",
    "Tonal" to "Тональная",
    "Vibrant" to "Яркая",
    "Expressive" to "Выразительная",
    "Neutral" to "Нейтральная",
    "Monochrome" to "Монохромная",
    "From the wallpaper" to "Из обоев",
    "The wallpaper is not read yet" to "Обои ещё не прочитаны",
    "Classic" to "Классическая",
    "newm's Nord colours" to "Цвета Nord от newm",
    "Palette copied: %s" to "Палитра скопирована: %s",
    // launcher, clipboard, keys, screenshots
    "Search programs" to "Поиск программ",
    "Looking for programs…" to "Ищу программы…",
    "Nothing called “%s”" to "Ничего не найдено: «%s»",
    "Frequent" to "Частые",
    "All programs" to "Все программы",
    "Search what was copied" to "Поиск по скопированному",
    "Clear all" to "Очистить всё",
    "Nothing copied yet — what you copy shows up here." to "Пока ничего не скопировано — всё, что вы копируете, появится здесь.",
    "Picture" to "Изображение",
    "Text" to "Текст",
    "All" to "Все",
    "Pictures" to "Картинки",
    "Files" to "Файлы",
    "%s files" to "файлов: %s",
    "Clear" to "Очистить",
    "The clipboard's history" to "История буфера обмена",
    "Copy the chosen one again" to "Скопировать выбранное снова",
    "Choose" to "Выбрать",
    "Go to the next part (kinds, Clear, the list, its buttons)" to "Следующая часть окна (вид, очистка, список, кнопки)",
    "Pin, so it stays" to "Закрепить, чтобы осталось",
    "Remove it" to "Удалить",
    "Close" to "Закрыть",
    "Don't show again" to "Больше не показывать",
    "Got it" to "Понятно",
    "Clear everything" to "Очистить всё",
    "Clear text" to "Очистить текст",
    "Clear pictures" to "Очистить картинки",
    "Clear files" to "Очистить файлы",
    "Tab part   ·   ↑ ↓ ← → or h j k l move   ·   Enter do   ·   Ctrl+P pin   ·   Delete remove   ·   Esc close" to "Tab часть окна   ·   ↑ ↓ ← → или h j k l двигаться   ·   Enter выполнить   ·   Ctrl+P закрепить   ·   Delete удалить   ·   Esc закрыть",
    "Pinned" to "Закреплённые",
    "Pin" to "Закрепить",
    "Unpin" to "Открепить",
    "Copy" to "Копировать",
    "Delete" to "Удалить",
    "Nothing here" to "Здесь пусто",
    "just now" to "только что",
    "%s min ago" to "%s мин назад",
    "%s characters" to "символов: %s",
    "%s lines" to "строк: %s",
    "↑ ↓ choose   ·   Enter copy   ·   Ctrl+P pin   ·   Delete remove   ·   Tab kind   ·   Esc close" to "↑ ↓ выбрать   ·   Enter копировать   ·   Ctrl+P закрепить   ·   Delete удалить   ·   Tab вид   ·   Esc закрыть",
    "%s lines · %s" to "строк: %s · %s",
    "%s · copied at %s" to "%s · скопировано в %s",
    "↑ ↓ choose   ·   Enter copy   ·   Delete remove   ·   Esc close" to "↑ ↓ выбрать   ·   Enter копировать   ·   Delete удалить   ·   Esc закрыть",
    "The clipboard's history is off (clipboard { enabled = true } turns it on)." to "История буфера обмена выключена (clipboard { enabled = true } включает её).",
    "Keys" to "Клавиши",
    "%s alone" to "только %s",
    "or" to "или",
    "3 fingers" to "3 пальца",
    "4 fingers up / down" to "4 пальца вверх / вниз",
    "5 fingers" to "5 пальцев",
    "Super + 1 finger" to "Super + 1 палец",
    "Super + left drag" to "Super + тянуть ЛКМ",
    "Super + right drag" to "Super + тянуть ПКМ",
    "Super + 2 fingers" to "Super + 2 пальца",
    "↑ ↓ choose   ·   Enter start   ·   Esc close" to "↑ ↓ выбрать   ·   Enter запустить   ·   Esc закрыть",
    "Any key closes this" to "Любая клавиша закрывает",
    "Touchpad" to "Тачпад",
    "Move over the wall" to "Двигаться по стене окон",
    "Zoom out / in" to "Отдалить / приблизить",
    "Launcher" to "Меню программ",
    "Move the window" to "Двигать окно",
    "Resize the window" to "Менять размер окна",
    "Area" to "Область",
    "Window" to "Окно",
    "Screen" to "Экран",
    "Pointer" to "Курсор",
    "Timer" to "Таймер",
    "Capture" to "Снять",
    "%s s" to "%s с",
    "Drag to choose a part first" to "Сначала выделите область",
    "Point at a window first" to "Сначала наведите на окно",
    "No window has the focus" to "Нет окна в фокусе",
    "Screenshot in %s" to "Снимок через %s",
    "Welcome to newm" to "Добро пожаловать в newm",
    "May %s take pictures of the screen?" to "Разрешить %s снимать экран?",
    "May %s use %s?" to "Разрешить %s доступ к %s?",
    "Allow" to "Разрешить",
    "Allow until newm quits" to "Разрешить до выхода из newm",
    "Deny" to "Запретить",
    "No D-Bus session bus: the tray, notifications and portals do not work. Install D-Bus (dbus-run-session), see newm's log." to
        "Нет сеансовой шины D-Bus: трей, уведомления и порталы не работают. Установите D-Bus (dbus-run-session), подробности в журнале newm.",
    "%s, left from another session, was closed: notifications are newm's now" to "%s от прошлой сессии закрыт: уведомления теперь у newm",
    "To choose newm at login (ly, SDDM, GDM): run  newm install-session  in a terminal." to
        "Чтобы выбирать newm при входе (ly, SDDM, GDM), выполните в терминале  newm install-session.",
    "Super+/ shows every key. Super+D starts a program, Super+S puts two windows side by side. On a touchpad: three fingers move, four zoom." to
        "Super+/ показывает все клавиши. Super+D запускает программу, Super+S ставит два окна рядом. На тачпаде: три пальца двигают, четыре приближают.",
    "Windows, whole" to "Окна целиком",
    "Move and resize the area" to "Область двигается и тянется",
    "A new area each time" to "Каждый раз новая область",
    "Clear the selection" to "Сбросить область",
    "Pointer: in the picture" to "Курсор: на снимке",
    "Pointer: left out" to "Курсор: без него",
    "Timer: %s s" to "Таймер: %s с",
    "Timer: off" to "Таймер: выкл.",
    "1 window" to "1 окно",
    "%s windows" to "Окон: %s",
    "Drag over windows or click them first" to "Сначала обведите окна или нажмите на них",
    "Screenshot saved and copied" to "Снимок сохранён и скопирован",
    "Screenshot saved" to "Снимок сохранён",
    "Screenshot copied" to "Снимок скопирован",
    // the template's key descriptions
    "Clipboard history" to "История буфера обмена",
    "Float, and back" to "Плавающее окно и обратно",
    "These keys" to "Эти клавиши",
    "Screenshot: choose a part" to "Снимок: выбрать область",
    "Screenshot of the screen" to "Снимок экрана",
    "Screenshot: choose" to "Снимок: выбрать",
    "Screenshot of the window" to "Снимок окна",
    "All windows (tap Super alone)" to "Все окна (нажмите Super отдельно)",
    "Brighter" to "Ярче",
    "Darker" to "Темнее",
    "Close the window" to "Закрыть окно",
    "Start a program" to "Запустить программу",
    "Window number n" to "Окно номер n",
    "Swallow the window into the terminal that started it" to "Спрятать окно в запустивший его терминал",
    "Next monitor" to "Следующий монитор",
    "Previous monitor" to "Предыдущий монитор",
    "Window onto the next monitor" to "Окно на следующий монитор",
    "Window onto the previous monitor" to "Окно на предыдущий монитор",
    "Special workspace, and back" to "Особое рабочее пространство и обратно",
    "Window into the special workspace" to "Окно в особое рабочее пространство",
    "Window back onto the wall" to "Окно обратно на стену",
    "An action of Caulis (volume +5, brightness -5, mic-mute)" to "Действие Caulis (volume +5, brightness -5, mic-mute)",
    "Find and start a program" to "Найти и запустить программу",
    "Fit the window to the screen" to "Подогнать окно под экран",
    "Fullscreen, and back" to "Полный экран и обратно",
    "Lock the screen" to "Заблокировать экран",
    "Louder" to "Громче",
    "Quieter" to "Тише",
    "Microphone off, and on" to "Микрофон выкл/вкл",
    "Sound off, and on" to "Звук выкл/вкл",
    "Move the window down" to "Переместить окно вниз",
    "Move the window left" to "Переместить окно влево",
    "Move the window right" to "Переместить окно вправо",
    "Move the window up" to "Переместить окно вверх",
    "Next window" to "Следующее окно",
    "Previous window" to "Предыдущее окно",
    "Next window of a stack" to "Следующее окно в стопке",
    "Open a terminal" to "Открыть терминал",
    "Reload this configuration" to "Перечитать конфигурацию",
    "Two windows side by side, and back" to "Два окна рядом и обратно",
    "Window above" to "Окно выше",
    "Window below" to "Окно ниже",
    "Window to the left" to "Окно слева",
    "Window to the right" to "Окно справа",
    "Window narrower" to "Окно уже",
    "Window wider" to "Окно шире",
    "Window shorter" to "Окно ниже ростом",
    "Window taller" to "Окно выше ростом",
    "Zoom in" to "Приблизить",
    "Zoom out" to "Отдалить",
    "Zoom in (see fewer)" to "Приблизить (меньше окон)",
    "Zoom out (see more)" to "Отдалить (больше окон)",
    // (After "Keyboard: %s (a click switches)": the longer one is tried first.)
    "Keyboard: %s" to "Клавиатура: %s",
)
