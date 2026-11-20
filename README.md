# Ohrhörer-Akku (Widget für JBL Tune 255NC)

Zeigt links, rechts und Case der JBL als Widget (Symbol + Prozent, Farbverlauf rot → orange → grün).
Nicht verbunden = alles grau mit Strich.

## APK bauen
1. Alle Dateien dieses Ordners in ein GitHub-Repo hochladen (inkl. `.github/workflows/build.yml`).
2. Im Repo: Actions → „APK bauen“ → Run workflow (oder einfach pushen).
3. Fertigen Lauf öffnen → unten „ohrhoerer-akku-apk“ herunterladen → `app-debug.apk` installieren.

## Einrichten
1. App „Ohrhörer-Akku“ öffnen, „Bluetooth-Erlaubnis erteilen“.
2. Startbildschirm lange drücken → Widgets → „Ohrhörer-Akku“ platzieren.
3. Tipp aufs Widget = jetzt aktualisieren. Beim Verbinden/Trennen der JBL aktualisiert es sich selbst.
