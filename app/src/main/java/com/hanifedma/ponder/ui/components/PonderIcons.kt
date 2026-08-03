package com.hanifedma.ponder.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The handful of icons the app needs, drawn from path data rather than pulled in
 * as a dependency — it keeps the download small, which is the whole spirit of
 * the web app.
 */
object PonderIcons {

    val Search = icon("search") {
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 " +
            "5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01," +
            "14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    }

    val Close = icon("close") {
        "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
    }

    val ArrowDropDown = icon("arrowDropDown") { "M7,10l5,5 5,-5z" }

    val ArrowBack = icon("arrowBack") {
        "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
    }

    val PlayArrow = icon("playArrow") { "M8,5v14l11,-7z" }

    val OpenInNew = icon("openInNew") {
        "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14," +
            "3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z"
    }

    val LightMode = icon("lightMode") {
        "M6.76,4.84l-1.8,-1.79 -1.41,1.41 1.79,1.79 1.42,-1.41zM4,10.5L1,10.5v2h3v-2zM13,0.55h-2L11," +
            "3.5h2L13,0.55zM20.45,4.46l-1.41,-1.41 -1.79,1.79 1.41,1.41 1.79,-1.79zM17.24,18.16l1.79," +
            "1.8 1.41,-1.41 -1.8,-1.79 -1.4,1.4zM20,10.5v2h3v-2h-3zM12,5.5c-3.31,0 -6,2.69 -6,6s2.69," +
            "6 6,6 6,-2.69 6,-6 -2.69,-6 -6,-6zM11,22.45h2L13,19.5h-2v2.95zM3.55,18.54l1.41,1.41 1.79," +
            "-1.8 -1.41,-1.41 -1.79,1.8z"
    }

    val DarkMode = icon("darkMode") {
        "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 " +
            "-2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4 -0.44,-0.06 " +
            "-0.9,-0.1 -1.36,-0.1z"
    }

    val Language = icon("language") {
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM18.92," +
            "8h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 " +
            "1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1," +
            "-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2s0.06,1.34 0.14,2H4.26zM5.08,16h2.95c0.32,1.25 " +
            "0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zM8.03,8H5.08c0.96,-1.66 2.49,-2.93 " +
            "4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43," +
            "1.43 -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32 -0.16,-2s0.07,-1.35 0.16," +
            "-2h4.68c0.09,0.65 0.16,1.32 0.16,2s-0.07,1.34 -0.16,2zM14.59,19.56c0.6,-1.11 1.06,-2.31 1.38," +
            "-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2s-0.06," +
            "-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z"
    }

    val Download = icon("download") { "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z" }

    val Shuffle = icon("shuffle") {
        "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 " +
            "20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z"
    }

    val Duplicates = icon("duplicates") {
        "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2," +
            "-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"
    }

    val MoreVert = icon("moreVert") {
        "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 " +
            "2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
    }

    val Person = icon("person") {
        "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8," +
            "4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"
    }

    val Logout = icon("logout") {
        "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0," +
            "1.1 0.9,2 2,2h8v-2H4V5z"
    }

    val Login = icon("login") {
        "M11,7L9.6,8.4l2.6,2.6H2v2h10.2l-2.6,2.6L11,17l5,-5L11,7zM20,19h-8v2h8c1.1,0 2,-0.9 2,-2V5c0," +
            "-1.1 -0.9,-2 -2,-2h-8v2h8v14z"
    }

    /**
     * Google's mark, built from the same four paths the web app's sign-in button
     * uses so the button is recognisably the real thing.
     */
    val Google: ImageVector = ImageVector.Builder(
        name = "google",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 18f,
        viewportHeight = 18f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.71" +
                    "-1.57 2.68-3.89 2.68-6.62z"
            ),
            fill = SolidColor(Color(0xFF4285F4)),
        )
        addPath(
            pathData = addPathNodes(
                "M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.02" +
                    "-3.7H.96v2.34A9 9 0 0 0 9 18z"
            ),
            fill = SolidColor(Color(0xFF34A853)),
        )
        addPath(
            pathData = addPathNodes(
                "M3.98 10.72a5.4 5.4 0 0 1 0-3.44V4.94H.96a9 9 0 0 0 0 8.12l3.02-2.34z"
            ),
            fill = SolidColor(Color(0xFFFBBC05)),
        )
        addPath(
            pathData = addPathNodes(
                "M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .96 4.94l3.02 " +
                    "2.34C4.68 5.16 6.66 3.58 9 3.58z"
            ),
            fill = SolidColor(Color(0xFFEA4335)),
        )
    }.build()

    /** Single-path 24dp icon, tinted by the caller. */
    private fun icon(name: String, path: () -> String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(path()),
                fill = SolidColor(Color.Black), // replaced by the Icon tint
            )
        }.build()
}
