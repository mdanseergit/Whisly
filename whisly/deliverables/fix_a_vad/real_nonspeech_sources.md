# Real non-speech audio for Whisly VAD evaluation

Generated 2026-11-23. All files live only in this scratch directory; nothing was uploaded.

## How these were sourced

1. Searched Freesound (`https://freesound.org/search/`) with the license filter
   `f=license:"Creative Commons 0"`, separately for music/instrument terms
   (acoustic guitar, xylophone, harmonica, piano, Spanish guitar) and for
   environmental terms (rain, wind, birds, ocean waves, storm, river, forest,
   insects, street construction).
2. For every chosen sound ID, opened the individual Freesound page and confirmed
   it displays **Creative Commons 0** with a link to
   `http://creativecommons.org/publicdomain/zero/1.0/`. The search results also
   carry the same license label.
3. Downloaded the public low-quality preview MP3 directly from
   `cdn.freesound.org` (no login, no API key, no payment). The raw downloaded
   bytes are preserved in `originals/`; SHA-256 is recorded in `manifest.json`.
4. Converted each raw download to a 6-second, 16 kHz, mono, PCM-16 WAV in
   `clips/` (central 6 s window; linear resampling; peak-normalized to 0.9).
   `manifest.json` points to these WAV files and also lists the raw originals.

## Why the license is acceptable

All 20 clips are **CC0-1.0 (Creative Commons Zero v1.0 Universal)**, a
permissive public-domain dedication. CC BY-NC / NC-only material was excluded.

Snippet from each Freesound sound page:

> Creative Commons 0
> You can copy, modify, distribute and perform the sound, even for commercial purposes, all without the need of asking permission to the author.

Snippet from the linked CC0 1.0 Universal legal code
(`https://creativecommons.org/publicdomain/zero/1.0/legalcode.txt`):

> 2. Waiver. To the greatest extent permitted by, but not in contravention of, applicable law, Affirmer hereby overtly, fully, permanently, irrevocably and unconditionally waives, abandons, and surrenders all of Affirmer's Copyright and Related Rights ... (iv) for any purpose whatsoever, including without limitation commercial, advertising or promotional purposes (the "Waiver").

> 3. Public License Fallback. ... Affirmer hereby grants to each affected person a royalty-free, non transferable, non sublicensable, non exclusive, irrevocable and unconditional license ... for any purpose whatsoever, including without limitation commercial, advertising or promotional purposes.

Attribution is recorded in the manifest as a courtesy (Freesound username),
even though CC0 does not require it.

## Source inventory

