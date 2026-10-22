package com.callersphere.app

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var numberInput: EditText
    private lateinit var resultView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(0xFFF6F7FB.toInt())
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        val brand = TextView(this).apply {
            text = "CallerSphere"
            textSize = 30f
            setTextColor(0xFF172554.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(brand)

        content.addView(TextView(this).apply {
            text = "Know who might be calling"
            textSize = 16f
            setTextColor(0xFF475569.toInt())
            setPadding(0, dp(6), 0, dp(22))
        })

        content.addView(TextView(this).apply {
            text = "Search a phone number"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF111827.toInt())
        })

        numberInput = EditText(this).apply {
            hint = "+92 300 1234567"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            singleLine = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        content.addView(numberInput, matchWrap())

        content.addView(makeButton("Look up sample number") {
            lookupSample(numberInput.text.toString())
        }, matchWrap(top = 10))

        resultView = TextView(this).apply {
            text = "Try the demo number +1 202 555 0147. This prototype uses local sample data only."
            textSize = 15f
            setTextColor(0xFF334155.toInt())
            setPadding(dp(14), dp(16), dp(14), dp(16))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(resultView, matchWrap(top = 16))

        content.addView(TextView(this).apply {
            text = "Incoming call features"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF172554.toInt())
            setPadding(0, dp(28), 0, dp(8))
        })

        content.addView(TextView(this).apply {
            text = "Android can let you choose an app for call screening. This early build includes the service foundation; it does not yet display a custom caller name over the system incoming-call screen."
            textSize = 14f
            setTextColor(0xFF475569.toInt())
        })

        content.addView(makeButton("Set as call-screening app") {
            requestCallScreeningRole()
        }, matchWrap(top = 12))

        content.addView(TextView(this).apply {
            text = "Roadmap: secure global database • spam reports • privacy controls • AdMob"
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(8))
        }, matchWrap(top = 10))

        outer.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(outer)
    }

    private fun lookupSample(raw: String) {
        val number = raw.trim().filter { it.isDigit() || it == '+' }
        if (number.isBlank()) {
            resultView.text = "Enter a phone number first."
            return
        }

        val digits = number.filter(Char::isDigit)
        val demoDigits = "12025550147"
        resultView.text = if (digits.endsWith(demoDigits) || digits == demoDigits) {
            "DEMO MATCH\nExample Service Desk\nSample record only — not a real verified identity."
        } else {
            "No sample record found for $number. Global lookup is not connected yet."
        }
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Call screening is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "CallerSphere already has the call-screening role.", Toast.LENGTH_LONG).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_CALL_ROLE)
    }

    @Deprecated("Android role request callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CALL_ROLE) {
            val message = if (resultCode == RESULT_OK) {
                "Call-screening role granted."
            } else {
                "Call-screening role not granted. You can try again later."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        if (top > 0) topMargin = dp(top)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CALL_ROLE = 4001
    }
}
