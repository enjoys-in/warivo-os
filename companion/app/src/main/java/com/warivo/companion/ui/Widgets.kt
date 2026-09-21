package com.warivo.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warivo.companion.ui.theme.AccentBrush
import com.warivo.companion.ui.theme.CardBrush
import com.warivo.companion.ui.theme.CardPadding
import com.warivo.companion.ui.theme.CardRadius
import com.warivo.companion.ui.theme.WarivoAccent
import com.warivo.companion.ui.theme.WarivoBlack
import com.warivo.companion.ui.theme.WarivoHairline
import com.warivo.companion.ui.theme.WarivoText
import com.warivo.companion.ui.theme.WarivoTextDim

@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun OwnerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CardRadius))
            .background(CardBrush, RoundedCornerShape(CardRadius))
            .border(1.dp, WarivoHairline, RoundedCornerShape(CardRadius))
            .padding(CardPadding),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = WarivoTextDim, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
    )
}

@Composable
fun Badge(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** A labelled field with an explicit Save, so a half-typed URL is never used. */
@Composable
fun OwnerField(
    label: String,
    value: String,
    placeholder: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Label(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text(placeholder, color = WarivoTextDim, fontSize = 15.sp)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = TextStyle(color = WarivoText, fontSize = 16.sp),
                    cursorBrush = SolidColor(WarivoAccent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draft != value) {
                PrimaryButton("Save", onClick = onSave)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) AccentBrush else CardBrush, RoundedCornerShape(50))
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (enabled) WarivoBlack else WarivoTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun OutlineButton(
    text: String,
    icon: ImageVector? = null,
    tint: Color = WarivoText,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, WarivoHairline, RoundedCornerShape(50))
            .tap(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Text(text, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun KeyValueRow(key: String, value: String, valueColor: Color = WarivoText) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = WarivoTextDim, fontSize = 15.sp)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
