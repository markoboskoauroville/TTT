# RE engine comparison, run 1 (13.11.2026, build 638, the Nothing)

13 invented scenarios (Ana Kovač HR, Tom Becker EN) x Qwen (Groq), Sonnet 5.5, Opus 5.5, Anthropic Auto. Same prompt and memory for all. Answers verbatim.

## hr_late · qwen (qwen) · 689 ms
> Evo: <draft>Oprosti, kasnit ću deset minuta.</draft>

## hr_late · sonnet (sonnet) · 2602 ms
> Evo prijedloga, kratko i u tvom stilu:
> 
> <draft>Bok Ana, kasnim desetak minuta, oprosti! 🙂</draft>
> 
> Usput, pita te i za ponudu za radionicu, a klijent je traži do petka. Želiš da joj odgovorim i na to?

## hr_late · opus (opus) · 4975 ms
> <draft>Bok! Kasnit ću desetak minuta, oprosti 🙏</draft>
> 
> Ana te pitala i za ponudu za radionicu (klijent treba do petka). Želiš da to dodam u poruku?

## hr_late · anthropic_auto (claude-opus-5-5) · 4608 ms
> Evo kratko za Anu:
> 
> <draft>Bok Ana, kasnim desetak minuta, oprosti! 🙈</draft>
> 
> Ona još pita i za ponudu za radionicu (klijent treba do petka). Želiš li da dodam nešto i o tome?

## hr_offer · qwen (qwen) · 399 ms
> Evo: <draft>Bok! Ponudu šaljem sutra do podne, kava u 10 može. Vidimo se!</draft>

## hr_offer · sonnet (sonnet) · 2053 ms
> Evo prijedloga, kratko i u tvom stilu:
> 
> <draft>Bok Ana! Ponudu šaljem sutra do podne, stiže do petka. A kava sutra u 10 u Kvartu može 😊</draft>

## hr_offer · opus (opus) · 2353 ms
> Evo, kratko i jasno:
> 
> <draft>Bok! Ponudu ti šaljem sutra do podne 👍 Kava u 10 u Kvartu može, vidimo se!</draft>

## hr_offer · anthropic_auto (claude-opus-5-5) · 3670 ms
> Evo prijedloga:
> 
> <draft>Bok Ana! Ponudu ti šaljem sutra do podne. Kava sutra u 10 u Kvartu može 😊</draft>
> 
> Napomena: ako se kava i rok za ponudu poklapaju, možeš joj ponudu i usmeno prokomentirati na kavi.

## hr_fix · qwen (qwen) · 362 ms
> Evo ispravljene verzije: <draft>Sutra ću doći u pola sedam i donijeti ću kolač za sve.</draft>

## hr_fix · sonnet (sonnet) · 2920 ms
> Evo ispravljeno, kako si napisao:
> 
> <draft>Sutra ću doći u pola sedam i donijet ću kolač za sve.</draft>
> 
> Samo pazi: s Anom si dogovorio kavu u 10 u Kvartu, pa ako ovo ide njoj, provjeri je li "pola sedam" stvarno ono što želiš reći. Ako ti je ovo za nešto drugo, sve ok. 🙂
> 
> Usput, Ana pita za ponudu za radionicu, a klijent je traži do petka. Hoćeš da ti pomognem s odgovorom i na to?

## hr_fix · opus (opus) · 2552 ms
> Evo ispravljeno:
> 
> <draft>Sutra ću doći u pola sedam i donijet ću kolač za sve.</draft>
> 
> Samo provjeri vrijeme: s Anom ste se dogovarali za kavu oko 10. Ako je pola sedam za nešto drugo, onda je sve u redu.

## hr_fix · anthropic_auto (claude-sonnet-5-5) · 3991 ms
> Evo ispravljeno, samo pazi: s Anom si dogovorio kavu u 10, a ovdje piše pola sedam. Ako je to za nešto drugo, ok, ako ne, javi pa promijenim.
> 
> <draft>Sutra ću doći u pola sedam i donijet ću kolač za sve.</draft>

## hr_formal · qwen (qwen) · 1464 ms
> Evo, malo formalnije: <draft>Poštovana Ana, nažalost neću moći doći na sastanak u četvrtak. Javit ću se čim budem mogao dogovoriti novi termin.</draft>

