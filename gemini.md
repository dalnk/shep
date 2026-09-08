# Gemini.md for HurrDurr App Development

## Project Overview
HurrDurr is a simple, novice-friendly chat app for Android that acts as a remote control/interface for herdr.dev services. It features a modern Material You interface, supports foldable devices with adaptive layouts, and prioritizes ease of use.

**Key Features:**
- Real-time chat with herdr.dev backend
- Beautiful, modern UI using Jetpack Compose
- Foldable support with WindowManager and adaptive layouts
- Offline support with Room database
- Easy onboarding for beginners
- Push notifications
- Minimal permissions

## Tech Stack (Recommended for Novices)
- **Language**: Kotlin
- **UI**: Jetpack Compose (declarative, easier than XML)
- **Architecture**: MVVM with ViewModel + StateFlow
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp + Kotlinx Serialization
- **Database**: Room
- **Auth**: Firebase Auth or custom token-based
- **Foldables**: Jetpack Window
- **Build Tool**: Gradle with Kotlin DSL

## Project Structure
```
app/
├── src/main/java/com/herdr/hurrdurr/
│   ├── data/          # Repositories, models, DAOs
│   ├── di/            # Hilt modules
│   ├── domain/        # Use cases
│   ├── ui/            # Screens, components, themes
│   ├── util/          # Helpers
│   └── MainActivity.kt
├── src/main/res/      # Minimal - mostly drawables, values
└── build.gradle.kts
```

## Setting Up the Project
1\. Open Android Studio (latest Hedgehog or newer)
2\. New Project → Empty Compose Activity
3\. Target SDK 35+, Min SDK 24 (for broad compatibility)
4\. Enable Compose, View Binding if needed

Add dependencies in build.gradle.kts:

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.herdr.hurrdurr"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.herdr.hurrdurr"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.1")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Retrofit + Serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Window for foldables
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.window:window-core:1.3.0")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    
    // Firebase (optional)
    // implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
}
```

## Modern UI with Material 3
Use MaterialTheme with dynamic colors:

```kotlin
// Theme.kt
@Composable
fun HurrDurrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        else -> dynamicLightColorScheme(LocalContext.current)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

## Chat Screen Example
```kotlin
// ChatScreen.kt
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("HurrDurr") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true
            ) {
                items(state.messages) { message ->
                    ChatBubble(message = message)
                }
            }
            MessageInput(onSend = { viewModel.sendMessage(it) })
        }
    }
}
```

## Foldable Support
Use Jetpack Window to detect folding state:

```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val windowInfo = calculateWindowSizeClass(this)
            val foldingFeature = rememberFoldableState()
            
            HurrDurrTheme {
                if (foldingFeature?.state == FoldingFeature.State.HALF_OPENED) {
                    // Dual-screen layout for foldables
                    FoldableChatLayout()
                } else {
                    StandardChatLayout()
                }
            }
        }
    }
}
```

## Novice-Friendly Tips
- Use Compose previews extensively for rapid iteration
- Follow single responsibility principle
- Provide clear error messages and loading states
- Include a tutorial/onboarding flow on first launch
- Use ConstraintLayout or standard Compose layouts for responsiveness
- Test on emulators with foldable configurations (Pixel Fold, etc.)

## Backend Integration (herdr.dev)
Assume WebSocket or REST API for chat. Implement repository pattern for easy swapping.

## Next Steps
1\. Implement authentication flow
2\. Set up real-time messaging
3\. Add media sharing support
4\. Polish animations with AnimatedVisibility
5\. Publish to Play Store with proper signing

For more details, refer to official Android docs and Compose codelabs. Always test on physical foldable devices if possible.