package com.ntplayone.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
private fun PinPad(onDigit: (String) -> Unit, onErase: () -> Unit, firstFocus: FocusRequester) {
    val key: @Composable (String, () -> Unit, FocusRequester?) -> Unit = { label, action, focus ->
        FocusBox(
            action,
            Modifier.size(72.dp, 54.dp),
            shape = RoundedCornerShape(12.dp),
            bg = SolidColor(CardBg2),
            focusRequester = focus,
        ) {
            Text(label, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in row) key(d, { onDigit(d) }, if (d == "1") firstFocus else null)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusBox(onErase, Modifier.size(72.dp, 54.dp), shape = RoundedCornerShape(12.dp), bg = SolidColor(Glass)) {
                Text("Apagar", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            key("0", { onDigit("0") }, null)
        }
    }
}

@Composable
private fun PinCard(
    title: String,
    hint: String,
    entered: String,
    error: String,
    onDigit: (String) -> Unit,
    onErase: () -> Unit,
    firstFocus: FocusRequester,
    footer: String,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            Modifier.width(320.dp)
                .background(CardBg2, RoundedCornerShape(24.dp))
                .border(1.dp, Brush.horizontalGradient(listOf(Cyan, Violet, Pink)), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(hint, color = TextLo, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Text(
                "●".repeat(entered.length) + "○".repeat((4 - entered.length).coerceAtLeast(0)),
                color = Cyan, fontSize = 22.sp, letterSpacing = 6.sp,
            )
            Text(
                if (error.isEmpty()) " " else error,
                color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            PinPad(onDigit, onErase, firstFocus)
            Spacer(Modifier.height(12.dp))
            Text(footer, color = TextLo, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AdultPinDialog(app: AppModel) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    PinCard(
        title = "Conteúdo adulto",
        hint = "Digite a senha de 4 dígitos para liberar",
        entered = entered,
        error = error,
        onDigit = { d ->
            error = ""
            if (entered.length < 4) entered += d
            if (entered.length == 4) {
                if (app.tryUnlockAdult(entered)) {
                    val action = app.pinAction
                    app.closePin()
                    action?.invoke()
                } else {
                    error = "Senha incorreta"
                    entered = ""
                }
            }
        },
        onErase = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
        firstFocus = firstFocus,
        footer = "Senha padrão: 1234 · altere em Listas",
    )
}

@Composable
fun ChangePinDialog(app: AppModel, onClose: () -> Unit) {
    var phase by remember { mutableStateOf(0) } // 0 = senha atual, 1 = nova, 2 = confirmar
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(phase) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }
    val title = when (phase) {
        0 -> "Senha atual"
        1 -> "Nova senha"
        else -> "Confirme a nova senha"
    }
    PinCard(
        title = title,
        hint = "Senha de 4 dígitos dos canais adultos",
        entered = entered,
        error = error,
        onDigit = { d ->
            error = ""
            if (entered.length < 4) entered += d
            if (entered.length == 4) {
                when (phase) {
                    0 -> if (app.tryUnlockAdult(entered)) phase = 1 else error = "Senha atual incorreta"
                    1 -> {
                        if (entered.all { it.isDigit() }) {
                            first = entered
                            phase = 2
                        } else error = "Use apenas números"
                    }
                    else -> {
                        if (entered == first) {
                            app.changeAdultPin(first)
                            app.message = "Senha dos canais adultos atualizada."
                            onClose()
                        } else {
                            error = "As senhas não conferem"
                            first = ""
                            phase = 1
                        }
                    }
                }
                entered = ""
            }
        },
        onErase = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
        firstFocus = firstFocus,
        footer = "Canais e categorias adultas ficam no fim da lista",
    )
}
