package com.openmapsrouter.app.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Minimalist High-Contrast Dark Mode Palette
// ==========================================

// Background & Surfaces
val DarkBackground = Color(0xFF000000)          // True pure black background (#000000)
val DarkSurface = Color(0xFF141416)             // Slightly elevated dark gray container (#141416)
val DarkSurfaceElevated = Color(0xFF1C1C1F)     // Nested inputs & sub-containers (#1C1C1F)
val DarkSurfacePill = Color(0xFF222226)         // Pill button surface

// Borders & Outlines
val DarkBorder = Color(0xFF2A2A2A)              // Discrete 1px low-opacity outline (#2A2A2A)
val DarkBorderSubtle = Color(0xFF1F1F23)        // Subtle inner dividers (#1F1F23)
val DarkBorderActive = Color(0x66FFFFFF)        // High-contrast active outline

// High-Emphasis Accents
val PureWhite = Color(0xFFFFFFFF)               // Primary action CTA buttons, headlines, active tabs
val PureBlack = Color(0xFF000000)               // Bold text on pure white buttons

// Typography & Contrast Levels
val TextPrimaryDark = Color(0xFFFFFFFF)         // High-emphasis headings, values, primary button text
val TextSecondaryDark = Color(0xFF8E8E93)       // Medium-emphasis labels, coordinates, placeholders (#8E8E93)
val TextMutedDark = Color(0xFF636366)           // Low-emphasis hints, disabled states (#636366)

// Positive Status Indicators
val EmeraldGreen = Color(0xFF34C759)            // Muted emerald green (#34C759)
val EmeraldGreenContainer = Color(0x1F34C759)   // Delicate green badge background (12% opacity)
val EmeraldGreenBorder = Color(0x4D34C759)      // Delicate green ring (30% opacity)

// Error / Negative Status
val ErrorDarkBg = Color(0xFF231214)             // Dark red container background
val ErrorDarkBorder = Color(0x4DFF453A)         // Subtle red outline
val ErrorDarkText = Color(0xFFFF453A)           // High-contrast red message text

// ==========================================
// Compatibility Aliases for Existing Views
// ==========================================
val Background = DarkBackground
val SurfaceWhite = DarkSurface
val TextPrimary = TextPrimaryDark
val TextSecondary = TextSecondaryDark
val TextMuted = TextMutedDark
val BorderLight = DarkBorder
val BorderSubtle = DarkBorderSubtle

val OrganicGreen = EmeraldGreen
val OrganicGreenLight = EmeraldGreenContainer
val OrganicGreenBorder = EmeraldGreenBorder
val OrganicGreenDark = EmeraldGreen

val PrimaryBlue = PureWhite
val PrimaryBlueLight = DarkSurfacePill

val ErrorBg = ErrorDarkBg
val ErrorBorder = ErrorDarkBorder
val ErrorText = ErrorDarkText
