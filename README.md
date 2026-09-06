# বকেয়া · Bokeya

**আপনার সব বকেয়া, ধার আর হিসাব — এক জায়গায়।**

Bokeya is a free, offline-first Android app for tracking the money you owe and
the money you spend. It is built for the way debt actually works in Bangladesh:
the running tab at the corner shop, the NGO loan with weekly instalments, the
phone bought on EMI, and the ৳৫০০ a friend lent you last Eid.

Everything lives on your phone. There is no account to create, no server to
sync with, no subscription, and no ads.

---

## Table of contents

- [Download](#download)
- [Why Bokeya](#why-bokeya)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Build & run](#build--run)
- [Testing](#testing)
- [Privacy philosophy](#privacy-philosophy)
- [License](#license)
- [Developer](#developer)

---

## Download

Every push to the build branch produces installable APKs. You do not need
Android Studio to try the app.

**Easiest — the rolling release:**
[**github.com/shohanbusinessmail-cmd/Bokeya/releases/tag/latest-build**](https://github.com/shohanbusinessmail-cmd/Bokeya/releases/tag/latest-build)

Download `bokeya-<version>-<commit>-debug.apk`, copy it to your phone, and open
it. Android will ask you to allow "Install unknown apps" for whichever app you
opened it from — that is expected for any APK installed outside the Play Store.

**Alternative — the CI artifact:** open the
[Actions tab](https://github.com/shohanbusinessmail-cmd/Bokeya/actions), pick the
newest green **Build check** run, and download the **bokeya-apk** ZIP from the
*Artifacts* section at the bottom. Artifacts are kept for 90 days and require
you to be signed in to GitHub.

| File | Use it for |
| --- | --- |
| `…-debug.apk` | Everyday testing. Application id `com.shohan.bokeya.debug`, so it installs alongside a release copy, and it exposes the "নমুনা তথ্য" demo-data option in Settings. |
| `…-release.apk` | Checking the minified R8 build. **Not publishable** — see the signing note under [Build & run](#build--run). |

Minimum Android version: **7.0 (API 24)**.

---

## Why Bokeya

Most expense apps assume a salaried user with a bank account and a credit card.
That is not how most people in Bangladesh track money. What they actually need
to remember is:

- how much is on the tab at **রহিম স্টোর**,
- when the next **NGO কিস্তি** is due,
- how many **EMI** payments are left on the fridge,
- and who still owes them a personal ধার.

Bokeya models those four things as first-class concepts instead of forcing them
into a generic "transaction" list, then layers ordinary income and expense
tracking on top.

The interface is Bangla-first — plain, spoken Bangla rather than translated
finance jargon — while keeping the technical terms people already use in
English (EMI, PIN, Backup, Restore, PDF, CSV).

---

## Features

### Four kinds of debt, each modelled properly

| Type | What it tracks |
|---|---|
| **দোকান / বাজার / বাকি** | Line items (item, quantity, unit, unit price, total), running tab, partial payments |
| **Bank / NGO Loan** | Principal, optional interest, total payable, instalment amount and frequency (daily / weekly / monthly / custom), auto-generated schedule |
| **EMI / কিস্তি** | Cash price, down payment, financed amount, tenure, per-month EMI, paid vs remaining instalments, overdue detection |
| **ব্যক্তিগত ধার** | Person, relationship, phone, direction (borrowed or lent), repayment history |

### আয়-ব্যয় (income & expense)

Ten built-in Bangla categories (খাবার, বাজার, যাতায়াত, বাসা, চিকিৎসা, শিক্ষা,
বিল, কেনাকাটা, বিনোদন, অন্যান্য) plus your own custom categories, with monthly
totals and a per-category breakdown.

### Everything else

- **Dashboard** — greeting, total বকেয়া, per-type breakdown, today's
  income/expense/net, upcoming and overdue payments, charts.
- **Payments** — partial payments with full history, a payment bottom sheet
  (amount, date, time, method, note) and a success state showing what was paid
  and what remains.
- **Smart reminders** — WorkManager-scheduled notifications for upcoming, due
  today and overdue payments, with configurable lead time and quiet toggles.
- **Calendar** — a month grid marked with dues, income and expense, plus a
  per-day detail panel.
- **Insights** — month-over-month spending change, top category, debt
  composition and the next seven days' payable, computed entirely on-device.
- **Search & history** — global search across shops, people, notes and
  transactions; history filtered by type/status and sorted by date or amount.
- **Reports** — monthly, debt, loan, income-expense and full reports exported
  as CSV or as a typeset PDF.
- **Backup & restore** — versioned JSON backup through the Storage Access
  Framework, with a confirmation step before any restore.
- **App Lock** — PIN (stored only as a salted PBKDF2 hash) with optional
  biometric unlock.
- **Bangla throughout** — Bengali numerals, Bengali dates with relative
  আজ / আগামীকাল / গতকাল wording, and the Hind Siliguri typeface bundled in.
- **Accessibility** — contrast-checked colours, 48dp touch targets, content
  descriptions, scalable text, and status conveyed by icon and text rather than
  colour alone.
- **Light & dark themes**, optional Material You dynamic colour on Android 12+.

---

## Screenshots

_Screenshots will be added here once the app is published._

| Dashboard | Account detail | Calendar | Insights |
|---|---|---|---|
| _(placeholder)_ | _(placeholder)_ | _(placeholder)_ | _(placeholder)_ |

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM + Clean Architecture, Repository pattern |
| Persistence | Room 2.8 (SQLite), DataStore Preferences |
| Async | Coroutines, Flow / StateFlow |
| Background work | WorkManager |
| Navigation | Navigation Compose |
| Security | AndroidX Biometric, PBKDF2 PIN hashing |
| Serialisation | kotlinx.serialization (backup files) |
| Build | Gradle 8.14 with Kotlin DSL + version catalog, KSP |
| Testing | JUnit, Robolectric, Room testing, coroutines-test, Turbine |
| Min / target SDK | 24 / 36 |

No analytics SDK, no crash reporter, no ad network, no third-party backend.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  ui/            Compose screens, components, theme       │
│                 ViewModels expose immutable UiState      │
├──────────────────────────────────────────────────────────┤
│  domain/        Pure Kotlin. Models, calculators,        │
│                 use cases. No Android imports.           │
├──────────────────────────────────────────────────────────┤
│  data/          Room entities/DAOs, repositories that    │
│                 map entities ⇄ domain models, DataStore  │
├──────────────────────────────────────────────────────────┤
│  core/          Money, MoneyFormatter, BanglaDate        │
└──────────────────────────────────────────────────────────┘
```

Three decisions shape most of the codebase:

**1. Money is an integer, never a float.**
`Money` is a `value class` wrapping a `Long` count of *poisha* (1 Taka = 100
poisha). Binary floating point cannot represent `0.10` exactly, and those errors
accumulate across hundreds of partial payments until a settled account still
shows ৳0.01 outstanding. Rounding happens in exactly one place: when the user
types a decimal amount.

**2. Balances are computed in one place.**
`BalanceCalculator` owns `remaining = (total − paid).coerceAtLeastZero()`, the
status precedence rules, and payment allocation across instalments. No ViewModel
re-derives them, so a fix lands everywhere at once. Overpayment is recordable —
the user really did hand over the money — but a balance can never go negative.

**3. Dependencies are wired by hand.**
`di/AppContainer` is a plain class holding the singletons, created once in
`BokeyaApp` and passed down. For an app this size a DI framework would add build
time and indirection without removing any real work.

Other notable choices:

- One `accounts` table backs all four debt types, with a cached `nextDueDate`
  so the dashboard never recomputes schedules while scrolling.
- Child rows (items, payments, instalments) cascade-delete; deleting a category
  nulls the reference rather than deleting the money.
- All database access is `suspend` or `Flow`, dispatched off the main thread.
- Room schemas are exported to `app/schemas` and there is deliberately **no**
  `fallbackToDestructiveMigration()` — losing a user's financial history on
  upgrade is never acceptable.

---

## Project structure

```
app/src/main/java/com/shohan/bokeya/
├── core/
│   ├── money/          Money value class + Bengali-aware formatter
│   └── datetime/       Bengali dates, relative wording, week/month helpers
├── domain/
│   ├── model/          Account, Payment, Category, summaries, enums
│   ├── calc/           BalanceCalculator, ScheduleGenerator
│   └── usecase/        DashboardUseCase, InsightsUseCase
├── data/
│   ├── local/          Room database, entities, DAOs, converters
│   ├── repository/     Account / Money / Ledger repositories + mappers
│   └── prefs/          DataStore-backed settings
├── notification/       Channels, WorkManager worker, scheduler, receivers
├── backup/             Versioned JSON export & restore
├── export/             CSV and PDF report writers
├── security/           PBKDF2 PIN manager
├── di/                 AppContainer
├── sample/             Debug-only demo data seeder
└── ui/
    ├── theme/          Colour ramps, typography, shapes
    ├── components/     Reusable cards, charts, inputs, list rows
    ├── navigation/     Routes, nav host, bottom bar, expandable FAB
    ├── viewmodel/      One ViewModel per screen + factory
    └── screens/        dashboard, accounts, payment, money, calendar,
                        history, search, insights, reports, reminders,
                        settings, backup, about, onboarding, lock, more
```

---

## Build & run

If you only want to install the app, grab a prebuilt APK from
[Download](#download) instead — building from source is only needed for
development.

**Requirements:** JDK 17, Android SDK 36, Android Studio Ladybug or newer.

```bash
git clone https://github.com/shohanbusinessmail-cmd/Bokeya.git
cd Bokeya

# Debug APK
./gradlew assembleDebug

# Install onto a connected device
./gradlew installDebug

# Release build (see the note below before publishing)
./gradlew assembleRelease
```

The debug build uses the application id `com.shohan.bokeya.debug`, so it can sit
alongside a release install, and it enables a "নমুনা তথ্য" option in Settings for
populating the app with demo data.

> **Before publishing:** the release build type is currently signed with the
> debug keystore so that CI can produce a runnable artifact. Replace
> `signingConfigs` in `app/build.gradle.kts` with a real upload key first.

---

## Testing

```bash
./gradlew testDebugUnitTest       # unit + Robolectric tests
./gradlew connectedDebugAndroidTest   # instrumented tests (device required)
```

The suite covers the parts where a bug costs the user money:

- **Money** — exactness, splitting with remainders, Bengali parsing, no drift
  across a thousand additions.
- **BalanceCalculator** — the required cases explicitly: `10000 − 2500 = 7500`,
  `7500 − 7500 = 0`, no negative remaining on overpayment, status precedence,
  instalment allocation.
- **ScheduleGenerator** — schedules that sum exactly to the amount owed, EMI
  splits, month-end clamping (31 Jan → 28/29 Feb), simple interest.
- **BanglaDate** — relative wording, Saturday-first weeks, leap years, 12-hour
  Bengali times.
- **Repositories** — against a real in-memory Room database with foreign keys
  enabled: totals, partial payments, cascade deletes, category orphaning.
- **Database** — cascades, `IFNULL` aggregates, reminder-log de-duplication.
- **DashboardUseCase** — bucket nesting, lent-vs-borrowed separation.

---

## Privacy philosophy

Bokeya is built on the assumption that your financial history is nobody else's
business — including the developer's.

- **The app does not request the `INTERNET` permission.** Not "does not use the
  network" — it is not able to. You can verify this in `AndroidManifest.xml`.
- No analytics, no crash reporting, no advertising SDK, no third-party
  libraries that phone home.
- All data stays in the app's private database. Backups are written only where
  you choose, through the system file picker.
- The App Lock PIN is stored as a salted PBKDF2 hash, never in plain text, and
  is deliberately excluded from backup files.
- Nothing sensitive is written to logcat, and debug logging is compiled out of
  release builds.
- No account, no sign-in, no cloud, no subscription.

---

## License

Released under the MIT License — see [LICENSE](LICENSE).

The bundled [Hind Siliguri](https://fonts.google.com/specimen/Hind+Siliguri)
typeface is licensed under the SIL Open Font License 1.1; see
[licenses/OFL-HindSiliguri.txt](licenses/OFL-HindSiliguri.txt).

---

## Developer

**Shohan Khan**
✉️ [helloiamshohan@gmail.com](mailto:helloiamshohan@gmail.com)

Made in Bangladesh 🇧🇩
