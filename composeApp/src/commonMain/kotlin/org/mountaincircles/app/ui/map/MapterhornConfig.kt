package org.mountaincircles.app.ui.map

/**
 * Mapterhorn terrain tile settings (aligned with MountainCircles---map Cloudflare branch).
 *
 * @see <a href="https://mapterhorn.com/">Mapterhorn</a>
 */
object MapterhornConfig {
    const val TILE_URL_TEMPLATE = "https://tiles.mapterhorn.com/{z}/{x}/{y}.webp"
    const val TILE_SIZE = 512
    const val MAX_ZOOM = 14
    const val ATTRIBUTION =
        "Terrain © Mapterhorn (https://mapterhorn.com/attribution)"

    const val HILLSHADE_EXAGGERATION = 0.4f
    const val HILLSHADE_ILLUMINATION_DIRECTION = 310f
    const val HILLSHADE_SHADOW_COLOR = 0xFF473B24
}
