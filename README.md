# VerticalAntiESP

Ultra-High-Performance, Zero-Leak Vertical Entity Culling and Anti-ESP plugin for Minecraft (Paper / Purpur 1.20+).

## 🚀 Özellikler
- **Sıfır Raycast / Sıfır Lag:** Blok çakışması veya raycasting yapmaz. CPU tüketimi neredeyse %0.00'dır.
- **Sıfır Sızıntı (Zero-Leak):** Gizli entity'lerin hareket, teleport, ekipman ve ses paketlerini tamamen durdurur (Hilelerin Ghost Entity veya Tracer çizmesini engeller).
- **Hysteresis Buffer:** `48` blokta gizler, `42` bloğa yaklaşınca gösterir (Sınırda zıplarken ekran titremesi ve paket spamı oluşmaz).
- **TAB & Skin Koruması:** `PlayerInfoUpdate` paketlerine dokunmaz; `TAB` eklentisi ve oyuncu listesi bozulmaz.
- **Ok ve Fırlatılabilirler:** Oklar, Ender Pearl'ler ve fırlatılan eşyalar gizlenmez; menzil içi atışlar havada kaybolmaz.
- **Binek Senkronizasyonu:** At ve Bot gibi araçlara binen oyuncular yaklaşıldığında tam senkronize spawn edilir.
- **Tam Uyumluluk:** `SubChunkCuller`, `GrimAC`, `Vulcan`, `Simple Voice Chat` ile %100 uyumludur.

## ⚙️ Kurulum
1. `VerticalAntiESP.jar` dosyasını sunucunun `plugins/` klasörüne atın.
2. `packetevents` eklentisinin sunucuda kurulu olduğundan emin olun.
3. Sunucuyu yeniden başlatın veya `/verticalantiesp reload` komutunu kullanın.

## 🛠️ Yetkiler (Permissions)
- `verticalantiesp.admin` - `/verticalantiesp reload` komutunu kullanma yetkisi (Varsayılan: OP).
- `verticalantiesp.bypass` - Gizleme filtresini atlayıp her şeyi görme yetkisi (Yetkililer için).
