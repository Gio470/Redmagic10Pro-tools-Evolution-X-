package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.elitedarkkaiser.redmagic.R

/** The app's icons, from Font Awesome Free (FortAwesome/Font-Awesome, solid style). */
object Icons {

    /**
     * The solid face, loaded once. Null if the font resource cannot be read, in which case callers
     * leave the text as it is -- a missing typeface must not take the row down with it.
     */
    @Volatile private var cached: Typeface? = null

    fun typeface(context: Context): Typeface? {
        cached?.let { return it }
        val loaded = runCatching {
            ResourcesCompat.getFont(context, R.font.fa_solid_900)
        }.getOrNull()
        cached = loaded
        return loaded
    }

    // ---- Hardware ------------------------------------------------------------------------------

    /** `fan` */ const val FAN = "\uf863"
    /** `wand-magic-sparkles` */ const val AUTO_FAN = "\ue2ca"
    /** `wave-square` */ const val HAPTICS = "\uf83e"
    /** `play` */ const val TEST = "\uf04b"
    /** `toggle-on` */ const val MAGIC_KEY = "\uf205"
    /** `gamepad` */ const val TRIGGERS = "\uf11b"
    /** `sliders` */ const val CONFIGURE = "\uf1de"
    /** `arrow-rotate-left` */ const val UNLOCK = "\uf0e2"
    /** `power-off` */ const val AUTOSTART = "\uf011"
    /** `up-right-and-down-left-from-center` */ const val DENSITY = "\uf424"
    /** `check` */ const val CHECK = "\uf00c"
    /** `xmark` */ const val XMARK = "\uf00d"
    /** `arrow-left` -- the way out of a page. See PageHost. */ const val BACK = "\uf060"
    /** `angle-right` -- a row that opens a page. */ const val CHEVRON = "\uf105"
    /** `power-off` -- how many features are on. */ const val POWER = "\uf011"
    /** `temperature-half` */ const val TEMPERATURE = "\uf2c9"
    // ---- Trigger editor ------------------------------------------------------------------------
    /** `arrow-up` */ const val ARROW_UP = "\uf062"
    /** `arrow-down` */ const val ARROW_DOWN = "\uf063"
    /** `arrow-left` */ const val ARROW_LEFT = "\uf060"
    /** `arrow-right` */ const val ARROW_RIGHT = "\uf061"
    /** `chevron-up` */ const val CHEVRON_UP = "\uf077"
    /** `chevron-down` */ const val CHEVRON_DOWN = "\uf078"
    /** `rotate-left` */ const val UNDO = "\uf2ea"
    /** `lock` */ const val LOCK = "\uf023"
    /** `lock-open` */ const val LOCK_OPEN = "\uf3c1"
    /** `right-left` */ const val SWAP = "\uf362"
    /** `equals` */ const val ALIGN = "\uf52c"
    /** `crosshairs` */ const val TARGET = "\uf05b"
    /** `hand-pointer` */ const val TAP = "\uf25a"
    /** `stopwatch` */ const val HOLD = "\uf2f2"
    /** `fire` */ const val RAPID = "\uf06d"
    /** `layer-group` */ const val LAYOUTS = "\uf5fd"
    /** `plus` */ const val PLUS = "\uf067"
    /** `copy` */ const val COPY = "\uf0c5"
    /** `trash` */ const val TRASH = "\uf1f8"
    /** `pen` */ const val RENAME = "\uf304"

    // ---- Lighting ------------------------------------------------------------------------------

    /** `lightbulb` */ const val LED = "\uf0eb"
    /** `eye` */ const val PREVIEW = "\uf06e"
    /** `star` */ const val LOGO = "\uf005"
    /** `gamepad` */ const val GAME_MODE = "\uf11b"
    /** `table-cells-large` */ const val APP_LIST = "\uf009"
    /** `wand-magic-sparkles` */ const val PROFILE = "\ue2ca"
    /** `phone` */ const val CALLS = "\uf095"
    /** `pause` */ const val PAUSE = "\uf04c"

    /** `plug` */ const val CHARGING = "\uf1e6"

    // ---- Software -------------------------------------------------------------------------------

    /** `circle-half-stroke` */ const val MONOCHROME = "\uf042"
    /** `shield-halved` */ const val SHIELD = "\uf3ed"

    // ---- Software: root tools ------------------------------------------------------------------


    /** `tachometer-alt` */ const val PERF = "\uf3fd"
    /** `cloud` -- stands in for Google Play services, not the real brand mark. */ const val GMS = "\uf0c2"

