package com.mantz_it.rfanalyzer.ui.composable

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.mantz_it.rfanalyzer.R
import com.mantz_it.rfanalyzer.database.COUNTRY_CODE_TO_NAME
import com.mantz_it.rfanalyzer.ui.RFAnalyzerTheme
import kotlinx.coroutines.delay
import java.io.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.security.MessageDigest
import kotlin.String
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.reflect.KClass

/**
 * <h1>RF Analyzer - Helper Composables</h1>
 *
 * Module:      HelperCompsables.kt
 * Description: Custom Composable components which can be reused across the app.
 *
 * @author Dennis Mantz
 *
 * Copyright (C) 2025 Dennis Mantz
 * License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */


val DEFAULT_MIN_BOX_HEIGHT = 70.dp

// Define your CompositionLocal for SnackbarHostState
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}

val LocalShowHelp = compositionLocalOf<(String) -> Unit> {
    error("LocalShowHelp not provided")
}

fun sha256(input: String): String {
    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

// extension function for Long values which hold a size in bytes
fun Long.asSizeInBytesToString(): String {
    val sizeInMB = this / (1024 * 1024)
    return if (sizeInMB < 1024) {
        "$sizeInMB MB"
    } else {
        // Otherwise, display in GB
        val sizeInGB = this / (1024F * 1024F * 1024F)
        "%.2f GB".format(sizeInGB)  // Format to 2 decimal places
    }
}

// extension function for Long values which hold numbers like frequency, sample rate, ...
fun Long.asStringWithUnit(unit: String): String {
    val units = listOf("", "k", "M", "G", "T")  // SI prefixes
    var value = this
    var index = 0
    while (value % 1000L == 0L && value >= 1000L && index < units.lastIndex) {
        value /= 1000L
        index++
    }
    val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }
    val formatter = DecimalFormat("#,###", symbols)
    return "${formatter.format(value)} ${units[index]}$unit"
}

// extension function for Long values which hold timestamps
fun Long.asDateTimeWithRelative(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val timestamp = Instant.ofEpochMilli(this)
    val dateTime = LocalDateTime.ofInstant(timestamp, zoneId)
    val formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()))
    val duration = Duration.between(timestamp, now)
    val days = duration.toDays()
    val hours = duration.toHours()
    val minutes = duration.toMinutes()
    val relative = when {
        minutes < 1 -> "just now"
        minutes < 60 -> pluralize(minutes, "minute") + " ago"
        hours < 24 -> pluralize(hours, "hour") + " ago"
        days < 30 -> pluralize(days, "day") + " ago"
        days < 365 -> pluralize(days / 30, "month") + " ago"
        else -> pluralize(days / 365, "year") + " ago"
    }
    return "$formattedDate ($relative)"
}

fun Long.toTimeSpanString(): String {
    if (this <= 0) return pluralize(0, "second")
    var remaining = this / 1000 // milliseconds → seconds
    val days = remaining / 86_400
    remaining %= 86_400
    val hours = remaining / 3_600
    remaining %= 3_600
    val minutes = remaining / 60
    val seconds = remaining % 60

    val parts = mutableListOf<String>()
    if (days > 0) parts += pluralize(days, "day")
    if (hours > 0) parts += pluralize(hours, "hour")
    if (minutes > 0) parts += pluralize(minutes, "minute")
    if (seconds > 0) parts += pluralize(seconds, "second")

    return when (parts.size) {
        0 -> pluralize(0, "second")
        1 -> parts[0]
        2 -> parts.joinToString(" and ")
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

data class Quadruple<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D) : Serializable
fun <T> Quadruple<T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth)
data class Quintuple<out A, out B, out C, out D, out E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E) : Serializable
fun <T> Quintuple<T, T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth, fifth)

private fun pluralize(value: Long, unit: String): String {
    return "$value $unit" + if (value != 1L) "s" else ""
}

// get black or white color depending on the color
fun Color.contrastingTextColor(): Color {
    val argb = this.toArgb()
    val r = (argb shr 16 and 0xFF)
    val g = (argb shr 8 and 0xFF)
    val b = (argb and 0xFF)

    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminance > 186) Color.Black else Color.White
}

// saturation functions for smoother user input (e.g. for tuner wheel or zoom slider)
// see https://www.desmos.com/calculator/s93u7whlpo
fun saturationFunction(x: Float, a: Float, k: Float): Float {
    //x^{a}/(x^{a}+k^{a})
    val xPowA = x.pow(a)
    return xPowA / (xPowA + k.pow(a))
}
fun reverseSaturationFunction(y: Float, a: Float, k: Float): Float {
    // ((yk^a)/(1-y))^(1/a)
    return (y * k.pow(a) / (1 - y)).pow(1/a)
}