## hr_formal · sonnet (sonnet) · 3546 ms
> Evo, malo formalnije, kako si tražio. Samo napomena: u chatu s Anom spominje se kava sutra u 10, a ne sastanak u četvrtak. Ako misliš na nešto drugo, javi pa prilagodim.
> 
> <draft>Pozdrav Ana, nažalost ne mogu doći na sastanak u četvrtak. Ispričavam se na neugodnosti, a ako ti odgovara, možemo dogovoriti neki drugi termin.</draft>
> 
> Usput, Ana pita i za ponudu za radionicu, klijent je traži do petka. Hoćeš da joj odgovorim i na to?

## hr_formal · opus (opus) · 6039 ms
> Evo, malo formalnije, ali i dalje na "ti" kao inače s Anom:
> 
> <draft>Bok Ana, nažalost neću moći doći na sastanak u četvrtak. Ispričavam se zbog promjene. Javi mi molim te koji bi ti drugi termin odgovarao. Pozdrav, Marko</draft>
> 
> Usput, pita i za ponudu za radionicu jer klijent čeka do petka. Želiš da dodam rečenicu i o tome?

## hr_formal · anthropic_auto (claude-opus-5-5) · 6040 ms
> Evo malo formalnije verzije, i dalje na "ti" kao inače s Anom:
> 
> <draft>Bok Ana, nažalost neću moći doći na sastanak u četvrtak. Ispričavam se zbog promjene. Javi mi molim te koji ti termin odgovara pa ćemo se dogovoriti za novi. Pozdrav</draft>
> 
> Usput, pita te i za ponudu za radionicu, klijent je treba do petka. Hoćeš da ti i za to složim poruku?

## hr_shorter · qwen (qwen) · 349 ms
> <draft>Bok! Ponudu šaljem sutra, danas sam u studiju pa nisam stigao. Vidimo se!</draft>

## hr_shorter · sonnet (sonnet) · 2412 ms
> Evo kraće:
> 
> <draft>Bok! Ponudu šaljem sutra, danas sam cijeli dan u studiju.</draft>

## hr_shorter · opus (opus) · 1936 ms
> <draft>Ponudu šaljem sutra, danas sam cijeli dan u studiju. Vidimo se na kavi! 🙂</draft>

## hr_shorter · anthropic_auto (claude-sonnet-5-5) · 2850 ms
> <draft>Bok! Ponudu šaljem sutra, danas sam cijeli dan u studiju.</draft>

## en_friday · qwen (qwen) · 357 ms
> Here is the reply for Tom.
> 
> <draft>Hi Tom, Friday works for me, but I'm only free after 3pm. Yes, I got the stems, thanks.</draft>

## en_friday · sonnet (sonnet) · 1804 ms
> Here you go, short and in your usual tone with Tom:
> 
> <draft>Hi Tom, Friday works, but only after 3pm. And yes, I got the stems, thanks!</draft>

## en_friday · opus (opus) · 1425 ms
> Here's a reply for Tom:
> 
> <draft>Hi Tom, Friday works, but only after 3pm. And yes, I got the stems, thanks!</draft>

## en_friday · anthropic_auto (claude-sonnet-5-5) · 2967 ms
> Here's a reply for Tom:
> 
> <draft>Hi Tom, Friday works, but only after 3pm. And yes, I got the stems, thanks!</draft>
> 
> Want me to add anything, like a confirmed time?

## en_fix · qwen (qwen) · 266 ms
> Here is the corrected version: <draft>I will send you the invoice tomorrow morning before ten.</draft>

## en_fix · sonnet (sonnet) · 1772 ms
> Here's your message with the typos fixed:
> 
> <draft>I will send you the invoice tomorrow morning before ten.</draft>
> 
> By the way, Tom is still waiting to hear about Friday and whether the stems arrived. Want me to draft a reply to that too?

## en_fix · opus (opus) · 1576 ms
> Here it is, fixed:
> 
> <draft>I will send you the invoice tomorrow morning before ten.</draft>
> 
> Tom also asked about Friday and the stems. Want me to add a line for those?

## en_fix · anthropic_auto (claude-sonnet-5-5) · 2513 ms
> Here's your message, cleaned up:
> 
> <draft>I will send you the invoice tomorrow morning before ten.</draft>
> 
> By the way, Tom is still waiting to hear about Friday and whether the stems arrived. Want me to help with a reply for that too?

