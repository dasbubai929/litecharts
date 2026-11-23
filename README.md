# Lite Charts — Mobile Android Charting App

A lightweight, mobile-first Android charting application in Kotlin (`minSdkVersion 23`) built using **TradingView Lightweight Charts** (`com.tradingview:lightweightcharts`), connected to **OANDA's v3 Practice API**, featuring a modular **DCC Indicator Engine** and a built-in touch-friendly drawing and measurement overlay.

---

## Features

1. **TradingView Lightweight Charts Integration**:
   - Hardware-accelerated, high-performance candlestick charting.
   - Clean dark theme (`#0D1117` background, `#26A69A` up-candles, `#EF5350` down-candles).
   - Native pinch-to-zoom and pan support.

2. **13-Market Watchlist**:
   - Single top-level declaration in `Markets.kt` (`MARKETS` list):
     `EUR_USD`, `GBP_USD`, `USD_JPY`, `AUD_USD`, `USD_CAD`, `USD_CHF`, `AUD_JPY`, `XAU_USD`, `WTICO_USD`, `SPX500_USD`, `NAS100_USD`, `US30_USD`, `BTC_USD`.
   - Compact dialog selector on the top bar for one-tap switching.

3. **Timeframe Selector**:
   - Compact scrollable horizontal strip: `M1`, `M3`, `M5`, `M15`, `M30`, `H1`, `H4`, `1D`, `1W`.
   - Directly mapped to OANDA's `granularity` parameters (`M1`, `M3`, `M5`, `M15`, `M30`, `H1`, `H4`, `D`, `W`) on `/v3/instruments/{instrument}/candles?price=M`.

4. **Modular DCC Indicator Engine (`DccIndicatorEngine.kt`)**:
   - Strongly typed `calculate(candles: List<OandaCandle>): IndicatorResult` returning lines and signal markers aligned with candle timestamps.
   - Includes a starter EMA channel (9/21) + ATR bands + crossover markers so charts render immediately.
   - **Plug-and-play**: Paste translated Pine Script indicator math into `calculate()` without touching UI or chart code.
   - Toggle switch in top bar to show/hide the DCC overlay on demand.

5. **Built-In Touch Drawing & Measurement Overlay (`DrawingOverlayView.kt`)**:
   - Real-time coordinate sync: Screen touches are converted to chart coordinates `(time, price)` via `priceToY`/`timeToX`/`coordinateToPrice`.
   - Drawings stay locked to exact price levels and timestamps across chart pans and zooms.
   - **Supported Tools**:
     - **Trend Tools**:
       - *Horizontal line*: Single tap places a line across the entire chart with price badge.
       - *Trendline*: 2 taps anchor a line between two points.
     - **Gann and Fibonacci**:
       - *Fib retracement*: 2 taps (high ↔ low) renders standard Fibonacci levels (`0.0`, `0.236`, `0.382`, `0.5`, `0.618`, `0.786`, `1.0`) with price tags and level shading.
     - **Forecasting and Measurement**:
       - *Long position*: 3 taps (entry → stop → target) renders green target zone, red stop zone, and risk-to-reward ratio (`R:R`).
       - *Short position*: 3 taps (entry → stop → target) renders inverted target/stop zones with `R:R`.
       - *Anchored volume profile (AVP)*: 1 tap anchors volume distribution calculation from selected bar to the present.
       - *Fixed range volume profile (FRV)*: 2 taps calculate and render volume distribution within a selected range.
       - *Price range*: 2 taps measures price delta, percentage change, and bar count.
     - **Geometric Shapes**:
       - *Rectangle*: 2 taps for diagonal corners.
     - **Controls**:
       - `Pan`: None mode — passes all touch gestures directly to the chart for smooth zooming/panning.
       - `Clear`: Deletes all drawings for the current market and timeframe.
   - **Persistence**: Drawings are serialized to JSON and persisted per `(market, timeframe)` in Android `SharedPreferences`.

6. **Secure API Key Management**:
   - Key injected via `local.properties` (`OANDA_API_KEY=...`) into `BuildConfig.OANDA_API_KEY`.
   - Never committed to version control (`.gitignore` protects `local.properties`).

---

## Project Structure