// A Transformation class that can transform frequency strings and add thousand separators as well as a unit
// example (separator=".", unit="Hz"), input: "123000678"  -->  "123.000.678 Hz"
class ThousandSeparatorTransformation(val separator: String = " ", val unit: String = "", val highlightedIndex: Int? = null, val highlightColor: Color = Color.Yellow) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text

        // Add separator after every 3rd char:
        val sb = StringBuilder()
        var count = 0
        for (char in originalText.reversed()) {
            if (count > 0 && count % 3 == 0) {
                sb.append(separator) // Add separator after every 3rd digit
            }
            sb.append(char)
            count++
        }
        val formattedText = "${sb.reverse()} $unit" // Reverse back to normal order and append unit

        // Build a mapping from each index in the transformed text to the index in the original text.
        // We go through each character of the formatted text and for each position, we record how many digits we've seen.
        val mapping = mutableListOf<Int>()
        var digitCount = 0
        for (char in formattedText) {
            mapping.add(digitCount)
            if (char.isDigit()) {
                digitCount++
            }
        }
        // Add mapping for the end of the text.
        mapping.add(digitCount)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // Find the last index in mapping that has the value equal to 'offset'.
                // This ensures that when the cursor moves in the original text, it lands in the correct position in the transformed text.
                return mapping.indexOfLast { it == offset }.takeIf { it != -1 } ?: formattedText.length
            }
            override fun transformedToOriginal(offset: Int): Int {
                // Return the original index from our mapping.
                return mapping.getOrElse(offset) { originalText.length }
            }
        }

        // Build formatted text with highlight and styles
        val annotatedText = buildAnnotatedString {
            var leadingZero = true // Track leading zeros

            formattedText.forEachIndexed { index, char ->
                // Detect if we're still in the leading zero phase (ignoring separators)
                if (leadingZero && char.isDigit() && char != '0') {
                    leadingZero = false
                }

                val backgroundColor = if (highlightedIndex != null && index == offsetMapping.originalToTransformed(highlightedIndex)) highlightColor
                                      else Color.Transparent
                val indexOfHundredDecimal = formattedText.length - " $unit".length - 3
                val fontSize = if (index in indexOfHundredDecimal..indexOfHundredDecimal+2) 18.sp else SpanStyle().fontSize
                val textColor = if (leadingZero && char.isDigit()) Color.Gray else SpanStyle().color
                withStyle(SpanStyle(color = textColor, fontSize = fontSize, background = backgroundColor)) {
                    append(char)
                }
            }
        }
        return TransformedText(annotatedText, offsetMapping)
    }
}

