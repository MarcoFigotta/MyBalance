# MyBalance

App Android (Kotlin + Jetpack Compose) per gestire un portafoglio personale.
Italiano / English, tema chiaro (predefinito) o scuro. Tutti i dati restano sul telefono.

## Come ottenere l'APK

### Opzione A - da telefono, senza computer (GitHub Actions)
1. Crea un account gratuito su github.com e un nuovo repository (privato va bene).
2. Carica **tutti** i file di questo progetto nel repository, mantenendo le cartelle
   (compresa la cartella nascosta `.github`).
3. Vai nella scheda **Actions** -> **Build APK** -> **Run workflow**.
4. Dopo circa 5-10 minuti apri l'esecuzione completata e scarica l'artefatto
   **MyBalance-apk**: dentro c'e' `app-release.apk`.

### Opzione B - con un computer (Android Studio)
1. Installa Android Studio e usa **File -> Open** sulla cartella `MyBalance`.
2. Attendi la sincronizzazione di Gradle.
3. **Build -> Build APK(s)** (oppure da terminale: `./gradlew assembleRelease`).
4. L'APK si trova in `app/build/outputs/apk/release/app-release.apk`.

## Installazione sul telefono
1. Copia l'APK sul telefono e aprilo.
2. Se richiesto, consenti "Installa app da fonti sconosciute" per l'app che hai usato per aprirlo.

## Note
- L'APK e' firmato con la chiave di debug: va bene per uso personale, non per il Play Store.
- Il recupero password con OTP apre l'app di posta con il codice gia' scritto.
  Per un invio automatico serve un servizio e-mail: basta implementare `OtpSender`
  (file `Security.kt`) e usarla in `ForgotScreen` (file `AuthScreens.kt`).
- Valute disponibili: EUR (predefinita) e USD.
