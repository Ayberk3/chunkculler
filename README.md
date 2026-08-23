# SubChunkCuller (v1.0.0)

Yüksek Performanslı SubChunk (Mağara/Blok) Gizleyici ve Sıfır Sızıntılı Dikey Anti-ESP Eklentisi (Paper / Purpur 1.20+).

## 🚀 Özellikler
- **SubChunk Culling:** Oyuncunun altındaki mağara subchunk'larını (16x16x16) silerek X-Ray hilelerini ve render yükünü engeller.
- **Zero-Leak Vertical Anti-ESP:** 48 blok altındaki oyuncuları ve yaratıkları hilelerden (ESP/Tracer/Radar) tamamen gizler.
- **Sıfır Raycast / Sıfır Lag:** Blok çakışması yapmaz; TPS etkisi $\%0.00$'dır.
- **Sıfır Sızıntı (Zero-Leak):** Gizli entity'lerin `TELEPORT`, `REL_MOVE`, `METADATA`, `EQUIPMENT` ve ses paketlerini tamamen durdurur (Ghost Entity oluşamaz).
- **Hysteresis Buffer:** `48` blokta gizler, `42` bloğa yaklaşınca gösterir (Sınırda zıplarken ekran titremesi ve paket spamı oluşmaz).
- **TAB & Skin Koruması:** `PlayerInfoUpdate` paketlerine dokunmaz; `TAB` eklentisi ve oyuncu listesi bozulmaz.
- **Ok ve Fırlatılabilirler:** Oklar, Ender Pearl'ler ve fırlatılan eşyalar gizlenmez; menzil içi atışlar havada kaybolmaz.
- **Binek Senkronizasyonu:** At ve Bot gibi araçlara binen oyuncular yaklaşıldığında tam senkronize spawn edilir.
- **Tam Uyumluluk:** `GrimAC`, `Vulcan`, `Simple Voice Chat` ile %100 uyumludur.

## ⚙️ Kurulum
1. [Releases](https://github.com/Ayberk3/chunkculler/releases) sayfasından `SubChunkCuller-1.0.0.jar` dosyasını indirin.
2. Sunucunun `plugins/` klasörüne atın.
3. `packetevents` eklentisinin sunucuda kurulu olduğundan emin olun.
4. Sunucuyu başlatın veya `/scc reload` komutunu kullanın.

## 🛠️ Komutlar & Yetkiler
- `/subchunkculler reload` (veya `/scc reload`, `/chunkculler reload`) - Configi yeniler.
- `subchunkculler.admin` - Reload yetkisi.
- `subchunkculler.bypass` - Culling filtresini atlama yetkisi.
