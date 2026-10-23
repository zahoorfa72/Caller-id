package com.callersphere.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

private data class PhoneContact(
    val name: String,
    val number: String,
    val isStarred: Boolean
)

internal data class CallerLookupResult(
    val found: Boolean,
    val label: String = "",
    val category: String = "",
    val message: String = "",
    val configured: Boolean = true
)

class MainActivity : Activity() {
    private enum class Page { HOME, CONTACTS, DIALER, LOOKUP, MORE }

    private var page = Page.HOME
    private var contacts: List<PhoneContact> = emptyList()
    private var contactsLoading = false
    private var lookupNumber = ""
    private var dialNumber = ""
    private var lastLookupNumber = ""
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var pageContent: LinearLayout
    private lateinit var lookupResultView: TextView
    private var contactFilter = ""
    private var navigationView: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 77, 184)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF4F7FC.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(0xFF165DCE.toInt())
        }
        val mark = TextView(this).apply {
            text = "C"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF0C469F.toInt(), 100)
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(label("CallerSphere", 22f, Color.WHITE, true))
        brand.addView(label("Caller ID • Contacts • Spam safety", 12f, 0xFFE1ECFF.toInt()))
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
        }
        scroll.addView(pageContent)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(buildNavigation())
        setContentView(root)
        renderPage()
    }

    private fun buildNavigation(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(4), dp(6), dp(4), dp(6))
            elevation = dp(8).toFloat()
        }
        val items = listOf(
            Page.HOME to "Home",
            Page.CONTACTS to "People",
            Page.DIALER to "Dial",
            Page.LOOKUP to "Lookup",
            Page.MORE to "More"
        )
        items.forEach { item ->
            val selected = item.first == page
            val button = TextView(this).apply {
                text = item.second
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (selected) 0xFF165DCE.toInt() else 0xFF64748B.toInt())
                setPadding(dp(2), dp(10), dp(2), dp(10))
                setOnClickListener {
                    page = item.first
                    renderPage()
                    refreshNavigation()
                }
            }
            nav.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        navigationView = nav
        return nav
    }

    private fun refreshNavigation() {
        navigationView?.let { old ->
            val parent = old.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(old)
            parent.removeViewAt(index)
            parent.addView(buildNavigation(), index)
        }
    }

    private fun renderPage() {
        pageContent.removeAllViews()
        when (page) {
            Page.HOME -> renderHome()
            Page.CONTACTS -> renderContacts()
            Page.DIALER -> renderDialer()
            Page.LOOKUP -> renderLookup()
            Page.MORE -> renderMore()
        }
    }

    private fun renderHome() {
        val hero = card()
        hero.setBackground(rounded(0xFF165DCE.toInt(), 22))
        hero.addView(label("Know who may be calling", 23f, Color.WHITE, true))
        addSpace(hero, 8)
        hero.addView(label(
            "A visual contact book, quick number lookup and on-device block list in one place.",
            14f, 0xFFEAF2FF.toInt()
        ))
        addSpace(hero, 14)
        hero.addView(actionButton("Look up a number", true) {
            page = Page.LOOKUP
            renderPage()
            refreshNavigation()
        })
        pageContent.addView(hero, matchWrap())

        addSpace(pageContent, 18)
        pageContent.addView(sectionTitle("Quick search"))
        val searchCard = card()
        val search = editText("Enter a phone number, including country code")
        search.inputType = InputType.TYPE_CLASS_PHONE
        search.setText(lookupNumber)
        searchCard.addView(search, matchWrap())
        addSpace(searchCard, 8)
        searchCard.addView(actionButton("Identify caller", true) {
            lookupNumber = search.text.toString().trim()
            page = Page.LOOKUP
            renderPage()
            refreshNavigation()
            performLookup(lookupNumber)
        })
        pageContent.addView(searchCard, matchWrap(8))

        addSpace(pageContent, 18)
        pageContent.addView(sectionTitle("Your visual address book"))
        val peopleCard = card()
        peopleCard.addView(label(
            "Contacts stay on this phone. CallerSphere does not upload your address book.",
            13f, 0xFF52627A.toInt()
        ))
        addSpace(peopleCard, 10)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            if (contacts.isEmpty() && !contactsLoading) loadContacts()
            if (contactsLoading) {
                peopleCard.addView(label("Loading contacts…", 14f, 0xFF64748B.toInt()))
            } else if (contacts.isEmpty()) {
                peopleCard.addView(label("No phone contacts found.", 14f, 0xFF64748B.toInt()))
            } else {
                contacts.take(4).forEach { peopleCard.addView(contactRow(it), matchWrap(8)) }
                addSpace(peopleCard, 6)
                peopleCard.addView(actionButton("View all contacts", false) {
                    page = Page.CONTACTS
                    renderPage()
                    refreshNavigation()
                })
            }
        } else {
            peopleCard.addView(actionButton("Load contacts on this phone", true) { requestContacts() })
        }
        pageContent.addView(peopleCard, matchWrap(8))

        addSpace(pageContent, 18)
        pageContent.addView(sectionTitle("Call protection"))
        val protection = card()
        protection.addView(label("Block numbers you choose", 16f, 0xFF172B4D.toInt(), true))
        addSpace(protection, 5)
        protection.addView(label(
            "When Android grants CallerSphere the call-screening role, numbers on your local block list can be rejected automatically. The app does not block everyone labelled by an unverified report.",
            13f, 0xFF52627A.toInt()
        ))
        addSpace(protection, 10)
        protection.addView(actionButton("Set up call protection", false) {
            page = Page.MORE
            renderPage()
            refreshNavigation()
            requestCallScreeningRole()
        })
        pageContent.addView(protection, matchWrap(8))
        addSpace(pageContent, 10)
        pageContent.addView(label(
            "Early MVP • Cloud caller directory activates after Supabase setup. Results are not guaranteed identities.",
            12f, 0xFF718096.toInt()
        ))
    }

    private fun renderContacts() {
        pageContent.addView(sectionTitle("People"))
        pageContent.addView(label(
            "A visual list of contacts stored on this device. Nothing is synced to the cloud.",
            13f, 0xFF64748B.toInt()
        ))
        addSpace(pageContent, 10)
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            val c = card()
            c.addView(label("Allow contact access to show your phone book here.", 14f, 0xFF475569.toInt()))
            addSpace(c, 10)
            c.addView(actionButton("Allow contacts", true) { requestContacts() })
            pageContent.addView(c)
            return
        }
        val search = editText("Search contacts by name or number")
        search.setText(contactFilter)
        pageContent.addView(search, matchWrap())
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageContent.addView(results, matchWrap(8))
        fun updateResults(query: String) {
            contactFilter = query
            renderContactRows(results, query)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateResults(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        if (contacts.isEmpty() && !contactsLoading) loadContacts()
        if (contactsLoading) results.addView(label("Loading contacts…", 14f, 0xFF64748B.toInt()))
        else updateResults(contactFilter)
    }

    private fun renderContactRows(parent: LinearLayout, query: String) {
        parent.removeAllViews()
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = contacts.filter {
            it.name.lowercase(Locale.ROOT).contains(q) ||
                it.number.filter(Char::isDigit).contains(q.filter(Char::isDigit))
        }.sortedWith(compareByDescending<PhoneContact> { it.isStarred }.thenBy { it.name.lowercase(Locale.ROOT) })
        if (filtered.isEmpty()) {
            parent.addView(card().apply {
                addView(label(
                    if (contacts.isEmpty()) "No contacts loaded yet." else "No matching contacts.",
                    14f, 0xFF64748B.toInt()
                ))
            })
        } else {
            filtered.take(150).forEach { parent.addView(contactRow(it), matchWrap(8)) }
        }
    }

    private fun contactRow(contact: PhoneContact): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(10), dp(11))
            background = rounded(Color.WHITE, 16, 0xFFE7ECF4.toInt())
        }
        val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatar = TextView(this).apply {
            text = if (contact.isStarred) "★" else initial
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF165DCE.toInt())
            background = rounded(0xFFE7F0FF.toInt(), 100)
        }
        row.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)))
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
        }
        info.addView(label(contact.name, 14f, 0xFF172B4D.toInt(), true))
        addSpace(info, 3)
        info.addView(label(contact.number, 12f, 0xFF64748B.toInt()))
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val call = TextView(this).apply {
            text = "Call"
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = rounded(0xFF165DCE.toInt(), 12)
            setOnClickListener { openDialer(contact.number) }
        }
        row.addView(call)
        row.setOnClickListener { showContactActions(contact) }
        return row
    }

    private fun renderDialer() {
        pageContent.addView(sectionTitle("Dialer"))
        pageContent.addView(label(
            "Enter a number and choose call or lookup. Calling opens your phone app; CallerSphere does not place calls itself.",
            13f, 0xFF64748B.toInt()
        ))
        addSpace(pageContent, 12)
        val card = card()
        val number = editText("Phone number")
        number.inputType = InputType.TYPE_CLASS_PHONE
        number.textSize = 24f
        number.gravity = Gravity.CENTER
        number.setText(dialNumber)
        card.addView(number, matchWrap())
        addSpace(card, 14)
        val digits = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
        digits.forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { digit ->
                val key = actionButton(digit, false) {
                    dialNumber = number.text.toString() + digit
                    number.setText(dialNumber)
                    number.setSelection(number.text.length)
                }.apply { textSize = 20f }
                row.addView(key, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            card.addView(row, matchWrap())
        }
        addSpace(card, 8)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Delete", false) {
            dialNumber = number.text.toString().dropLast(1)
            number.setText(dialNumber)
            number.setSelection(number.text.length)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(actionButton("Call", true) {
            dialNumber = number.text.toString().trim()
            if (dialNumber.isBlank()) toast("Enter a phone number first.")
            else openDialer(dialNumber)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(6), 0, 0, 0)
        })
        card.addView(actions, matchWrap())
        addSpace(card, 8)
        card.addView(actionButton("Look up this number", false) {
            lookupNumber = number.text.toString().trim()
            page = Page.LOOKUP
            renderPage()
            refreshNavigation()
        })
        pageContent.addView(card)
    }

    private fun renderLookup() {
        pageContent.addView(sectionTitle("Identify a number"))
        pageContent.addView(label(
            "Enter the full international number where possible, such as +923001234567. Cloud lookup uses your Supabase project when configured.",
            13f, 0xFF64748B.toInt()
        ))
        addSpace(pageContent, 10)
        val card = card()
        val input = editText("+1 202 555 0147")
        input.inputType = InputType.TYPE_CLASS_PHONE
        input.setText(lookupNumber)
        card.addView(input, matchWrap())
        addSpace(card, 8)
        card.addView(actionButton("Search caller directory", true) {
            lookupNumber = input.text.toString().trim()
            performLookup(lookupNumber)
        })
        addSpace(card, 12)
        lookupResultView = label(
            "Search results will appear here. A name or spam label may be incomplete and must not be treated as proof of identity.",
            14f, 0xFF475569.toInt()
        )
        lookupResultView.setPadding(dp(13), dp(14), dp(13), dp(14))
        lookupResultView.background = rounded(0xFFF4F7FC.toInt(), 14)
        card.addView(lookupResultView, matchWrap())
        addSpace(card, 8)
        card.addView(actionButton("Block this number on this phone", false) {
            val raw = input.text.toString().trim()
            if (raw.isBlank()) toast("Enter a phone number first.")
            else {
                BlockListStore.add(this, raw)
                lastLookupNumber = raw
                toast("Number added to the local block list.")
            }
        })
        addSpace(card, 6)
        card.addView(actionButton("Report as spam", false) {
            val raw = input.text.toString().trim()
            if (raw.isBlank()) toast("Enter a phone number first.")
            else {
                lastLookupNumber = raw
                lookupResultView.text = "Sending spam report…"
                SupabaseCallerApi.reportSpam(this, raw) { result ->
                    runOnUiThread {
                        lookupResultView.text = result.message
                        if (result.ok) toast("Report submitted for review.")
                    }
                }
            }
        })
        pageContent.addView(card)

        addSpace(pageContent, 12)
        val note = card()
        note.addView(label("Protect your privacy", 15f, 0xFF172B4D.toInt(), true))
        addSpace(note, 5)
        note.addView(label(
            "CallerSphere does not upload your contacts. Directory entries should be public business information or sufficiently reviewed spam labels—not unverified names of private individuals.",
            13f, 0xFF64748B.toInt()
        ))
        pageContent.addView(note)
    }

    private fun performLookup(raw: String) {
        val number = raw.trim()
        if (number.isBlank()) {
            toast("Enter a phone number first.")
            return
        }
        lastLookupNumber = number
        if (number.filter(Char::isDigit).endsWith("12025550147")) {
            lookupResultView.text =
                "DEMO MATCH\nExample Service Desk\nSample data only; not a real or verified identity."
            return
        }
        lookupResultView.text = "Searching the caller directory…"
        SupabaseCallerApi.lookup(this, number) { result ->
            runOnUiThread {
                lookupResultView.text = if (result.found) {
                    "Possible caller: " + result.label + "\nCategory: " + result.category +
                        "\nCommunity information can be incomplete and is not a verified personal identity."
                } else {
                    result.message
                }
            }
        }
    }

    private fun renderMore() {
        pageContent.addView(sectionTitle("More"))
        val protection = card()
        protection.addView(label("Call protection", 17f, 0xFF172B4D.toInt(), true))
        addSpace(protection, 5)
        protection.addView(label(
            "CallerSphere only screens numbers you explicitly put on your local block list. It does not automatically reject every number labelled as spam by the community.",
            13f, 0xFF64748B.toInt()
        ))
        addSpace(protection, 10)
        protection.addView(actionButton("Choose call-screening app", true) { requestCallScreeningRole() })
        addSpace(protection, 6)
        protection.addView(actionButton("Add number to block list", false) { promptAddBlockedNumber() })
        pageContent.addView(protection)

        addSpace(pageContent, 14)
        pageContent.addView(sectionTitle("Blocked on this phone"))
        val blockCard = card()
        val blocked = BlockListStore.getAll(this).sorted()
        if (blocked.isEmpty()) {
            blockCard.addView(label("Your local block list is empty.", 14f, 0xFF64748B.toInt()))
        } else {
            blocked.forEach { number ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(label(number, 13f, 0xFF334155.toInt()),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(actionButton("Unblock", false) {
                    BlockListStore.remove(this@MainActivity, number)
                    renderPage()
                })
                blockCard.addView(row, matchWrap(6))
            }
        }
        pageContent.addView(blockCard)

        addSpace(pageContent, 14)
        val backend = card()
        backend.addView(label("Cloud directory", 17f, 0xFF172B4D.toInt(), true))
        addSpace(backend, 5)
        val configured = SupabaseCallerApi.isConfigured()
        backend.addView(label(
            if (configured) "Supabase URL and public client key are present in this build."
            else "Not configured in this build. Add SUPABASE_URL and SUPABASE_ANON_KEY as GitHub Actions repository secrets, then rebuild.",
            13f, 0xFF64748B.toInt()
        ))
        addSpace(backend, 10)
        backend.addView(label("Privacy controls", 15f, 0xFF172B4D.toInt(), true))
        addSpace(backend, 4)
        backend.addView(label(
            "Contacts are read locally only after permission. The app does not request SMS or call-log access in this MVP. Cloud lookups and spam reports are sent only when you use those buttons.",
            13f, 0xFF64748B.toInt()
        ))
        pageContent.addView(backend)

        addSpace(pageContent, 14)
        pageContent.addView(label(
            "CallerSphere is an independent early-stage app. Global caller identification, full-screen caller photos, SMS inbox integration, verified company records, and Play Store release setup still require further implementation and testing.",
            12f, 0xFF718096.toInt()
        ))
    }

    private fun loadContacts() {
        if (contactsLoading) return
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        contactsLoading = true
        io.execute {
            val result = mutableListOf<PhoneContact>()
            try {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                )
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection, null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
                )?.use { cursor ->
                    val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val starredCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                    while (cursor.moveToNext() && result.size < 500) {
                        val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        val number = if (numberCol >= 0) cursor.getString(numberCol).orEmpty() else ""
                        val starred = starredCol >= 0 && cursor.getInt(starredCol) == 1
                        if (name.isNotBlank() && number.isNotBlank()) result.add(PhoneContact(name, number, starred))
                    }
                }
            } catch (_: Exception) {
                // Keep the UI usable if a device contact provider returns an error.
            }
            runOnUiThread {
                contacts = result.distinctBy { it.number.filter(Char::isDigit) + "|" + it.name }
                contactsLoading = false
                renderPage()
            }
        }
    }

    private fun requestContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_CONTACTS)
        }
    }

    @Deprecated("Android permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts()
            } else {
                toast("Contacts permission was not granted. You can still use number lookup and dialer.")
            }
        }
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            toast("Call screening is not available on this device.")
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            toast("CallerSphere already has the call-screening role.")
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(
            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
            REQUEST_CALL_ROLE
        )
    }

    @Deprecated("Android role request callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CALL_ROLE) {
            toast(if (resultCode == RESULT_OK) "Call-screening role granted." else "Call-screening role not granted.")
        }
    }

    private fun promptAddBlockedNumber() {
        val input = editText("+923001234567")
        input.inputType = InputType.TYPE_CLASS_PHONE
        AlertDialog.Builder(this)
            .setTitle("Block a number on this phone")
            .setView(input)
            .setPositiveButton("Block") { _, _ ->
                val number = input.text.toString().trim()
                if (number.isBlank()) toast("Enter a phone number first.")
                else {
                    BlockListStore.add(this, number)
                    toast("Number added to your local block list.")
                    renderPage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showContactActions(contact: PhoneContact) {
        AlertDialog.Builder(this)
            .setTitle(contact.name)
            .setItems(arrayOf("Call", "Send SMS", "Open WhatsApp", "Block number on this phone")) { _, choice ->
                when (choice) {
                    0 -> openDialer(contact.number)
                    1 -> openSms(contact.number)
                    2 -> openWhatsApp(contact.number)
                    3 -> {
                        BlockListStore.add(this, contact.number)
                        toast("Number added to your local block list.")
                    }
                }
            }
            .show()
    }

    private fun openDialer(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null)))
        } catch (_: Exception) {
            toast("No compatible phone app was found.")
        }
    }

    private fun openSms(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.trim(), null)))
        } catch (_: Exception) {
            toast("No compatible messaging app was found.")
        }
    }

    private fun openWhatsApp(number: String) {
        val digits = number.filter(Char::isDigit)
        if (digits.isBlank()) {
            toast("This contact has no valid phone number.")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits)))
        } catch (_: Exception) {
            toast("Unable to open WhatsApp or a compatible browser.")
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(Color.WHITE, 18, 0xFFE7ECF4.toInt())
        elevation = dp(1).toFloat()
    }

    private fun sectionTitle(text: String): TextView =
        label(text, 19f, 0xFF172B4D.toInt(), true).apply {
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun label(
        text: String, size: Float, color: Int, bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun editText(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 15f
        singleLine = true
        setTextColor(0xFF172B4D.toInt())
        setHintTextColor(0xFF91A0B7.toInt())
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = rounded(0xFFF8FAFD.toInt(), 12, 0xFFDBE3F0.toInt())
    }

    private fun actionButton(
        text: String, primary: Boolean, action: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(if (primary) Color.WHITE else 0xFF165DCE.toInt())
        background = rounded(if (primary) 0xFF165DCE.toInt() else 0xFFEAF2FF.toInt(), 12)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { if (top > 0) topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_CALL_ROLE = 4001
        private const val REQUEST_CONTACTS = 4002
    }
}