| Category | ID | Title | Author | Source page | Direct download URL | Original size (bytes) |
|---|---|---|---|---|---|---:|
| music | 508322 | Acoustic Guitar (8).wav | owstu | https://freesound.org/people/owstu/sounds/508322/ | https://cdn.freesound.org/previews/508/508322_1690102-lq.mp3 | 27151 |
| music | 97776 | acoustic guitar with non_standard tuning phrase 8.wav | timcam | https://freesound.org/people/timcam/sounds/97776/ | https://cdn.freesound.org/previews/97/97776_392538-lq.mp3 | 146016 |
| music | 211963 | Acoustic guitar rythm with maracas | maurolupo | https://freesound.org/people/maurolupo/sounds/211963/ | https://cdn.freesound.org/previews/211/211963_3931578-lq.mp3 | 188640 |
| music | 690185 | acoustic_guitar_chords_CGAmEm.mp3 | bender25 | https://freesound.org/people/bender25/sounds/690185/ | https://cdn.freesound.org/previews/690/690185_15041642-lq.mp3 | 77864 |
| music | 19460 | tg_xylophone 4.wav | Tristan | https://freesound.org/people/Tristan/sounds/19460/ | https://cdn.freesound.org/previews/19/19460_1178-lq.mp3 | 108432 |
| music | 501300 | Toy Xylophone | JappeHallunken | https://freesound.org/people/JappeHallunken/sounds/501300/ | https://cdn.freesound.org/previews/501/501300_10865671-lq.mp3 | 118704 |
| music | 701063 | Harmonica East Top Easy Tune | ainst | https://freesound.org/people/ainst/sounds/701063/ | https://cdn.freesound.org/previews/701/701063_6007224-lq.mp3 | 279240 |
| music | 792630 | Harmonica-C random stuff multi-take | Sadiquecat | https://freesound.org/people/Sadiquecat/sounds/792630/ | https://cdn.freesound.org/previews/792/792630_5287430-lq.mp3 | 69168 |
| music | 711243 | Simple bit of Beethoven on the piano | WillTaylor | https://freesound.org/people/WillTaylor/sounds/711243/ | https://cdn.freesound.org/previews/711/711243_5731056-lq.mp3 | 899880 |
| music | 735624 | Sierra Serenade (Spanish guitar) | city11 | https://freesound.org/people/city11/sounds/735624/ | https://cdn.freesound.org/previews/735/735624_92171-lq.mp3 | 566592 |
| noise | 236292 | Rain on window | mffm | https://freesound.org/people/mffm/sounds/236292/ | https://cdn.freesound.org/previews/236/236292_3840145-lq.mp3 | 149160 |
| noise | 276294 | Leaves in wind | Sandermotions | https://freesound.org/people/Sandermotions/sounds/276294/ | https://cdn.freesound.org/previews/276/276294_1402315-lq.mp3 | 744768 |
| noise | 509035 | Birds-singing | ana_sofia_ | https://freesound.org/people/ana_sofia_/sounds/509035/ | https://cdn.freesound.org/previews/509/509035_11086990-lq.mp3 | 313128 |
| noise | 534909 | Ocean Waves 04 | Lucas_Schacht | https://freesound.org/people/Lucas_Schacht/sounds/534909/ | https://cdn.freesound.org/previews/534/534909_7020075-lq.mp3 | 334488 |
| noise | 409403 | Storm from indoors | Monkey Pants | https://freesound.org/people/Monkey%20Pants/sounds/409403/ | https://cdn.freesound.org/previews/409/409403_772539-lq.mp3 | 545136 |
| noise | 510233 | River Stream, Lake of Glacier | Singular_Estudio | https://freesound.org/people/Singular_Estudio/sounds/510233/ | https://cdn.freesound.org/previews/510/510233_2053141-lq.mp3 | 351432 |
| noise | 637559 | forest ambience constant breeze | kyles | https://freesound.org/people/kyles/sounds/637559/ | https://cdn.freesound.org/previews/637/637559_612689-lq.mp3 | 991416 |
| noise | 482264 | Zikaden_Grillen_Summer_Atmo | RepDac3 | https://freesound.org/people/RepDac3/sounds/482264/ | https://cdn.freesound.org/previews/482/482264_1192746-lq.mp3 | 385272 |
| noise | 532696 | City street ambience with construction | gravity_proof | https://freesound.org/people/gravity_proof/sounds/532696/ | https://cdn.freesound.org/previews/532/532696_9771209-lq.mp3 | 1307712 |
| noise | 726522 | Rushing Stream River Water | RyanKingArt | https://freesound.org/people/RyanKingArt/sounds/726522/ | https://cdn.freesound.org/previews/726/726522_11069322-lq.mp3 | 421200 |

## Sources considered but skipped

- Wikimedia Commons API: attempted CC0/PD audio searches; results were mostly
  speech/ pronunciation files or 25–580 MB FLAC/WAV masters, and the API began
  returning HTTP 429 after repeated queries. Skipped (3 attempts: `filetype:audio CC0`,
  `freesound cc0 rain/wind/birds/traffic`, category enumeration).
- Wikimedia "CC0-CH" historical recordings: public domain, but individual FLAC
  files were 47–106 MB. Skipped in favor of small previews.
- Pixabay mirrors on Commons: license is the Pixabay Content License, not CC0.
  Skipped as not clearly CC0/CC BY.
- FSD50K / UrbanSound-style dataset archives: permissive but distributed as
  multi-GB archives; not appropriate for "a few seconds each". Skipped.
- Freesound results with CC BY-NC or unknown license were never downloaded.
- Synthesized/MIDI-like results (synth loops, stylophone tones, generated
  atmospheres) were deliberately avoided in the music category.
