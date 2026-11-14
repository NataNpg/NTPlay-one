package com.ntplayone.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

val WhatsAppNumber = "(42) 99804-0495"

val WhatsAppUrl = "https://wa.me/5542998040495?text=" +
    URLEncoder.encode("Olá! Quero solicitar minha lista do NTPlayone.", "UTF-8")

@Composable
fun SetupScreen(app: AppModel) {
    val waUrl = WhatsAppUrl
    val startFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { startFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    "NTPlayone",
                    style = TextStyle(brush = Accent, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                )
                Text("SEU PLAYER IPTV PARA TV", color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            QrCard(
                "Adicionar lista pelo celular",
                "Escaneie com a câmera",
                "Celular no mesmo Wi-Fi da TV · " + app.pairUrl,
                app.pairUrl,
            )
            QrCard(
                "Peça sua lista",
                WhatsAppNumber,
                "WhatsApp · teste do aplicativo ou da lista",
                waUrl,
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NpButton("Entrar com Xtream", { app.navigate(Screen.XtreamForm) }, focusRequester = startFocus)
            NpButton("Adicionar link M3U", { app.navigate(Screen.M3uForm) }, primary = false)
        }
        if (app.loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Cyan)
        }
    }
}

@Composable
fun Field(value: (String) -> Unit, label: String, current: String, password: Boolean = false, focusRequester: FocusRequester? = null) {
    TextField(
        value = current,
        onValueChange = value,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardBg2,
            unfocusedContainerColor = CardBg2,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
            cursorColor = Cyan,
            focusedIndicatorColor = Cyan,
            unfocusedIndicatorColor = Line,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = TextLo,
        ),
        modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
    )
}

@Composable
fun XtreamFormScreen(app: AppModel) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Entrar com Xtream Codes", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Use os dados enviados pelo seu provedor de lista.", color = TextLo, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field({ name = it }, "Nome da lista", name, focusRequester = firstFocus)
            Field({ server = it }, "Servidor (http://servidor.com:8080)", server)
            Field({ user = it }, "Usuário", user)
            Field({ pass = it }, "Senha", pass, password = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NpButton(if (busy) "Conectando..." else "Conectar", {
                    if (!busy) {
                        busy = true
                        error = ""
                        app.scope.launch {
                            val err = app.addXtream(name, server, user, pass)
                            app.main.post {
                                busy = false
                                if (err != null) error = err
                            }
                        }
                    }
                })
                NpButton("Voltar", { app.back() }, primary = false)
            }
            if (error.isNotEmpty()) Text(error, color = Color(0xFFF87171), fontSize = 14.sp)
        }
    }
}

@Composable
fun M3uFormScreen(app: AppModel) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Adicionar link M3U", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field({ name = it }, "Nome da lista", name, focusRequester = firstFocus)
            Field({ url = it }, "Link da lista (M3U / get.php)", url)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NpButton(if (busy) "Baixando..." else "Adicionar", {
                    if (!busy) {
                        busy = true
                        error = ""
                        app.scope.launch {
                            val err = app.addM3u(name, url)
                            app.main.post {
                                busy = false
                                if (err != null) error = err
                            }
                        }
                    }
                })
                NpButton("Voltar", { app.back() }, primary = false)
            }
            if (error.isNotEmpty()) Text(error, color = Color(0xFFF87171), fontSize = 14.sp)
        }
    }
}

@Composable
fun ListsScreen(app: AppModel) {
    var confirm by remember { mutableStateOf("") }
    var pinOpen by remember { mutableStateOf(false) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { backFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NpButton("Voltar", { app.back() }, primary = false, focusRequester = backFocus)
            Spacer(Modifier.width(16.dp))
            Text("Listas", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            NpButton("QR code / celular", { app.navigate(Screen.Setup) })
            Spacer(Modifier.width(12.dp))
            NpButton("Xtream", { app.navigate(Screen.XtreamForm) })
            Spacer(Modifier.width(12.dp))
            NpButton("M3U", { app.navigate(Screen.M3uForm) }, primary = false)
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp))
                    .border(1.dp, Line, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Canais adultos", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Vão para o fim das listas e pedem senha · PIN atual: " +
                            "●".repeat(app.adultPin.length) + " (padrão 1234)",
                        color = TextLo, fontSize = 13.sp,
                    )
                }
                NpButton("Alterar senha", { pinOpen = true }, primary = false)
            }
            if (app.playlists.isEmpty()) {
                Text("Nenhuma lista adicionada ainda.", color = TextLo, fontSize = 16.sp)
            }
            for (p in app.playlists) {
                Row(
                    Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp))
                        .border(1.dp, if (p.id == app.activeId) Cyan else Line, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (p.kind == "xtream") "Xtream Codes" else "Link M3U") +
                                (if (p.expires.isNotEmpty()) " · vence em ${p.expires}" else "") +
                                (if (p.id == app.activeId) " · ativa" else ""),
                            color = TextLo, fontSize = 13.sp,
                        )
                    }
                    NpButton("Abrir", { app.setActive(p.id) })
                    Spacer(Modifier.width(8.dp))
                    NpButton("Atualizar", {
                        app.scope.launch {
                            val err = app.refresh(p)
                            if (err != null) app.main.post { app.message = err }
                        }
                    }, primary = false)
                    Spacer(Modifier.width(8.dp))
                    NpButton(if (confirm == p.id) "Confirmar?" else "Remover", {
                        if (confirm == p.id) {
                            app.removePlaylist(p)
                            confirm = ""
                        } else confirm = p.id
                    }, primary = false)
                }
            }
        }
    }
    if (pinOpen) ChangePinDialog(app) { pinOpen = false }
}