@Composable
fun FrequencyChooser(
    label: String,
    unit: String,
    currentFrequency: Long,
    onFrequencyChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    minFrequency: Long = 0,
    maxFrequency: Long = 999_999_999_999L,
    enabled: Boolean = true,
    digitCount: Int = 12,
    liveUpdate: Boolean = false,     // trigger onFrequencyChanged callback on every value change (if valid) and don't show 'apply' button
    insertMode: Boolean = true,
    helpSubPath: String? = null,
) {
    var lastObservedFrequency by remember { mutableStateOf(currentFrequency) }
    var displayedFrequency by remember { mutableStateOf(TextFieldValue("%0${digitCount}d".format(currentFrequency), TextRange(0))) }  // might differ from currentFrequency when the user is in the process of entering a new value
    var isValid by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var buttonsVisible by remember { mutableStateOf(!liveUpdate && isFocused) }
    LaunchedEffect(isFocused) {
        if(!liveUpdate && isFocused) {
            delay(300)
            buttonsVisible = true
        } else
            buttonsVisible = false
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface

    // check whether the current frequency changed and set the displayed frequency accordingly
    if(currentFrequency != lastObservedFrequency) {
        lastObservedFrequency = currentFrequency
        displayedFrequency = displayedFrequency.copy(text = "%0${digitCount}d".format(currentFrequency))
    }

    fun checkValid() {
        val newFreq = displayedFrequency.text.toLongOrNull()
        if (newFreq != null) {
            isValid = minFrequency <= newFreq && newFreq <= maxFrequency
        } else
            isValid = false
    }
    OutlinedBox(
        label = if (isValid) label else "$label (range: $minFrequency - $maxFrequency)",
        modifier = modifier.height(70.dp),
        background = background,
        enabled = enabled,
        helpSubPath = helpSubPath
    ) {
        BasicTextField(
            value = displayedFrequency,
            onValueChange = { newText ->
                val newFilteredText = TextFieldValue(
                    text = buildString {
                        for (char in newText.text) {
                            append(if (char in '0'..'9') char else '0')
                        }
                    },
                    selection = newText.selection)
                if (insertMode) {
                    if (displayedFrequency.text.length != newFilteredText.text.length) {
                        if (newFilteredText.text.length < displayedFrequency.text.length) {
                            // A character was deleted
                            val deletedIndex = newFilteredText.selection.start
                            val newString = newFilteredText.text.substring(0, deletedIndex) + "0" + newFilteredText.text.substring(deletedIndex)
                            displayedFrequency = TextFieldValue(text = newString, selection = TextRange(deletedIndex))
                        } else {
                            // A character was inserted
                            val overwrittenIndex = minOf(newFilteredText.selection.start, newFilteredText.text.length-1)
                            val newString = newFilteredText.text.removeRange(overwrittenIndex, overwrittenIndex+1)
                            val newStringLimited = newString.substring(0, minOf(newString.length, digitCount))  // copy-pasting can add more than one char at once. then we need to limit the length to digitCount
                            displayedFrequency = TextFieldValue(text = newStringLimited, selection = TextRange(overwrittenIndex))
                        }
                    } else {
                        displayedFrequency = newFilteredText  // only the cursor position has changed
                    }
                    // make sure cursor is not at the very end of the text
                    if (displayedFrequency.text.length == displayedFrequency.selection.start)
                        displayedFrequency = displayedFrequency.copy(selection = TextRange(displayedFrequency.text.length - 1))
                } else {
                    displayedFrequency = newFilteredText // Fallback if not in insert mode
                }
                checkValid()
                if(liveUpdate && isValid) onFrequencyChanged(displayedFrequency.text.toLong())
            },
            textStyle = TextStyle(fontSize = 28.sp, letterSpacing = 2.sp, textAlign = TextAlign.End, color = textColor),
            maxLines = 1,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd // match your TextAlign.End
                ) {
                    innerTextField()
                }
            },
            visualTransformation = ThousandSeparatorTransformation(
                unit = unit,
                highlightedIndex = if (insertMode && isFocused) displayedFrequency.selection.start else null,
                highlightColor = highlightColor),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    checkValid()
                    if(isValid) {
                        onFrequencyChanged(displayedFrequency.text.toLong())
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }
            ),
            enabled = enabled,
            interactionSource = interactionSource,  // for detecting whether we are focused
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focusState ->
                    // Restore current frequency when focus is lost:
                    if (!focusState.isFocused) {
                        displayedFrequency = displayedFrequency.copy(text = "%0${digitCount}d".format(currentFrequency))
                        checkValid()
                    }
                }
        )

        AnimatedVisibility(
            visible = buttonsVisible,
            enter = scaleIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-62).dp)
        ) {
            Row {
                FloatingActionButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.height(50.dp).width(70.dp).padding(end = 2.dp)
                ) {
                    Text("Cancel")
                }
                FloatingActionButton(
                    onClick = {
                        checkValid()
                        if (isValid) {
                            onFrequencyChanged(displayedFrequency.text.toLong())
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    containerColor = if(isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    contentColor = if(isValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    modifier = Modifier.height(50.dp).width(70.dp)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
fun OutlinedBox(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    helpSubPath: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val showHelp = if (helpSubPath != null) LocalShowHelp.current else null
    val actualBorderColor = borderColor.copy(alpha = if(enabled) 1f else 0.2f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = DEFAULT_MIN_BOX_HEIGHT)  // This is the minimum and default height of all components
            .background(background)
            .padding(start = 1.dp, end = 1.dp, top = 12.dp, bottom = 0.dp)
            .drawBehind {
                drawRoundRect(
                    color = actualBorderColor,
                    cornerRadius = CornerRadius(8f, 8f),
                    size = size,
                    style = Stroke(width = 2f)
                )
            }
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)
    ) {
        Box(modifier = Modifier.then(
                if (helpSubPath != null && showHelp != null)
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showHelp(helpSubPath) } )  // Trigger help on long press on label-box
                    }
                else Modifier
            )
            .offset(x = 6.dp, y = (-14.5f).dp)
            .background(background)) {
            Text(label,
                style = TextStyle(fontSize = 14.sp),
                color = actualBorderColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        content()
    }
}

@Composable
fun <T> OutlinedSteppedSlider(
    label: String,
    steps: List<T>,
    selectedStepIndex: Int,
    modifier: Modifier = Modifier,
    unit: String? = null,
    unitInLabel: Boolean = true,
    formatValue: (T) -> String = { value -> value.toString() },
    helpSubPath: String? = null,
    onSelectedStepIndexChanged: (Int) -> Unit
) {
    val displayedUnitInOutline = if (unit.isNullOrEmpty() || !unitInLabel) "" else "($unit)"
    val displayedUnitInText = if (unit.isNullOrEmpty() || unitInLabel) "" else " $unit"
    val maxNumberOfChars = steps.maxBy { formatValue(it).length }.let { (formatValue(it) + displayedUnitInText).length }
    OutlinedBox(label="$label $displayedUnitInOutline", modifier = modifier, helpSubPath = helpSubPath) {
        Row {
            Slider(
                value = selectedStepIndex.toFloat(),
                onValueChange = { onSelectedStepIndexChanged(it.roundToInt()) },
                valueRange = 0f..(steps.size - 1).toFloat(),
                steps = if (steps.size >= 2) steps.size - 2 else 0,
                modifier = Modifier.weight(1f).padding(start = 10.dp)
            )
            Text(
                text = "${formatValue(steps[selectedStepIndex])}$displayedUnitInText",
                fontSize = 20.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(5.dp + 15.dp*maxNumberOfChars)
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun OutlinedSlider(
    label: String,
    unit: String,
    minValue: Float,
    maxValue: Float,
    value: Float,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    showOutline: Boolean = true,
    decimalPlaces: Int = 1,
    unitInLabel: Boolean = true,
    helpSubPath: String? = null,
    onValueChanged: (Float) -> Unit
) {
    val maxNumberOfCharsWithoutUnit = max("%.${decimalPlaces}f".format(minValue).length, "%.${decimalPlaces}f".format(maxValue).length)
    val maxNumberOfChars = if (unitInLabel) maxNumberOfCharsWithoutUnit else maxNumberOfCharsWithoutUnit + " $unit".length
    val content: @Composable  BoxScope.() -> Unit = {
        Row(modifier = Modifier.align(Alignment.Center)) {
            Slider(
                value = value,
                onValueChange = onValueChanged,
                valueRange = minValue..maxValue,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            Text(
                text = "%.${decimalPlaces}f".format(value, unit) + (if(unitInLabel) "" else " $unit"),
                fontSize = 20.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(5.dp + 14.dp*maxNumberOfChars)
                    .padding(horizontal = 4.dp)
            )
        }
    }
    if (showOutline) {
        OutlinedBox(
            label = if (unitInLabel) "$label ($unit)" else label,
            modifier = modifier,
            background = background,
            helpSubPath = helpSubPath,
            content = content
        )
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun ScrollableColumnWithFadingEdge(
    modifier: Modifier = Modifier,
    fadeHeight: Dp = 70.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val fadeHeightPx = with(density) { fadeHeight.toPx() }
    val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue && scrollState.maxValue > 0 } }

    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()

                // If we don't need to fade either side, return early to save drawing cost
                if (!canScrollUp && !canScrollDown) return@drawWithContent

                // Define colors based on scroll state (Transparent = fade out, Black = keep visible)
                val topColor = if (canScrollUp) Color.Transparent else Color.Black
                val bottomColor = if (canScrollDown) Color.Transparent else Color.Black

                val topFadeEndPx = fadeHeightPx.coerceAtMost(size.height / 2)
                val bottomFadeStartPx = (size.height - fadeHeightPx).coerceAtLeast(size.height / 2)

                // Draw the gradient stops (normalized 0f..1f range)
                val colorStops = arrayOf(
                    0f to topColor,
                    (topFadeEndPx / size.height) to Color.Black,
                    (bottomFadeStartPx / size.height) to Color.Black,
                    1f to bottomColor
                )
                drawRect(brush = Brush.verticalGradient(colorStops = colorStops), blendMode = BlendMode.DstIn)
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.verticalScroll(scrollState),
            content = content
        )
    }
}

@Preview
@Composable
fun Prev() {
    Column {
        Row {
            OutlinedEnumDropDown(
                label = "Demodulation Mode",
                enumClass = DemodulationMode::class,
                getDisplayName = { it.displayName },
                selectedEnum = DemodulationMode.USB,
                onSelectionChanged = {  },
                modifier = Modifier.weight(0.4f)
            )
            FrequencyChooser(
                label = "Station Bandwidth",
                unit = "Hz",
                digitCount = 6,
                currentFrequency = 123400,
                onFrequencyChanged = {  },
                liveUpdate = true,
                modifier = Modifier.weight(0.6f)
            )
        }
        OutlinedBox(label="this is my label") { Text("This is my inner text", modifier = Modifier.padding(10.dp)) }
        OutlinedSlider("label", "Hz", 0f, 10f, 4f, showOutline = false) { }
        FrequencyChooser(
            "test",
            unit = "Hz",
            currentFrequency = 123,
            onFrequencyChanged = { },
            minFrequency = 1,
            maxFrequency = 10000,
        )
        Row {
            OutlinedSlider("slider 1", "Hz", 0f, 100f, 100f, modifier = Modifier.weight(1f)) { }
            OutlinedSlider("slider 2", "XX", 0f, 100f, 100f, decimalPlaces = 0, unitInLabel = false, modifier = Modifier.weight(1f)) { }
        }
        Row {
            OutlinedSlider("slider 1", "Hz", 0f, 1000f, 4f, modifier = Modifier.weight(1f)) { }
            FrequencyChooser(
                "test",
                unit = "Hz",
                currentFrequency = 123,
                onFrequencyChanged = { },
                minFrequency = 1,
                maxFrequency = 10000,
                modifier = Modifier.weight(2f)
            )
        }
        OutlinedSwitch(
            label = "Use Channel Squelch",
            helpText = "Only write samples while squelch is satisfied. AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            isChecked = true,
            onCheckedChange = { },
            enabled = true
        )
        OutlinedSwitch(
            label = "embedded",
            helpText = "this has embedded content",
            isChecked = true,
            onCheckedChange = { },
            enabled = true
        ) {
            Button(
                onClick = { },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 4.dp).fillMaxWidth()
            )
            {
                Text("Buy Full Version", modifier = Modifier.padding(end = 10.dp))
                Icon(Icons.Default.ShoppingCart, "Buy Full Version")
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedSlider("slider 1", "Hz", 0f, 1000f, 4f, modifier = Modifier.weight(1f)) { }
            OutlinedSwitch(
                label = "Use Channel Squelch",
                helpText = "Only write samples while squelch is satisfied",
                isChecked = true,
                onCheckedChange = { },
                enabled = true,
                fontSize = 8.sp,
                modifier = Modifier.weight(0.5f)
            )
        }
        OutlinedEnumDropDown(
                label = "Signal Source",
                selectedEnum = SourceType.FILESOURCE,
                enumClass = SourceType::class,
                getDisplayName = { it.displayName },
                onSelectionChanged = { },
                enabled = true,
        )
        OutlinedListDropDown(
            label = "Signal Source2",
            getDisplayName = { "$it" },
            onSelectionChanged = { },
            enabled = true,
            modifier = Modifier.width(200.dp),
            items = listOf(1, 2, 5),
            selectedItem = 3,
        )
        Row {
            OutlinedSlider("slider 1", "Hz", 0f, 1000f, 4f, modifier = Modifier.weight(1f)) { }
            OutlinedEnumDropDown(
                label = "Signal Source",
                selectedEnum = SourceType.FILESOURCE,
                enumClass = SourceType::class,
                getDisplayName = { it.displayName },
                onSelectionChanged = { },
                enabled = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Preview
@Composable
fun ColorSchemePreview() {
    RFAnalyzerTheme {
        val colorScheme = MaterialTheme.colorScheme
        val colors = listOf(
            "primary" to colorScheme.primary,
            "onPrimary" to colorScheme.onPrimary,
            "primaryContainer" to colorScheme.primaryContainer,
            "onPrimaryContainer" to colorScheme.onPrimaryContainer,
            "secondary" to colorScheme.secondary,
            "onSecondary" to colorScheme.onSecondary,
            "secondaryContainer" to colorScheme.secondaryContainer,
            "onSecondaryContainer" to colorScheme.onSecondaryContainer,
            "background" to colorScheme.background,
            "onBackground" to colorScheme.onBackground,
            "outline" to colorScheme.outline,
            "surfaceContainerHigh" to colorScheme.surfaceContainerHigh,
            "surfaceContainerHighest" to colorScheme.surfaceContainerHighest,
            "surfaceContainerLow" to colorScheme.surfaceContainerLow,
            "inverseOnSurface" to colorScheme.inverseOnSurface,
            "inverseSurface" to colorScheme.inverseSurface,
            "inversePrimary" to colorScheme.inversePrimary,
            "surface" to colorScheme.surface,
            "onSurface" to colorScheme.onSurface,
            "surfaceVariant" to colorScheme.surfaceVariant,
            "onSurfaceVariant" to colorScheme.onSurfaceVariant,
            "tertiary" to colorScheme.tertiary,
            "onTertiary" to colorScheme.onTertiary,
            "tertiaryContainer" to colorScheme.tertiaryContainer,
            "onTertiaryContainer" to colorScheme.onTertiaryContainer,
            "error" to colorScheme.error,
            "onError" to colorScheme.onError,
            "errorContainer" to colorScheme.errorContainer,
            "onErrorContainer" to colorScheme.onErrorContainer,
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState()))
        {
            for ((name, color) in colors) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color, shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Log.i("COLOR", "$name = Color(0x${color.value.toHexString(HexFormat.UpperCase)})")
                }
            }
        }
    }
}


@Composable
fun OutlinedSwitch(
    label: String,
    helpText: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    fontSize: TextUnit = 12.sp,
    enabled: Boolean = true,
    helpSubPath: String? = null,
    embeddedContent: (@Composable () -> Unit)? = null
) {
    OutlinedBox(label = label, modifier = modifier, background = background, enabled = enabled, helpSubPath = helpSubPath) {
        Column(modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterStart)) {
            Row {
                Text(
                    text = helpText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.2f),
                    style = TextStyle(fontSize = fontSize),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f)
                        .padding(start = 4.dp, end = 8.dp),
                )
                Switch(
                    enabled = enabled,
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                )
            }
            if (embeddedContent != null && isChecked)
                embeddedContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Enum<T>> OutlinedEnumDropDown(
    enumClass: KClass<T>,
    getDisplayName: (T) -> String,
    selectedEnum: T,
    onSelectionChanged: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    fontSize: TextUnit = 18.sp,
    enabled: Boolean= true,
    helpSubPath: String? = null,
) {
    var expandedState by remember { mutableStateOf(false) }
    val expanded = if(enabled) expandedState else false
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if(enabled) expandedState = !expandedState
        },
        modifier = modifier
    ) {
        OutlinedBox(
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            background = background,
            enabled = enabled,
            helpSubPath = helpSubPath
        ) {
            Row(modifier = Modifier.align(Alignment.Center)) {
                CompositionLocalProvider(LocalContentColor provides textColor) {
                    Text(
                        text = getDisplayName(selectedEnum),
                        textAlign = TextAlign.End,
                        fontSize = fontSize,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(top = 8.dp, bottom = 8.dp, end = 4.dp)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expandedState = false }) {
                enumClass.java.enumConstants?.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(getDisplayName(option)) },
                        onClick = {
                            expandedState = false // Close the menu
                            onSelectionChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OutlinedListDropDown(
    items: List<T>,
    getDisplayName: (T) -> String,
    selectedItem: T,
    onSelectionChanged: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    fontSize: TextUnit = 18.sp,
    enabled: Boolean = true,
    helpSubPath: String? = null,
) {
    var expandedState by remember { mutableStateOf(false) }
    val expanded = if (enabled) expandedState else false
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (enabled) expandedState = !expandedState
        },
        modifier = modifier
    ) {
        OutlinedBox(
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            background = background,
            enabled = enabled,
            helpSubPath = helpSubPath
        ) {
            Row(modifier = Modifier.align(Alignment.Center)) {
                CompositionLocalProvider(LocalContentColor provides textColor) {
                    Text(
                        text = getDisplayName(selectedItem),
                        textAlign = TextAlign.End,
                        fontSize = fontSize,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(top = 8.dp, bottom = 8.dp, end = 4.dp)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expandedState = false }
            ) {
                items.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(getDisplayName(option)) },
                        onClick = {
                            expandedState = false
                            onSelectionChanged(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OutlinedLongTextField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    checkCondition: (Long) -> Pair<Boolean, String?> = { true to null },
    digits: Int? = null,
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    helpSubPath: String? = null,
) {
    // helpers
    fun String.toLongOrNullFlexible(): Long? =
        if (this == "-" || this.isBlank()) null else toLongOrNull()

    fun Long.format(): String {
        if (digits != null) {
            val absValue = kotlin.math.abs(this)
            val padded = absValue.toString().padStart(digits, '0')
            return if (this < 0) "-$padded" else padded
        } else
            return toString()
    }

    var text by remember { mutableStateOf(value.format()) }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun commitOrRevert() {
        if (text.isEmpty()) {
            onValueChange(0)
        } else if (isError) {
            text = value.format()
            isError = false
            errorMsg = null
        } else {
            // force formatting even if already valid
            text = value.format()
        }
    }

    // Sync external value ONLY when not editing
    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            text = value.format()
            val (valid, msg) = checkCondition(value)
            isError = !valid
            errorMsg = msg
        }
    }

    OutlinedBox(
        label = label,
        helpSubPath = helpSubPath,
        enabled = enabled,
        background = background,
        modifier = modifier
    ) {
        TextField(
            value = text,
            onValueChange = { newText ->
                text = newText

                val parsed = newText.toLongOrNullFlexible()

                if (parsed == null) {
                    isError = newText.isNotBlank()
                    errorMsg = if (isError) "Invalid number" else null
                    return@TextField
                }

                val (valid, msg) = checkCondition(parsed)
                isError = !valid
                errorMsg = msg

                if (valid) {
                    onValueChange(parsed)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commitOrRevert()
                    focusManager.clearFocus()
                }
            ),
            enabled = enabled,
            isError = isError,
            supportingText = errorMsg?.let { { Text(it) } },
            colors = TextFieldDefaults.colors(
                errorTextColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                //focusedContainerColor = background,
                //unfocusedContainerColor = background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        commitOrRevert()
                    }
                }
        )
    }
}

@Composable
fun OutlinedDoubleTextField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    checkCondition: (Double) -> Pair<Boolean, String?> = { true to null },
    decimalPlaces: Int = 2,
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    helpSubPath: String? = null,
) {
    // helpers
    fun String.toDoubleOrNullFlexible(): Double? = replace(',', '.').toDoubleOrNull()
    fun Double.format(): String = "%.${decimalPlaces}f".format(this)

    var text by remember { mutableStateOf(value.format()) }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun commitOrRevert() {
        if (text.isEmpty()) {
            onValueChange(0.0)
        } else if (isError) {
            text = value.format()
            isError = false
            errorMsg = null
        } else {
            // force formatting even if already valid
            text = value.format()
        }
    }

    // Sync external value ONLY when not editing
    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            text = value.format()
            val (valid, msg) = checkCondition(value)
            isError = !valid
            errorMsg = msg
        }
    }

    OutlinedBox(
        label = label,
        helpSubPath = helpSubPath,
        enabled = enabled,
        background = background,
        modifier = modifier
    ) {
        TextField(
            value = text,
            onValueChange = { newText ->
                text = newText

                val parsed = newText.toDoubleOrNullFlexible()

                if (parsed == null) {
                    isError = newText.isNotBlank()
                    errorMsg = if (isError) "Invalid number" else null
                    return@TextField
                }

                val (valid, msg) = checkCondition(parsed)
                isError = !valid
                errorMsg = msg

                if (valid) {
                    onValueChange(parsed)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commitOrRevert()
                    focusManager.clearFocus()
                }
            ),
            enabled = enabled,
            isError = isError,
            supportingText = errorMsg?.let { { Text(it) } },
            colors = TextFieldDefaults.colors(
                errorTextColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = background,
                unfocusedContainerColor = background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        commitOrRevert()
                    }
                }
        )
    }
}

@Composable
fun OutlinedConditionTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    checkCondition: (String) -> Pair<Boolean, String?> = { true to null },
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
    helpSubPath: String? = null,
) {
    var text by remember { mutableStateOf(value) }
    var isError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isFocused by remember { mutableStateOf(false) }

    // Sync external value ONLY when not editing
    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            text = value
            val (valid, msg) = checkCondition(value)
            isError = !valid
            errorMsg = msg
        }
    }

    OutlinedBox(
        label = label,
        helpSubPath = helpSubPath,
        enabled = enabled,
        background = background,
        modifier = modifier
    ) {
        TextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                val (valid, msg) = checkCondition(newText)
                isError = !valid
                errorMsg = msg
                if (valid) {
                    onValueChange(newText)
                }
            },
            enabled = enabled,
            isError = isError,
            supportingText = errorMsg?.let { { Text(it) } },
            colors = TextFieldDefaults.colors(
                errorTextColor = MaterialTheme.colorScheme.error,
                focusedContainerColor = background,
                unfocusedContainerColor = background,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    // Revert if invalid
                    if (!focusState.isFocused && isError) {
                        text = value
                        isError = false
                        errorMsg = null
                    }
                }
        )
    }
}

// LazyColumnWithScrollAwayTopElement

@Stable
class ScrollAwayHeaderState(
    val listState: LazyListState,
    initialHeaderOffset: Float
) {
    var headerOffset by mutableFloatStateOf(initialHeaderOffset)
    var headerHeightPx by mutableFloatStateOf(0f)
}

@Composable
fun rememberScrollAwayHeaderState(
    initialHeaderOffset: Float = 0f
): ScrollAwayHeaderState {
    return rememberSaveable(
        saver = Saver(
            save = {
                listOf(
                    it.listState.firstVisibleItemIndex,
                    it.listState.firstVisibleItemScrollOffset,
                    it.headerOffset,
                    it.headerHeightPx
                )
            },
            restore = {
                val listState = LazyListState(
                    firstVisibleItemIndex = it[0] as Int,
                    firstVisibleItemScrollOffset = it[1] as Int
                )
                ScrollAwayHeaderState(
                    listState,
                    initialHeaderOffset = it[2] as Float
                ).apply {
                    headerHeightPx = it[3] as Float
                }
            }
        )
    ) {
        ScrollAwayHeaderState(
            listState = LazyListState(),
            initialHeaderOffset = initialHeaderOffset
        )
    }
}


@Composable
fun LazyColumnWithScrollAwayHeaderElement(
    state: ScrollAwayHeaderState,
    modifier: Modifier = Modifier,
    lazyColumnContentPaddingValues: PaddingValues = PaddingValues(),
    headerElement: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    val listState = state.listState
    val animatableOffset = remember { Animatable(state.headerOffset) }

    // Keep animatable in sync during drag
    LaunchedEffect(state.headerOffset) {
        if (listState.isScrollInProgress) {
            animatableOffset.snapTo(state.headerOffset)
        }
    }

    // Snap to closest position after scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && state.headerHeightPx > 0f) {
            val halfHeight = -state.headerHeightPx / 2f
            val target = if (state.headerOffset < halfHeight) -state.headerHeightPx else 0f
            animatableOffset.animateTo(target)
            state.headerOffset = animatableOffset.value
        }
    }

    val scrollBehavior = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (state.headerHeightPx != 0f) {
                    val delta = available.y
                    state.headerOffset = (state.headerOffset + delta).coerceIn(-state.headerHeightPx, 0f)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior)
    ) {
        // List "under" top content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = with(LocalDensity.current) { (state.headerHeightPx + animatableOffset.value.roundToInt()).coerceAtLeast(0f).toDp() }),
            contentPadding = lazyColumnContentPaddingValues,
            content = content
        )

        // Sliding top element
        Box(
            Modifier
                .onSizeChanged { state.headerHeightPx = it.height.toFloat() }
                .offset { IntOffset(0, animatableOffset.value.roundToInt()) }
        ) {
            headerElement()
        }
    }
}

