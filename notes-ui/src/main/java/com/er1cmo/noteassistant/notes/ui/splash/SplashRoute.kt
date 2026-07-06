package com.er1cmo.noteassistant.notes.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SLOGAN = "为记录而生，也为效率而来"

@Composable
fun SplashRoute(onSplashFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(850)
        onSplashFinished()
    }

    Surface(color = Color(0xFFF5F6FA), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF5B6CFF), Color(0xFF8DC7FF)),
                        ),
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("泓", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "小泓便签",
                modifier = Modifier.padding(top = 22.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
            )
            Text(
                text = SLOGAN,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF697386),
            )
        }
    }
}
