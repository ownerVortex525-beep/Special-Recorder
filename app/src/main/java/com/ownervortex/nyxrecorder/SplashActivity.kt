package com.ownervortex.nyxrecorder

import android.content.Intent
import android.os.Bundle

/**
 * Branded launch screen: the icon artwork, app name and "Build by CYBER-FORCE"
 * subtitle are baked into [R.drawable.splash_background] via the splash theme
 * declared in the manifest. Held briefly so Compose is ready, then launches
 * [MainActivity].
 */
class SplashActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        // Block touches until we transition, so the hold isn't skipped.
        window.decorView.isClickable = true
        window.decorView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }
}