@Preview
@Composable
fun donationDialogPreview() {
    RFAnalyzerTheme {
        FossDonationDialog(
            usageTimeStr = "1 hour and 3 minutes",
            dismissAction = {}
        )
    }
}

@Composable
fun FossDonationOptionDialogContent(
    usageTimeStr: String,
    postDonationAction: () -> Unit,
    modifier: Modifier = Modifier,
    scrollableText: Boolean = false
) {
    val context = LocalContext.current
    val columnWrapper: @Composable ColumnScope.(@Composable ColumnScope.() -> Unit) -> Unit = if (scrollableText) {
        { content -> ScrollableColumnWithFadingEdge(modifier = Modifier.weight(1f, fill = false), content = content) }
    } else {
        { content -> Column(content = content) }
    }
    Column(modifier = modifier) {
        columnWrapper {
            Text("You've used RF Analyzer for $usageTimeStr - thank you! Every minute of that has been powered by late nights, debugging, and a lot of care. \uD83D\uDC9B",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Text("This app is open-source so anyone can explore SDR without barriers and let the community benefit from a Google-free, privacy-respecting version.",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Text("If you enjoy using it, please support the project - your donation helps keeping it alive and accessible to everyone. \uD83D\uDE4F",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Text("73, Dennis Mantz (DM4NTZ)",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp)) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://ko-fi.com/rfanalyzer".toUri())
                    context.startActivity(intent)
                    postDonationAction()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202020)),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .requiredHeight(40.dp)
                    .padding(horizontal = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kofi),
                    contentDescription = "Ko-fi Logo",
                    modifier = Modifier.size(70.dp)
                )
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://liberapay.com/DM4NTZ/donate".toUri())
                    context.startActivity(intent)
                    postDonationAction()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF6C915)),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1.3f)
                    .requiredHeight(40.dp)
                    .padding(horizontal = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.liberapay),
                    contentDescription = "Liberapay Logo",
                    modifier = Modifier.size(85.dp)
                )
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://paypal.me/dennismantz".toUri())
                    context.startActivity(intent)
                    postDonationAction()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .requiredHeight(40.dp)
                    .padding(horizontal = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.paypal),
                    contentDescription = "PayPal Logo",
                    modifier = Modifier.size(78.dp)
                )
            }
        }
    }
}