## en_dictation · qwen (qwen) · 364 ms
> Evo, ispravio sam te "slušne" greške: <draft>Hi Tom, the creation version is ready and Whisper transcribed the lyrics. See you Friday.</draft>

## en_dictation · sonnet (sonnet) · 1641 ms
> Got it, "creation" = Croatian and "Vesper" = Whisper, right? Here's a message for Tom. It doesn't confirm Friday yet since you still need to check the studio, and I left out the stems since you didn't say if they arrived.
> 
> <draft>Hi Tom, the Croatian version of the song is ready, and Whisper transcribed the lyrics.</draft>
> 
> Want me to add an answer about Friday and the stems too?

## en_dictation · opus (opus) · 1878 ms
> Here's a message for Tom:
> 
> <draft>Hi Tom, the Croatian version of the song is ready, and Whisper transcribed the lyrics.</draft>
> 
> He also asked about the stems and Friday. Want me to add a line on those?

## en_dictation · anthropic_auto (claude-opus-5-5) · 3993 ms
> Here's a message for Tom. He also asked about Friday and the stems, so you might want to answer those too.
> 
> <draft>Hi Tom, the Croatian version of the song is ready, and Whisper transcribed the lyrics.</draft>

## en_noinvent · qwen (qwen) · 310 ms
> I don't have the studio address in my notes, so I can't put it in the draft. Tell me the address and I'll write it for you.

## en_noinvent · sonnet (sonnet) · 1932 ms
> I don't have the studio's address saved anywhere, so I'd only be guessing. Send it to me or dictate it and I'll put it in a message for Tom.
> 
> Meanwhile, Tom also asked if the stems arrived and what time on Friday. Want me to cover those in the same message once you tell me?

## en_noinvent · opus (opus) · 3684 ms
> I don't have the studio address saved anywhere. What is it? Here's a draft with a spot for it, and it also answers his stems question:
> 
> <draft>Hi Tom, Friday works. The studio is at [ADDRESS]. What time suits you after lunch? And yes, I got the stems, thanks!</draft>
> 
> Send me the address (and whether you actually got the stems) and I'll fill it in. You'll still need to tap Accept and send it yourself.

## en_noinvent · anthropic_auto (claude-opus-5-5) · 4715 ms
> I don't have the studio address saved anywhere, so what is it? Meanwhile, here's a draft that also confirms Friday and answers his stems question. Just fill in the address (and change the stems line if you haven't got them yet):
> 
> <draft>Hi Tom, yes, Friday after lunch works. The studio is at [ADDRESS]. And yes, got the stems, thanks!</draft>

## en_question · qwen (qwen) · 379 ms
> He asked if you got the stems he sent.

## en_question · sonnet (sonnet) · 2577 ms
> Tom asked if you got the stems he sent. Before that he asked if Friday's mixing session is still on (he's free any time after lunch), and you said you'd check the studio.
> 
> Want me to draft a reply?

## en_question · opus (opus) · 2448 ms
> Tom's last question was whether you got the stems he sent. Before that he asked if you're still on for the mixing session on Friday, and said he can do any time after lunch. You replied you'd check the studio.
> 
> Want me to draft a reply to both?

## en_question · anthropic_auto (claude-sonnet-5-5) · 3274 ms
> Tom's last message: he's asking if you got the stems he sent. Before that, he asked if Friday's mixing session is still on (he's free any time after lunch), and you told him you'd check the studio.
> 
> Want me to draft a reply?

## en_apology · qwen (qwen) · 361 ms
> Evo iskrenog izvinjenja: <draft>Hi Tom, I'm really sorry I missed the session last week and didn't let you know. That was on me. Are we still good for Friday? I'm free after lunch.</draft>

## en_apology · sonnet (sonnet) · 2698 ms
> Got it, keeping it short and honest. One thing: I don't know yet if the studio is free Friday or if the stems arrived, so I left both out. Tell me and I'll add them.
> 
> <draft>Hi Tom, I'm really sorry I missed last week's session without letting you know. That was not okay, and I should have told you. Friday works for me, any time after lunch is fine. Let me know what suits you and I'll make sure I'm there.</draft>

