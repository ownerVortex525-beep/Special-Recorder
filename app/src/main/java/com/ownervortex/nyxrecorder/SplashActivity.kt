package com.ownervortex.nyxrecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Branded launch screen: rounded-corner app photo, the "Build by CYBER-FORCE"
 * watermark just below it, then rotating taglines that fade in/out until the
 * main app opens (~3s).
 */
class SplashActivity : ComponentActivity() {

    private val openMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        // Block touches until we transition, so the hold isn't skipped.
        window.decorView.isClickable = true
        setContent { SplashContent() }
        window.decorView.postDelayed(openMain, HOLD_MS)
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(openMain)
        super.onDestroy()
    }

    companion object {
        private const val HOLD_MS = 3000L
    }
}

private val TAGLINES = listOf(
    "Premium Build",
    "Record Everything",
    "Edit Like A Pro",
    "NYX Recorder"
)

@Composable
private fun SplashContent() {
    var index by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in TAGLINES.indices) {
            index = i
            visible = true
            delay(560)
            visible = false
            delay(190)
        }
    }

    val taglineAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(240),
        label = "tagline"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F12)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(26.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Build by CYBER-FORCE",
            color = Color(0xFF6C63FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "NYX-RECORDER",
            color = Color(0xFFF2F2F7),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(30.dp))
        // Fixed-height slot so taglines fade in place without layout jumps.
        Box(
            Modifier
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                TAGLINES[index],
                color = Color(0xFF9A9CB0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }
    }
}