@Composable
fun FossDonationDialog(
    usageTimeStr: String,
    dismissAction: () -> Unit,
) {
    Dialog(
        onDismissRequest = dismissAction,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        var donationButtonClicked by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .padding(10.dp)
                .background(MaterialTheme.colorScheme.background)
                .border(2.dp, MaterialTheme.colorScheme.tertiary, shape = MaterialTheme.shapes.medium) // Border
        ) {
            IconButton(
                onClick = dismissAction,
                modifier = Modifier
                    .padding(16.dp)
                    .size(25.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                Text(
                    "Support RF Analyzer",
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(all = 10.dp)
                        .align(Alignment.CenterHorizontally)
                )
                FossDonationOptionDialogContent(
                    usageTimeStr = usageTimeStr,
                    postDonationAction = { donationButtonClicked = true },
                    modifier = Modifier.weight(1f, fill = false),  // this shrinks the content so that the thank you button has space
                    scrollableText = true
                )
                if (donationButtonClicked) {
                    Button(
                        onClick = dismissAction,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Thank you! \uD83D\uDC9B  (click to dismiss)")
                    }
                }
            }
        }
    }
}

@Composable
fun rememberCreateFilePicker(
    suggestedFileName: String,
    mimeType: String = "*/*",
    onFileCreated: (Uri) -> Unit,
    onAbort: () -> Unit = {}
): () -> Unit {
    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        onResult = { uri -> if (uri != null) onFileCreated(uri) else onAbort() }
    )
    return { createFileLauncher.launch(suggestedFileName) } // Return a lambda for triggering it
}

