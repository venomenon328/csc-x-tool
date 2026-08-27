# Launcher und Paketierung

Dieser Ordner ist für den komfortablen Windows-Start und die Paketierung des CSC X Tool vorgesehen.

## Zielverhalten

Ein normaler Programmaufruf soll:

1. prüfen, ob bereits eine lokale Instanz läuft
2. gegebenenfalls nur die bestehende Anwendung im Browser öffnen
3. andernfalls den lokalen Spring-Boot-Prozess ohne sichtbares Konsolenfenster starten
4. den erfolgreichen Health-Check abwarten
5. den vom Betriebssystem konfigurierten Standardbrowser öffnen

Ist Vivaldi als Standardbrowser eingerichtet, wird Vivaldi verwendet. Eine eigene Browserauswahl innerhalb des Tools ist nicht vorgesehen.

## Prozesslebenszyklus

- genau eine laufende Instanz je Benutzerprofil
- Instanzinformationen unter dem lokalen Anwendungsverzeichnis
- Erkennung und Bereinigung veralteter Instanzdateien
- kontrolliertes Herunterfahren über eine Aktion in der Weboberfläche
- sauberes Schließen der SQLite-Datenbank
- keine notwendige PowerShell- oder Konsolenbedienung

## Vorgesehene Paketierung

- `jpackage` oder gleichwertiges Werkzeug
- gebündelte Java-Laufzeit
- Windows-App-Image oder Installer
- Startmenüeintrag und optional Desktopverknüpfung
- Anwendungsicon in `launcher/assets/`
- Paketierungsdateien und Skripte in `launcher/packaging/`

## Nicht Aufgabe des Launchers

- keine eigene fachliche Benutzeroberfläche
- kein eingebettetes Browserfenster
- keine Benutzeranmeldung
- keine Updateplattform in der ersten Version
- keine Datenbanklogik außerhalb kontrollierter Start- und Stop-Hooks

## Noch nicht enthalten

Konkrete Paketierungsdateien folgen, sobald Backend und Frontend gemeinsam ausführbar sind. Der Launcher wird nicht vorgezogen, nur damit ein sehr komfortabler Doppelklick anschließend zuverlässig auf eine noch leere Anwendung führt.