```
Lite Charts/
├── app/
│   ├── build.gradle.kts          # App dependencies & BuildConfig configuration
│   ├── proguard-rules.pro        # Proguard rules for OkHttp and WebView bridge
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions (INTERNET) & MainActivity
│       ├── java/com/litecharts/app/
│       │   ├── MainActivity.kt               # Main controller & UI event binding
│       │   ├── chart/
│       │   │   └── ChartManager.kt           # TradingView Lightweight Charts bridge & coordinate mapping
│       │   ├── data/
│       │   │   ├── Markets.kt                # Top-level 13-market list
│       │   │   ├── Timeframes.kt             # Timeframes & OANDA granularity mapping
│       │   │   ├── OandaModels.kt            # OandaCandle data class
│       │   │   └── OandaApiService.kt        # OkHttp client for OANDA v3 Practice API
│       │   ├── drawing/
│       │   │   ├── DrawingModels.kt          # Sealed Drawing classes & JSON serialization
│       │   │   ├── DrawingOverlayView.kt     # Custom Canvas overlay with touch gesture handling
│       │   │   └── DrawingPersistence.kt     # SharedPreferences persistence
│       │   └── indicator/
│       │       └── DccIndicatorEngine.kt     # Modular DCC indicator math & TA helper functions
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml         # Mobile-first dark layout
│           └── values/
│               ├── colors.xml                # Dark theme palette
│               ├── strings.xml               # App name
│               └── themes.xml                # NoActionBar Material dark theme
├── build.gradle.kts              # Root build script
├── settings.gradle.kts           # Root settings & repository config
├── gradle.properties             # JVM arguments & AndroidX flags
├── local.properties              # OANDA_API_KEY configuration
├── gradlew & gradlew.bat         # Gradle wrapper scripts
└── README.md
```

---

## How to Configure Your OANDA API Key

The key provided is already configured in `local.properties`:

```properties
OANDA_API_KEY=af2cbf06afafb47fde5549338fffa54e-938b48b11be124229f8a5435d3fae0fc
```

To update or change it at any time, edit `local.properties` in the root folder.

---

## Modular DCC Indicator Engine (`DccIndicatorEngine.kt`)

Fully translated from **DCC Strategy v2.5.5 (`DCC+TSM+VPSR+SQZ`)**:

1. **01 • EMA / Trend**:
   - Fast EMA (period: 9, color: `#B37D13`)
   - Slow EMA (period: 21, color: `#1E4D6B`)
2. **02 • VWAP**:
   - Daily Anchored VWAP (source: `(H+L+C)/3`, color: `#6D4C7D`) resets automatically at 00:00 UTC each day.
3. **03 • HTF EMA 1 & Bias**:
   - H1 HTF EMA (length: 21, smoothed approximation) driving live market BIAS (`LONG`, `SHORT`, or `FLAT`).
4. **04 & 05 • DMI / ADX & Stop Loss**:
   - Directional Movement Index with 14-period Wilder's RMA ADX.
   - Dynamic Risk / Live Stop Loss level based on `0.9 × ATR(14)`.
5. **10 • Trend Strength Matrix (TSM)**:
   - Multi-factor composite trend score combining normalized RSI(14), MACD(12,26,9), ADX direction/strength, and 50-period SMA weight.
   - Displays percentage and strong trend flag (`★` when `|TSM| ≥ 50%`).
6. **11 & 12 • Signals & Markers**:
   - `❌` Crossover Markers: Bullish (`#4F7965`) and Bearish (`#A35C62`) plotted directly on candles.
   - Pullback Swing Entry Signals: `PB BUY` (triangle up) and `PB SELL` (triangle down) following EMA touch and breakout confirmation.
   - P3 Re-Entry Signals: `P3 BUY` (`#00B0FF`) and `P3 SELL` (`#FF6D00`) for trend continuation.
7. **15 & 16 • Squeeze Index (SQZ) & Live Table HUD**:
   - Tracks convergence/divergence compression `psi` with states `COMP` (Compression), `REL` (Release), and `IDLE`.
   - Compact top HUD banner renders live metrics (`BIAS`, `ADX`, `TSM`, `SL`, and `SQZ`).

---

## Building and Running

Open the project in **Android Studio**:
1. Select **File > Open** and choose the `Lite Charts` folder.
2. Android Studio will automatically sync the Gradle configuration.
3. Select an emulator or connected physical Android device (API 23+).
4. Click **Run (Shift + F10)**.

Or from terminal:
```bash
./gradlew assembleDebug
```

---

## Compiling with GitHub Actions (Cloud CI/CD)

The repository includes a ready-to-use GitHub Actions workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml).

### How to Build & Download the APK:
1. Every `git push` to `main` automatically triggers the build.
2. You can also trigger it manually:
   - Go to your repository on GitHub: [https://github.com/dasbubai929/litecharts](https://github.com/dasbubai929/litecharts)
   - Click the **Actions** tab at the top.
   - Select **Build Android APK** from the workflows list on the left.
   - Click **Run workflow** > **Run workflow**.
3. Once the workflow finishes (typically ~1-2 minutes):
   - Click on the completed workflow run.
   - Scroll down to the **Artifacts** section at the bottom of the page.
   - Click **LiteCharts-debug-apk** to download the compiled `.apk` directly to your phone or computer.

### Optional: Setting your Secret API Key in GitHub
To change your API key in GitHub Actions:
1. Go to repository **Settings** > **Secrets and variables** > **Actions**.
2. Click **New repository secret**.
3. Set Name to `OANDA_API_KEY` and Value to your token.
*(If omitted, it automatically falls back to your configured practice key).*