@Composable
fun rememberOpenFilePicker(
    mimeTypes: Array<String> = arrayOf("*/*"),
    onFileChosen: (uri: Uri, filename: String?) -> Unit,
    onAbort: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    Log.d("HelperComposables", "rememberOpenFilePicker: URI has no persistable read grant: $uri")
                } catch (e: IllegalArgumentException) {
                    Log.d("HelperComposables", "rememberOpenFilePicker: URI provider does not support persistable grants: $uri")
                }
                val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
                onFileChosen(uri, displayName)
            } else {
                onAbort()
            }
        }
    )
    return { openFileLauncher.launch(mimeTypes) } // Return a lambda for triggering it
}

@Composable
fun EditStringDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseEditSheet(
    title: String,
    hasUnsavedChanges: (() -> Boolean)?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    disableSaveButton: Boolean = false,
    overwriteSaveTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var showSheet by remember { mutableStateOf(true) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    fun onDissmissRequested() {
        if (hasUnsavedChanges != null && hasUnsavedChanges()) {
            showSheet = false
            showUnsavedDialog = true
        }
        else {
            showSheet = false
            onDismiss()
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = ::onDissmissRequested) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = ::onDissmissRequested) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                    )
                    Button(onClick = {
                            onSave()
                            onDismiss()
                        },
                        enabled = if (disableSaveButton) false else if (hasUnsavedChanges != null) hasUnsavedChanges() else true
                    ) { Text(overwriteSaveTitle ?: "Save") }
                }
                content()
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnsavedDialog = false
                showSheet = true
            },
            title = { Text("Unsaved Changes") },
            text = { Text("Discard your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    showSheet = false
                    onDismiss()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    showSheet = true
                }) { Text("Cancel") }
            }
        )
    }
}

