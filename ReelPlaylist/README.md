# Reel Playlist

Statt der For-You-Page läuft in Instagram deine eigene Liste weiter (Likes oder Gespeichert).
Instagram wird NICHT verändert; die App öffnet per Deeplink das jeweils nächste Reel aus deiner Liste.

## APK herunterladen

[Debug-APK herunterladen](app-debug.apk)

Die APK benötigt Android 11 oder neuer. Sie ist mit einem Debug-Schlüssel signiert und nicht als Play-Store-Release gedacht.

## 1. Daten holen (einmalig, kann Minuten bis Tage dauern)
Instagram → Profil → ☰ → Konto-Center → Deine Informationen und Berechtigungen → Deine Informationen herunterladen
→ Bestimmte Informationen auswählen (Likes, Gespeichert) → Format **JSON** → Gesamter Zeitraum.
(Die Menünamen ändern sich ab und zu.) ZIP entpacken. Gesucht:
- `.../likes/liked_posts.json`
- `.../saved/saved_posts.json`
(Pfad kann leicht abweichen, z. B. mit Ordner `your_instagram_activity`.)

## 2. App installieren oder bauen
Die APK liegt unter `app-debug.apk`. Zum Bauen den Ordner `ReelPlaylist` in Android Studio öffnen, Gradle Sync ausführen und **Build → Build APK(s)** wählen. Benötigt werden JDK 17 und Gradle 8.7.

## 3. Bedienungshilfe aktivieren
Einstellungen → Bedienungshilfen → Reel Playlist → an.
Android 13+ sperrt das bei Sideload-Apps ("Eingeschränkte Einstellung"). Lösung:
App-Info → ⋮ → "Eingeschränkte Einstellungen zulassen", oder per ADB:
    adb shell appops set de.local.reelplaylist ACCESS_RESTRICTED_SETTINGS allow

Falls Reels beim Weiterschalten nicht aus dem Hintergrund starten (neuere Android-Versionen sind streng):
    adb shell appops set de.local.reelplaylist SYSTEM_ALERT_WINDOW allow

## 4. Benutzen
1. Beide JSON-Dateien importieren.
2. Liste wählen (Likes / Gespeichert), "Start / Weiter" tippen. Instagram öffnet das erste Reel.
3. Weiter mit: Lautstärke leiser (nächstes), lauter (vorheriges), oder den schwebenden Buttons » « ✕.
4. Die Position wird gespeichert. Beim nächsten Mal "Start / Weiter" = dort weitermachen.

## 5. Swipe-Erkennung (experimentell)
Funktioniert nur, wenn Instagram seine Scroll-Ereignisse samt View-ID an die Bedienungshilfe meldet.
1. "Debug" einschalten, in Instagram im Reels-Viewer ein paar Mal wischen.
2. In der App unten im Debug-Log nach dem Eintrag suchen, der beim Reel-Wischen auftaucht
   (nicht bei Kommentaren o. Ä.). Ein Teil der ID (Standard: `clips_viewer`) muss darin vorkommen.
3. Passenden Teil als Pager-ID speichern, "Swipe-Erkennung" einschalten.
Wenn nichts Passendes im Log steht, nimm Lautstärketasten/Buttons.

## Hinweise
- Liste leer? "Nur /reel/-Links" ausschalten (manche Reels stehen im Export als /p/-Links).
- Reihenfolge: standardmäßig neueste zuerst (wie in Instagram); mit "umkehren" älteste zuerst.
- Der Dienst wertet nur den Paketnamen der Apps und Scroll-Ereignisse von Instagram aus. Es wird nichts gespeichert oder gesendet (die App hat keine Internet-Berechtigung).
- Der APK-Build wurde erfolgreich erstellt. Die App-Funktionen sollten auf dem Zielgerät geprüft werden.
