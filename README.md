# Charter (Paper 1.21.11)

Eigenstaendiger Nachbau der Charter-Mechaniken als Paper-Plugin. Enthaelt keinen Code
und keine Assets der Original-Mod (Lizenz: All Rights Reserved).

## Bauen
```
gradle build          # Java 21, Paper-API 1.21.11
```
Jar: `build/libs/Charter-0.1.0.jar` -> in `plugins/`.

## Resource Pack
`resourcepack/` zippen (Inhalt direkt in die ZIP, `pack.mcmeta` im Root) und in der
`server.properties` als `resource-pack` setzen. Die Modelle nutzen aktuell Vanilla-Texturen
als Platzhalter. Eigene Texturen: `assets/charter/textures/item/*.png` anlegen und in
`assets/charter/models/item/*.json` eintragen.
`pack_format` (75) bitte gegen 1.21.11 pruefen.

## Items (`/charter give <Spieler> <Item>`)
| ID | Funktion |
|---|---|
| `contract` | Rechtsklick Luft/Block: an dich binden. A rechtsklickt B, B rechtsklickt A -> Vertrag in A's Hand bindet B als Verschuldeten. Roter Stoff, Spiralen, Glocke. |
| `merchant_crest` | Mit Vertrag in der anderen Hand: Teleport zum Verschuldeten und zurueck. Im Inventar: saugt Leben vom nahen Verschuldeten. Beute in Bastion-Schatzkisten. |
| `eternal_seal` (Eternal Pact) | Im Inventar: Verschuldete deiner Vertraege stehen unter Eternal Indenture. |
| `apex_focus` | Zutat fuer den Charter Stone; droppt, wenn ein Charter stirbt. |
| `charter_stone` | Block: errichtet einen Charter (65er-Zone). Nicht-Mitglieder koennen nicht bauen/abbauen/interagieren. |
| `waystone` | Block: erweitert einen Charter um eine 33er-Zone. |
| `charter_vessel` | Block: senkt die Abnutzung (Uses) des Charters. |
| `pact_press` (Pact Vessel) | Block: Contract einlegen -> Verschuldeter wird Charter-Mitglied; Soul-Feuer darunter -> Soul Strain. |
| `swapper` (Soul Sand Swapper) | Block: bei Redstone-Impuls Tausch von Bloecken bzw. bei zwei Pact Vessels der Verschuldeten. |
| `bonefly` / `aylythian_bonefly` | Rechtsklick = rufen/entlassen. Fliegen: WASD, Leertaste = steigen, Sprint = schneller. |
| `soul_mould` | Auf Block rechtsklicken = Waechter. Freunde: `/charter trust <Spieler>`. Sneak+Rechtsklick = einsammeln. |
| `caller_bell` | Ruft das zuletzt benutzte Bonefly (Funktion im Original unbekannt -> Annahme). |
| `benefactor_mark` | Im Inventar: erlaubt das Verlassen der Divine Dominance. |
| `chain_sword` | Viel Schaden. Toedlicher Treffer: Opfer bleibt auf einem halben Herz, wird festgekettet und ist unverwundbar. Naechster Treffer: Lichtstrahl, gelber Burst, lila Wirbel, "X's existence was repurposed". |
| `divine_dominance` | Block platzieren: erzeugt eine quadratische Border (Start-Radius 60), die sich verkleinert. Nur Mark-Traeger kommen raus. Schliesst sie sich, werden alle noch Eingeschlossenen gebannt. |

## Befehle
- `/charter charter` Info zum Charter an deiner Position
- `/charter dominance list` / `cancel <Id>` (Admin, beendet ohne Bann)
- `/charter reload`

## Bekannte Luecken
- Nicht kompiliert/getestet (kein Netzwerk in der Entwicklungsumgebung).
- Animationen sind Partikel + Display-Entity-Interpolation, keine echten Modell-Animationen.
- Boneflies fliegen ueber ein unsichtbares Horse; Flugverhalten ggf. nachjustieren (`bonefly.*` in der config).
- Soul Sand Swapper tauscht keinen Skin; Charter-Owner-Flug des Originals fehlt.
- Eigene Implementierung der Mechaniken (kein Code/Assets aus dem Original-Repo uebernommen, das ist All Rights Reserved).