@Preview
@Composable
fun PreviewCountryPicker() {
    RFAnalyzerTheme {
        CountryPicker(selectedCountryCode = "US", onCountryCodeSelected = {})
    }
}

@Composable
fun CountryPicker(
    selectedCountryCode: String?,
    onCountryCodeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val buttonText = if(selectedCountryCode==null || selectedCountryCode.isEmpty())
            "<select country>"
        else
            "${countryCodeToFlagEmoji(selectedCountryCode)} ${COUNTRY_CODE_TO_NAME[selectedCountryCode]} ($selectedCountryCode)"

    Row(modifier = modifier) {
        Button(onClick = { dialogOpen = true }) {
            Row {
                Text(buttonText)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        if (selectedCountryCode != null && selectedCountryCode.isNotEmpty()) {
            IconButton(onClick = { onCountryCodeSelected("") }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
        }
    }

    if (dialogOpen) {
        CountryPickerDialog(
            onDismiss = { dialogOpen = false },
            onSelect = {
                onCountryCodeSelected(it)
                dialogOpen = false
            }
        )
    }
}

@Composable
fun CountryPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = COUNTRY_CODE_TO_NAME.keys.filter {
        val display = "${COUNTRY_CODE_TO_NAME[it]} ($it)"
        display.contains(query, ignoreCase = true)
    }

    Log.d("CountryPicker", "show Dialog!")
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxSize(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Country") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered) { code ->
                        val display = "${countryCodeToFlagEmoji(code)} ${COUNTRY_CODE_TO_NAME[code]} ($code)"
                        ListItem(
                            headlineContent = { Text(display) },
                            modifier = Modifier
                                .clickable { onSelect(code) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ColorPickerRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val presetColors = listOf(
        Color(0xFFEF5350), // red
        Color(0xFFAB47BC), // purple
        Color(0xFF5C6BC0), // indigo
        Color(0xFF29B6F6), // light blue
        Color(0xFF26A69A), // teal
        Color(0xFF66BB6A), // green
        Color(0xFFFFCA28), // amber
        Color(0xFFFFA726), // orange
        Color(0xFFAF4706), // orange
        Color(0xFF8D6E63), // brown
        Color(0xFF9E9E9E), // gray
        Color(0xFF000000), // black
        Color(0xFFFFFFFF), // white
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Color",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presetColors.forEach { color ->
                val isSelected = color == selectedColor
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp)
    )
}


@Composable
fun SettingsActionRow(
    title: String,
    description: String? = null,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsInfoRow(
    text: AnnotatedString,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            text = text,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



fun countryCodeToFlagEmoji(code: String): String {
    if (code.length != 2) return ""
    return code.uppercase(Locale.US).map { char ->
        Character.toChars(Character.codePointAt(char.toString(), 0) - 'A'.code + 0x1F1E6)
    }.joinToString("") {
        String(it)
    }
}
