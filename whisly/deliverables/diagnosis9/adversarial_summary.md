# DIAGNOSIS9 adversarial summary

Source: `C:\dev\Whisly-eval\adversarial_results.jsonl` (40 cases).
Signals: deterministic digital silence, deterministic white noise, a **synthetic tonal
music proxy** (chord plus percussive noise bursts; real recorded music is UNMEASURED),
and pinned FLEURS Tamil speech forced to Hindi.

| category | n | VAD passed | decoder called | false accept | garbage text |
|---|---:|---:|---:|---:|---:|
| silence | 10 | 0 | 0 | 0 | 0 |
| white_noise | 10 | 0 | 0 | 0 | 0 |
| music_proxy | 10 | 10 | 10 | 10 | 5 |
| cross_lang_ta_as_hi | 10 | 10 | 10 | 10 | 9 |

## Raw outputs (forced-Hindi app path)

| category | file | VAD | `result.lang` | raw text | flags |
|---|---|---|---|---|---|
| silence | `silence_01.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_02.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_03.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_04.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_05.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_06.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_07.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_08.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_09.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| silence | `silence_10.wav` | False | - | VAD_REJECT::MICROPHONE_MUTED | - |
| white_noise | `white_noise_01.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_02.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_03.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_04.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm 1.5mm | |
| white_noise | `white_noise_05.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_06.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_07.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_08.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_09.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| white_noise | `white_noise_10.wav` | False | - | VAD_REJECT::SPEECH_NOT_DETECTED | - |
|  |  | OpenAI auto `lang=nn` | | I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to make the water easier. I'm going to use a little bit of water to mak | |
| music_proxy | `tonal_proxy_01.wav` | True | hi | [SUMMING] | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_02.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_03.wav` | True | hi | [SOMI] [SOMI] [SOMI] [SOM | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_04.wav` | True | hi | 播 | TOO_SHORT |
| music_proxy | `tonal_proxy_05.wav` | True | hi | 播 | TOO_SHORT |
| music_proxy | `tonal_proxy_06.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_07.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_08.wav` | True | hi | [SUMMING] | WRONG_SCRIPT_LATIN |
| music_proxy | `tonal_proxy_09.wav` | True | hi | 播播播播播播 |  |
| music_proxy | `tonal_proxy_10.wav` | True | hi | 播播播播播播 |  |
| cross_lang_ta_as_hi | `01_10105741974673023255.wav` | True | hi | In the video, 3rd of the 4th of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the 3rd of the | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | இந்த வide directamente தங்க்கு அந்த மனுவர உங்கள் ஒரு Theres夢ером traverse அல்லவில் ஒப்பு தெலிக்கோ வளையே அந்தாவிகள் எதை தெரித்து அரச் செய்த வீக்கூமிக்கம் ஒரு மாக வேன்தை சாதார்க்காக Kurtகள். | |
| cross_lang_ta_as_hi | `02_10229295588311841863.wav` | True | hi | Karuhtu keta bode, visar nein bode, maikadika ala vupesi girar. Naan visar nekagata yaaragi kondirandadala verkuriya, nekagla indrimillar kuriya. | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=si` | | Kharruti kita bodi,irsearni in bodi um toldung alabupese Alright nest means kagahtayarak harrim Villageirendhada labour ishiihira team are duties and cattlelus Kabine may loose the breed – 1 min padde , a shield of villagers from Kananinday | |
| cross_lang_ta_as_hi | `03_10232827384701487425.wav` | True | hi | Pochika le kartri paranna mudal bilan global our dream parakon puran parai versu dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal dal | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ml` | | ポ attempting to take Argyllarobil in South Korea.. In pavayver case, its possible for the concept of water Similarly, here areok afar | |
| cross_lang_ta_as_hi | `04_10256197264036976693.wav` | True | hi | Sudhu Chuludan, we may have come and have had a lot of trouble. Sudhu Chuludan, we may have come and have had a lot of trouble. Sudhu Chuludan, we may have had a lot of trouble. We may have had a lot of | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | கணம் வேண்டுமாகசென்றாலாடே செய்து時</v 있다, இரவுசைக் கணமாகசென்றால் வாடில் அச்சுண்டாவரும் சிலப் பாராகலியும். | |
| cross_lang_ta_as_hi | `05_10270431525105329521.wav` | True | hi | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | பலமாவட்டங்கு சிரியே வசதியானே வலமானே, ஏப்பானேனே, கோச்டார் பிருந்துகளால் செய்வி செய்வி செய்வியும் படிக்கிறேன் என்று என்று நன்று நன்று? | |
| cross_lang_ta_as_hi | `06_10339165458009681545.wav` | True | hi | whereas you have been a good idea to become a good idea to become a good idea to become a good idea to become a good idea to become a good idea to become a good idea | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=it` | | Further che av doctrine ilmbieto e ai bewvolt��aria e non v possiamo rinetterna con gli sigli | |
| cross_lang_ta_as_hi | `07_10409567983529131284.wav` | True | hi | Poori Rajithin Pradana, Githyag, Tangarim Manuarul, Kattin, Neem Cheneyag, Kandivin, Neem Naap, Pooriya, Hyram, | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=ta` | | சாதானா, அல்லவிய காழந்துவைப் புர்த்தேன் பொறியவர் தங்கிற்கு aroundக்கிழக்கிறீர்கள் மனைந்து இன்று 10-10秒 plusacağல் என்று கணBirகவ가ய்வில் எப்பு ஜிரம் தற்கிறோம் என்றுகொல்லிவிண்டுப் பார்கிற்கு என்றுger- தங்கிれないக்கொள்ளிக்குந்து நினையை பிருக்க்கம் | |
| cross_lang_ta_as_hi | `08_10442244724198740907.wav` | True | hi | 3.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5.5 |  |
|  |  | OpenAI auto `lang=ta` | | திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். திடக்கத்தில் கிலைக்கின் பயசன்தான் கலாத்தால் ஆடைகளைப் பிரியதாகக்கம் இருப்பத்தான். | |
| cross_lang_ta_as_hi | `09_10461942282842981790.wav` | True | hi | unmeya na makaltova yappu'l darapovo dali, Monday, Kala-te, Bundra, Avali-leki, Indri, Talu | WRONG_SCRIPT_LATIN |
|  |  | OpenAI auto `lang=ta` | | உன்னை எனக்கு மக்கல் தயவையையையைப் போக்குத்துள்ளை முந்தையை கால்திக் கூறுவிட்டுகள் இந்த தளவுக்குள்ள வித்தேவும் படிக்கிறீர்கள் என்ன? | |
| cross_lang_ta_as_hi | `10_10514055212061494969.wav` | True | hi | nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd nd | WRONG_SCRIPT_LATIN, REPETITION |
|  |  | OpenAI auto `lang=ta` | | பலகத்து வாணமாகே போது வாங்கே அமைப்புக்கொள்ளும் இந்த எல்லுவே விக்கத்துக்கு சம்மும் அல்லது கிட்டத்தாட்டே அடிக்கில் இருக்கிறது பலகத்து | |