    /** `microchip` */ const val PROCESSES = "\uf2db"

    /** `magnifying-glass` -- a search bar's leading icon. */ const val SEARCH = "\uf002"
    /** `circle-info` -- an info block's leading icon. */ const val INFO = "\uf05a"

    /** `circle-xmark` -- a search bar's clear button. */ const val CLEAR = "\uf057"


    // ---- Navigation bar -----------------------------------------------------------------------

    /** `house` */ const val HOME = "\uf015"
    /** `microchip` */ const val HARDWARE = "\uf2db"
    /** `laptop-code` */ const val SOFTWARE = "\uf5fc"
    /** `gear` */ const val SETTINGS = "\uf013"

    // ---- Magic Key ----------------------------------------------------------------------------

    /** `camera` */ const val CAMERA = "\uf030"
    /** `gamepad` */ const val GAME_SPACE = "\uf11b"
    /** `volume-high` */ const val SOUND_MODE = "\uf028"
    /** `lightbulb` */ const val FLASHLIGHT = "\uf0eb"
    /** `microphone` */ const val RECORDER = "\uf130"
    /** `arrow-up-right-from-square` */ const val LAUNCH_APP = "\uf08e"
    /** `ban` */ const val DISABLED = "\uf05e"

    // ---- Settings ------------------------------------------------------------------------------

    /** `bug` */ const val CRASH_LOG = "\uf188"
    /** `list` */ const val WRITE_LOG = "\uf03a"
    /** `circle-half-stroke` */ const val DYNAMIC_COLOR = "\uf042"
    /** `palette` */ const val COLOR_PALETTE = "\uf53f"
    /** `table-columns` */ const val DOCKED_BAR = "\uf0db"
    /** `layer-group` */ const val GLASS = "\uf5fd"
    /** `droplet` */ const val PURE_BLACK = "\uf043"
    /** `droplet`, for ReSukiSU's Opacity icon on Card transparency */ const val OPACITY = "\uf043"
    /** `image` */ const val BLUR = "\uf03e"
    /** `temperature-half` */ const val FAHRENHEIT = "\uf2c9"
    /** `circle-half-stroke` */ const val THEME_MODE = "\uf042"
    /** `wand-magic-sparkles` */ const val BACKGROUND = "\ue2ca"
    /** `wave-square`: Settings' motion intensity */ const val MOTION = "\uf83e"
    /** `memory` */ const val MEMORY = "\uf538"

    // ---- Choice icons --------------------------------------------------------------------------

    /** `sun` */ const val SUN = "\uf185"
    /** `moon` */ const val MOON = "\uf186"
    /** `minus` */ const val MINUS = "\uf068"
    /** `feather` */ const val FEATHER = "\uf52d"
    /** `basketball` */ const val BOUNCE = "\uf434"
    /** `circle` */ const val STEADY = "\uf111"
    /** `wind` */ const val BREATHE = "\uf72e"
    /** `bolt` */ const val FLASH = "\uf0e7"
    /** `water` */ const val FLOW = "\uf773"
    /** `burst` */ const val BURST = "\ue4dc"
    /** `leaf` */ const val QUIET = "\uf06c"
    /** `gauge` */ const val BALANCED = "\uf624"
    /** `rocket` */ const val TURBO = "\uf135"
    /** `fire` */ const val EXPERIMENTAL = "\uf06d"
    /** `phone-volume` */ const val INCOMING_CALL = "\uf2a0"
    /** `arrows-spin` */ const val CYCLE = "\ue4bb"
    /** `gauge-high` */ const val GAUGE_HIGH = "\uf625"
    /** `volume-high` */ const val VOLUME_UP = "\uf028"
    /** `volume-low` */ const val VOLUME_DOWN = "\uf027"
    /** `play` */ const val PLAY = "\uf04b"
    /** `forward-step` */ const val NEXT = "\uf051"
    /** `backward-step` */ const val PREVIOUS = "\uf048"
    /** `clock` */ const val CLOCK = "\uf017"
    /** `mobile-screen` */ const val PHONE_LIFT = "\uf3cf"
    /** `battery-half` */ const val BATTERY = "\uf242"
    /** `broom` */ const val BROOM = "\uf51a"
    /** `triangle-exclamation` */ const val WARNING = "\uf071"
    /** `charging-station` */ const val CHARGE_STATION = "\uf5e7"
}