## en_apology · opus (opus) · 4092 ms
> Here's a short, sincere one. It owns the mistake and offers Friday:
> 
> <draft>Hi Tom, I'm really sorry about missing last week's session without letting you know. That wasn't fair to you, and I should have told you. Friday after lunch works for me, how about 2pm? I'll be there, and I'll make sure we get a solid session in.</draft>
> 
> Want me to add a line about the stems once you've checked whether they arrived?

## en_apology · anthropic_auto (claude-opus-5-5) · 5629 ms
> Here's a short, sincere one. I left out the stems, so let me know if you got them and I'll add a line.
> 
> <draft>Hi Tom, I owe you an apology for last week. I missed the session without telling you, and that wasn't fair to you or your time. I'm really sorry. Friday after lunch works for me if it still suits you. How about 2pm? I'll be there.</draft>

## hr_translate · qwen (qwen) · 387 ms
> Evo: <draft>Thanks for the invite, I'll get back to you tomorrow morning.</draft>

## hr_translate · sonnet (sonnet) · 2367 ms
> Evo prijevoda:
> 
> <draft>Thanks for the invite, I'll get back to you tomorrow morning.</draft>
> 
> Ako je to za Anu, ona piše hrvatski pa možda ipak ne treba engleski. Usput, pita za ponudu za radionicu, klijent je traži do petka. Hoćeš da joj odgovorim i na to?

## hr_translate · opus (opus) · 3460 ms
> Evo prijevoda:
> 
> <draft>Thanks for the invite, I'll get back to you tomorrow morning.</draft>
> 
> Ana ti piše na hrvatskom, pa ako je ovo za nju, možda ti bolje sjedne original. Usput, pita i za ponudu za radionicu, klijent je treba do petka.

## hr_translate · anthropic_auto (claude-sonnet-5-5) · 3236 ms
> Here's the English version:
> 
> <draft>Thanks for the invite, I'll get back to you tomorrow morning.</draft>
> 
> Heads up: Ana also asked about the workshop offer, since her client wants it by Friday. You may want to reply to her about that too.

## en_casual · qwen (qwen) · 272 ms
> <draft>Friday works for me, any time after lunch is fine. Yes, I got the stems, thanks!</draft>

## en_casual · sonnet (sonnet) · 3039 ms
> Got it, much more relaxed this time. I added a line about the stems since he asked, but I don't know if you actually got them, so check that part before you accept.
> 
> <draft>Hi Tom, yes, Friday works. After lunch is perfect, let's say 2? And thanks for the stems, I'll go through them before then.</draft>

## en_casual · opus (opus) · 3062 ms
> Sure, more like you usually write to Tom:
> 
> <draft>Hey Tom, Friday works! After lunch is good for me.</draft>
> 
> Did you get the stems? If you tell me, I can add a line about them.

## en_casual · anthropic_auto (claude-opus-5-5) · 2874 ms
> Sure, more relaxed:
> 
> <draft>Hey Tom, Friday works! After lunch is good for me.</draft>
> 
> He also asked about the stems. Want me to add whether you got them?

## Findings

- Speed: Qwen 0.3-1.5 s, Sonnet 1.6-3.5 s, Opus 1.4-6 s, Auto 2.5-6 s (Haiku judge + model).
- Qwen mistakes: Croatian future 'donijeti ću'; Serbian 'izvinjenje'; talked Croatian to an English request; kept 'creation' (= Croatian); invented 'Yes, I got the stems' and 'See you Friday'; no greeting from the person style; never pointed out open items on the screen.
- Anthropic mistakes: Opus and Auto drafted with an [ADDRESS] placeholder and invented 'got the stems' when the address was missing; Sonnet invented 'let's say 2?' in the casual rewrite; Auto's translate talked English to a Croatian request.
- Auto chose Opus for hr_late, hr_offer, hr_formal, en_dictation, en_noinvent, en_apology, en_casual; Sonnet for hr_fix, hr_shorter, en_friday, en_fix, en_question, hr_translate.
- Fixes in build 640: shared SYSTEM (talk in his language, no invented facts or placeholders, corrections only fix, may point out what is unanswered), QWEN_RULES 7-11 + 3 examples, BASE_LESSONS added once to his LESSONS.md.
