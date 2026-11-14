package com.ntplayone.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = (application as NtApp).model
        setContent {
            AppBackground {
                when (val s = app.screen) {
                    Screen.Setup -> SetupScreen(app)
                    Screen.Home -> HomeScreen(app)
                    Screen.Lists -> ListsScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.XtreamForm -> XtreamFormScreen(app)
                    Screen.M3uForm -> M3uFormScreen(app)
                    is Screen.Browse -> BrowseScreen(app, s.type)
                    is Screen.Detail -> DetailScreen(app, s.item)
                    is Screen.Player -> PlayerScreen(app, s)
                }
                if (app.importStatus.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(18.dp)
                            .background(Glass, RoundedCornerShape(12.dp))
                            .border(1.dp, Brush.horizontalGradient(listOf(Cyan, Violet)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(app.importStatus, color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (app.message.isNotEmpty()) {
                    LaunchedEffect(app.message) {
                        delay(4000)
                        app.message = ""
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(24.dp)
                            .background(Glass, RoundedCornerShape(14.dp))
                            .border(1.dp, Brush.horizontalGradient(listOf(Cyan, Violet, Pink)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Text(app.message, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (app.pinAction != null) AdultPinDialog(app)
            }
            BackHandler(enabled = app.pinAction != null) { app.closePin() }
            // O player controla as próprias camadas de voltar.
            BackHandler(
                enabled = app.screen != Screen.Home &&
                    app.screen != Screen.Setup &&
                    app.screen !is Screen.Player,
            ) { app.back() }
        }
    }
}