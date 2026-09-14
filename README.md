# GrzybcioRynek

Plugin rynku graczy (Auction House) na Paper/Spigot **Minecraft 26.2**.

Gracze wystawiają przedmioty z ręki, przeglądają oferty w GUI i kupują je za walutę serwerową (Vault). Wspiera custom itemy Nexo i Oraxen oraz opcjonalnie PlaceholderAPI.

**Wersja pluginu:** 1.0.3  
**Autor:** Grzybcio  
**Licencja:** Apache License 2.0

---

## Wymagania

| Zależność | Status |
|-----------|--------|
| Paper / Spigot **26.2** | wymagane |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) + plugin ekonomii | wymagane |
| Nexo | opcjonalnie |
| Oraxen | opcjonalnie |
| PlaceholderAPI | opcjonalnie |

Bez Vault i providera ekonomii plugin się nie uruchomi.  
Plugin **odmawia startu** na serwerze innym niż **Paper 26.2**.

---

## Instalacja

1. Zainstaluj Vault oraz ekonomię (np. EssentialsX Economy).
2. Wrzuć `GrzybcioRynek-1.0.3.jar` do `plugins/`.
3. Uruchom serwer.
4. Skonfiguruj `plugins/GrzybcioRynek/config.yml` i `gui.yml`.

---

## Funkcje

- GUI rynku z kategoriami, wyszukiwaniem, sortowaniem i paginacją
- Moje oferty, ulubione, historia zakupów/sprzedaży, statystyki
- Profil sprzedawcy i system ocen (1–5)
- Skrzynka odbiorcza (wygasłe / niedostarczone itemy)
- Alerty cenowe
- Prowizja rynku, anty-abuse, bezpieczne transakcje (ID + logi)
- Podpowiedzi cen na podstawie historii
- Placeholdery PlaceholderAPI
- Publiczne API dla innych pluginów (`MarketAPI`)

---

## Komendy

| Komenda | Opis |
|---------|------|
| `/market`, `/rynek`, `/ah` | Otwiera rynek |
| `/market sell <cena>` | Wystawia przedmiot z ręki |
| `/market my` / `moje` | Twoje aktywne oferty |
| `/market search <fraza>` / `szukaj` | Wyszukiwanie |
| `/market favorites` / `ulubione` | Ulubione oferty |
| `/market history` / `historia` | Historia transakcji |
| `/market stats` | Statystyki rynku |
| `/market mailbox` / `skrzynka` | Skrzynka odbiorcza |
| `/market alerts` / `alert` | Alerty cenowe |
| `/market remove <id>` | Usuwa ofertę |
| `/market reload` | Przeładowuje konfigurację |
| `/market admin …` | Narzędzia administracyjne |
| `/wystaw <cena>` | Skrót do wystawienia |
| `/aukcje` | Alias otwarcia GUI |

### Admin

```text
/market admin list
/market admin inspect <id>
/market admin remove <id>
/market admin forceexpire <id>
/market admin refund <transaction-id>
/market admin reload
```

---

## Uprawnienia

| Uprawnienie | Opis | Domyślnie |
|-------------|------|-----------|
| `market.use` | Korzystanie z rynku | wszyscy |
| `market.sell` | Wystawianie ofert | wszyscy |
| `market.reload` | Reload configu | OP |
| `market.admin` | Zarządzanie rynkiem | OP |

---

## Konfiguracja

```text
plugins/GrzybcioRynek/
├── config.yml       # limity, prowizja, wiadomości, anty-abuse
├── gui.yml          # układ slotów GUI
├── listings.yml     # aktywne oferty (generowane)
└── market-data.yml  # historia, mailbox, ulubione, alerty, oceny
```

Wybrane sekcje `config.yml`:

- `settings` — limity ofert, wygasanie, min/max cena  
- `market-fee` — prowizja od wystawienia i sprzedaży  
- `history` — retencja historii transakcji  
- `favorites` / `price-alerts` / `mailbox` / `ratings`  
- `anti-abuse` — cooldown i limity transakcji  
- `messages` — wszystkie komunikaty i teksty GUI  

`/market reload` wymaga `market.reload` lub `market.admin` i dopisuje brakujące domyślne wiadomości.

---

## PlaceholderAPI

Szczegóły: [PLACEHOLDERS.md](PLACEHOLDERS.md)

Przykłady: `%grybciorynek_active_listings%`, `%grybciorynek_player_listings%`, `%grybciorynek_market_volume%`

---

## Budowanie

Wymaga JDK **25** (Paper 26.2).

```bash
# Windows
.\gradlew.bat jar

# Linux / macOS
./gradlew jar
```

Artefakt: `build/libs/GrzybcioRynek-1.0.3.jar`

Gotowy JAR możesz też pobrać z zakładki **Releases** na GitHubie (po opublikowaniu).

---

## Licencja

Projekt na licencji [Apache License 2.0](LICENSE).
