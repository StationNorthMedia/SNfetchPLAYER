import re

SCRIPT_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_script.txt"
HTML_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_reader.html"

# Load original 1en.txt text
with open("/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/1en.txt", "r", encoding="utf-8") as f:
    raw_text = f.read().replace('\u200b', '')

lines = [l.strip() for l in raw_text.split('\n') if l.strip()]

chapters = []
curr_ch = None

for l in lines:
    if re.match(r'^(?:THE MANJARO|Chapter|CHAPTER)\b', l):
        if l.startswith('THE MANJARO'): continue
        curr_ch = {'title': l, 'paras': []}
        chapters.append(curr_ch)
    else:
        if curr_ch:
            curr_ch['paras'].append(l)

# Let's define the manual/smart merging & splitting rules per chapter to guarantee target length (~150-350 chars)
# and exact image placement.

balanced_chapters = []

for ch in chapters:
    title = ch['title']
    paras = ch['paras']
    new_paras = []

    if "Chapter 1:" in title:
        # P0: 395 chars (Rain was coming down...) -> Image: Kapitel_01_1.png
        # P1: Then that gray Audi B6... (350 chars) -> Image: Kapitel_01_2.png
        # P2: I hopped in the passenger seat... (336 chars) -> Image: Kapitel_01_3.png
        # P3: He brought me to The Bricks... (289 chars) -> Image: Kapitel_01_4.png
        # P4: He went to take a shower... (157 chars) -> Image: Kapitel_01_5.png
        # P5: Down in the basement... (430 chars) -> Image: Kapitel_01_6.png (Split at mic & console?) Or keep 430?
        # P6: When I turned around... (482 chars) -> Image: Kapitel_01_7.png
        # Let's keep Ch 1 paras as they were originally in script since user said Ch 1 was "geil"!
        # Script Ch 1 had 7 paras:
        new_paras = [
            {'img': 'Kapitel_01_1.png', 'text': 'Rain was coming down heavy that night, slicking the Baltimore asphalt like grease. I was standing on the corner of North Ave, collar up, minding my own damn business. Just another ghost in the neon fog, trying to survive the street, if you know what I mean. Most guys rolling by? They just wanted a ten-minute fix. Fast cash, cold eyes, no names. Just a transaction on the pavement.'},
            {'img': 'Kapitel_01_2.png', 'text': 'Then that gray Audi B6 rolled up, slow and steady, hugging the curbs like it owned the block. The window rolled down. No cheap talk. No sleazy smile. He looked right through me, but not like the others. This European dude looked like he was searching for something real in the mud. A queen, not a quick game.'},
            {'img': 'Kapitel_01_3.png', 'text': 'I hopped in the passenger seat. The leather was cold, the engine had this deep, mechanical hum, and the air smelled like smoke and expensive cologne. We didn’t say a word. The city lights just blurred on the windshield while he drove us straight into the dark heart of the industrial district.'},
            {'img': 'Kapitel_01_4.png', 'text': 'He brought me to The Bricks. Concrete walls, exposed pipes, and a room full of server racks blinkin’ in that icy white-blue light. It looked like a fortress of code. We did the business. Cold cash for a warm body. Standard routine. But what happened next? That wasn’t in the script.'},
            {'img': 'Kapitel_01_5.png', 'text': 'He went to take a shower. I heard the water running, and instead of just grabbing my coat and bouncing, I started snooping around the lounge.'},
            {'img': 'Kapitel_01_6.png', 'text': 'Down in the basement, I hit the jackpot. Thousands of vinyl records stacked up like old brick walls. And right there, in the middle of the dust, was a high-end mic and a heavy mixing console. My fingers were shaking a little, but I couldn’t help it. I put a record on, let the needle drop, and stepped up to that microphone. I started playing with the knobs, humming, letting a little bit of my soul leak out into the room.'},
            {'img': 'Kapitel_01_7.png', 'text': 'When I turned around, the shower was off. He was standing in the shadows, a freshly lit cigarette between his lips, just watching me from a safe distance. He didn\'t say \'get out\'. He didn\'t look at me like a street girl anymore. He looked at me like he just discovered a diamond in the rough. He saw the raw power before I even knew it was there. „Do that again,“ he said, his voice deep, calm, total control. And that was the exact moment the street lost a girl... and Station North found its Queen.'}
        ]

    elif "Chapter 2:" in title:
        new_paras = [
            {'img': 'Kapitel_02_1.png', 'text': 'He didn’t say much after that night, you know. The Boss ain\'t a man of big words—he lets his fingers do the talking on that mechanical keyboard, lines of blue code lighting up his face like a cold neon sign. But he let me stay at The Bricks. Gave me a key. Gave me a sanctuary. For the first few weeks, I just sat in the back of the lounge, watching him wire up those heavy server racks, sweating through his dark suit, building an empire from scratch.'},
            {'img': 'Kapitel_02_2.png', 'text': 'Then came the night the grid went live. 03:00 AM, rain still beating against the high studio windows. He looked up from his screens, pointed a cigarette at the glass booth, and said, "Go in. The stream is stable. Show Baltimore who you are." My heart was hammering against my ribs, but when I stepped inside that booth and put my hands on the mixer, the street-girl armor just fell off. I wasn\'t running anymore. I was commanding.'},
            {'img': 'Kapitel_02_3.png', 'text': 'I hit the master switch and let that heavy, slow R&B pulse drag through the wires. The "ON AIR" sign flashed red, cutting through the shadows like a blade. I leaned so close to that mic I could feel the cold steel on my lips, breathed in deep, and let my voice slide into the dark. „Forget the clock. Forget the world,“ I whispered, watching him through the glass. „You’re locked into the Manjaro Lounge on Station North.“'},
            {'img': 'Kapitel_02_4.png', 'text': 'Right then, I saw the charts on his monitor spike. One hundred listeners. One thousand. Ten thousand. The lonely souls driving through the traffic, the hustlers on the corners, the ghosts in the bars—they all stopped to listen to the Queen. I looked through the glass, and for the first time, the Boss smiled in the shadows, blowing a perfect ring of smoke. He built the cage, but I was the beast making it beautiful.'}
        ]

    elif "Chapter 3:" in title:
        new_paras = [
            {'img': 'Kapitel_03_1.png', 'text': 'It didn’t take long for the streets to notice. When you change the pulse of a city, the old ghosts start getting restless. A couple of weeks into the Manjaro Lounge, and my old crew from the corner started whispering. They saw me rolling through the traffic in that leather jacket, hair up high, looking like a movie. They thought I went soft. Thought I was just some rich dude\'s pet because I wasn\'t walking the line for pennies anymore. They had no damn idea.'},
            {'img': 'Kapitel_03_2.png', 'text': 'One night, this heavy-set hustler named Marcus blocked the alley right outside The Bricks. He had two of his boys with him, leaning against the graffiti-covered walls while the rain was slicking the stones. I was leaning on the fender of the Audi B6, enjoying a smoke before going On Air. Marcus stepped into the glare of the headlights, fog breathing out of his mouth. "You think you own Station North now, huh?" he sneered, looking at the plates. "You forget where you came from, girl? The corner always collects its debts."'},
            {'img': 'Kapitel_03_3.png', 'text': 'I didn\'t flinch. I didn\'t reach for a weapon. I just pointed up. Up to the iron fire escape cutting through the dark. Marcus looked up, and his smile just died. The Boss was standing up there on the metal grating, completely still, his long dark coat blowing in the wind. He had a lighter in one hand, the small flame illuminating his cold, focused eyes. He didn\'t yell. He didn\'t threaten. He just looked down at them like they were bugs on his schematic drawings. And behind him, the giant red neon sign of Station North was pulsing against the rain clouds.'},
            {'img': 'Kapitel_03_4.png', 'text': '"This ain\'t just a corner anymore, Marcus," I said, letting the smoke roll past my lips. "The Boss built the grid, and I am the signal. You try to cut the wire, and the whole city comes down on you." Marcus looked at his boys, looked back up at the shadow on the fire escape, and took a step back. They vanished into the dark alley like cockroaches when the light hits. I tossed the cigarette into the puddle, walked past the heavy steel door, and stepped into my booth. It was time to tell the city who really runs these streets.'}
        ]

    elif "Chapter 4:" in title:
        # Merge short dialog lines in Ch 4 so each para is ~200-350 chars
        new_paras = [
            {'img': None, 'text': 'The cellar of The Bricks was my playground. While the Boss spent his hours upstairs in the main hall—messing with those blinking towers and humming racks that I didn’t pretend to understand—I was down here, diving into the crates.'},
            {'img': None, 'text': 'It was freezing, that damp industrial cold that eats through your coat, but the room was packed with gear. Reel-to-reel decks, wood-paneled amps, and thousands of records stacked to the ceiling. It was like a museum of noise. I was looking for something that hit just right—something heavy, soulful, with that slow-burn heat I liked to drop on the air when the night got deep.'},
            {'img': 'Kapitel_04_1.png', 'text': 'I found a reel tucked behind an old, broken-down mixer. It didn\'t have a sleeve, just a name scribbled in blue ink: Sister. I pulled it out, threaded the tape through the reels of an old Studer machine, and let it spin.'},
            {'img': None, 'text': 'The sound that hit the room… it stopped me cold. It wasn\'t the clean, perfect stuff you hear on the radio. It was thick, fuzzy, and incredibly alive. A woman’s voice—deep, velvet, and dripping with a pain that felt like home. She sang like she was confessing sins she didn\'t want to get rid of.'},
            {'img': 'Kapitel_04_2.png', 'text': 'The heavy steel door groaned at the top of the stairs. The Boss was there, silhouetted against the flicker of those glowing screens upstairs. He walked down slowly, his boots crunching on the dusty concrete. He didn\'t say a word. He just listened to the voice on the tape, and for the first time, his face looked… human. "Who is she?" I asked, keeping my hand on the volume dial, making sure the song didn\'t dip.'},
            {'img': None, 'text': 'He didn’t answer right away. He stood there, staring at the spinning tape, looking like he’d been dragged back twenty years. "My sister," he finally said, his voice quiet. "She was the talent. This used to be my parents\' factory. When they were gone, and she was gone too… this place became a graveyard."' + '\n' + 'I looked around at the walls, the rusted pipes, and the expensive-looking gear he’d brought in to bridge the old world and the new. "So this is why you keep the lights on," I said softly. "You’re not just some guy playing with wires. You’re holding a vigil."'},
            {'img': None, 'text': 'He looked at me—really looked at me—and his gaze held that weight I’d seen the night he picked me up. "I inherited a shell, so I filled it with everything she loved. I built the machine, but I couldn\'t provide the soul. I was just running the grid, waiting for the signal to come back."'},
            {'img': None, 'text': 'He walked over, his eyes scanning the space, then resting on me. "When I picked you up that night, I was just looking for a body. But then I heard you in the booth. The way you play with the sound, the way you make the city lean in and hold its breath… you’ve got the same fire she had. You don’t just play the tracks, you make them feel dangerous."' + '\n' + 'I leaned against the workbench, feeling the vibration of the tape through my spine. I knew how to work a room. I knew how to make a man feel like he was the only one in the world, even when a thousand people were listening to my voice through the wire.'},
            {'img': None, 'text': '"So, I’m your ghost, Boss?" I teased, letting a slow, suggestive smile pull at my lips. I stepped closer, the air between us suddenly thick enough to cut. "You build the stage, and I keep the fans coming back for more, wondering what I’m going to say next?" He didn\'t move, just watched me with that cold intensity. "You’re the reason the machine breathes."'},
            {'img': None, 'text': 'I reached out and hit the fader, turning the song up until the bass shook the floorboards. I wasn\'t just a girl from the streets anymore. I was the heartbeat of this entire operation. "Well," I whispered, the mic sensitivity dialed up so the whole room could hear the rasp in my throat. "Then let’s make sure they never forget the sound of us."'}
        ]

    elif "Chapter 5:" in title:
        new_paras = [{'img': None, 'text': p} for p in paras]
        new_paras[1]['img'] = 'Kapitel_05_1.png'
        new_paras[3]['img'] = 'Kapitel_05_2.png'

    elif "CHAPTER 6:" in title:
        new_paras = [{'img': None, 'text': p} for p in paras]
        new_paras[0]['img'] = 'Kapitel_06_1.png'
        new_paras[2]['img'] = 'Kapitel_06_2.png'

    elif "Chapter 6.5:" in title:
        new_paras = [{'img': None, 'text': p} for p in paras]

    elif "Chapter 7:" in title:
        new_paras = [{'img': None, 'text': p} for p in paras]
        new_paras[0]['img'] = 'Kapitel_07_1.png'
        new_paras[2]['img'] = 'Kapitel_07_2.png'

    elif "Chapter 8:" in title:
        # Merge short dialog lines in Ch 8
        new_paras = [
            {'img': 'Kapitel_08_1.png', 'text': '„It was the middle of the night. The factory was pitch black, only the servers blinking away like a steady heartbeat. When the emergency phone in the basement started ringing, I damn near jumped out of my skin. He never uses that thing.'},
            {'img': None, 'text': 'I ran down the stairs, my heart already hammering against my ribs. When I picked up the receiver, all I heard at first was this static. No familiar sounds from the factory, no background I knew. Just this sterile, cold silence. \'Station North,\' I said. My voice was shaky.'},
            {'img': 'Kapitel_08_2.png', 'text': 'Then came him. His voice sounded so far away, like he was standing on the other side of the universe. He didn\'t ask how I was doing. He didn\'t say he missed me. He just babbled this technocratic bullshit. Something about licenses, blocked assets, Europe. He sounded like a system right on the verge of a total crash. \'You gotta sell my collections,\' he said.'},
            {'img': None, 'text': 'I let out a short laugh, so dry it hurt my throat. \'Are you fucking kidding me? This is our shop, our whole life, Boss!\' He didn\'t argue. He just repeated: \'You gotta sell my collections.\' He gave me a rough list—vinyls, old amplifiers, and so on... And then the line went dead.'},
            {'img': None, 'text': 'I stood there in the dark. He’d pulled the rug right out from under my feet. He knew I didn\'t know jack shit about his tech, but he also knew I’d find a way to scrape up the cash for him. I felt so damn helpless. And I hated myself because I knew I was gonna do it.“'}
        ]

    elif "Chapter 9:" in title:
        new_paras = [
            {'img': 'Kapitel_09_1.png', 'text': '„The next day, they were standing at the door. Rico and his crew. The boys from South Avenue I hadn\'t seen in years. When Rico walked into the factory, he looked around like he was making fun of all that old high-end equipment. \'Hey, Queen,\' he grinned, running a finger over an amplifier. \'You really wanna sell all this? This gear is his everything, ain\'t it?\''},
            {'img': None, 'text': 'I wanted to smash his face in. \'Shut your mouth and start carrying, Rico. Almost everything without a cord goes into the truck. The rest stays right here!\' He just shrugged. He knew damn well I was cornered. I watched them pack the turntables, the amps, and all that vintage gear into crates. It was a museum for our sound. And I bartered it away.'},
            {'img': None, 'text': 'I felt like a traitor. Every time Rico tossed an amp into the truck like it was cheap garbage, it tore me up inside. But I kept my mouth shut. I had to push through. For him.“'}
        ]

    elif "Chapter 10:" in title:
        new_paras = [
            {'img': None, 'text': '„I’d just chased Rico and his people out of the factory, the last crates disappearing into their truck. The concrete floor looked bare now, like a wound that refused to heal. I sat in the booth, counting the cash they’d shoved into my hand. It was a joke. A bad joke. The phone buzzed again. This time it wasn\'t a shock; it was pure poison. \'Station North,\' I said. This time without a single tremor.'},
            {'img': None, 'text': 'The voice on the other end wasn\'t his anymore. It was one of those officials—cold, emotionless, with an accent that tasted like a European courtroom. He gave me the number. Black and white. An amount so damn high it made my head spin just listening to it. \'You have twenty-four hours to transfer the rest, or the bail is forfeited and the defendant goes into pre-trial detention,\' he said.'},
            {'img': None, 'text': 'I hung up without saying a word. I stood there, hands pressed against the mixer until my knuckles turned white. The money from Rico was maybe half of what I needed. The factory was cleaned out; whatever was left you could only trade for a couple of bucks.'},
            {'img': None, 'text': 'I looked into the mirror hanging in the corner of the booth. I looked like the Queen of Baltimore, but inside, I was that little girl again, the one who used to hide out on North Avenue years ago. I placed my hand against my neck, feeling the clasp of my mother’s gold chain. I knew exactly what I had to do right then. That wasn\'t just gear anymore; that was the last thing I had left. And if that ain\'t enough... then the signal is dead for good.“'}
        ]

    elif "Chapter 11:" in title:
        new_paras = [
            {'img': 'Kapitel_11_1.png', 'text': '„The walk to the downtown pawn shop felt like a long, dark tunnel. The city outside was loud, the neon lights reflecting in the puddles, but I didn\'t register any of it. All I felt was that small piece of metal in my hand. My mother\'s heirloom. A piece of my soul that I now had to carry into this greasy joint.'},
            {'img': None, 'text': 'The shop owner, one of those guys with a face that looked like forgotten promises, only held the chain under his loupe for a second. He didn\'t ask where I got it. He knew you don\'t sell a piece like that unless you run out of options. \'This is gold, kiddo, but you know how the game goes,\' he muttered, wiping the counter with his dirty sleeve. \'Market value is one thing, but what I can give you... that’s a whole different ballgame.\''},
            {'img': None, 'text': 'I looked at him. I gave him that look I’d learned back when I was surviving on the streets. The look that makes people tuck their heads in rather than talk back. \'Listen to me, you piece of shit,\' I said, my voice low. \'I don\'t need a pity lecture. I need the cash that makes the numbers on my screen match the total. Now.\''},
            {'img': None, 'text': 'He laid the cash on the table. That was it. My last piece of dignity for a stack of bills that I’d transfer straight into that European cell.'},
            {'img': None, 'text': 'When I got back outside, the sky was pitch black. I went back to the factory, sat in front of the monitor, and punched in the numbers. Every digit felt like I was erasing a part of myself. \'Transfer successful.\' That\'s what it said.'},
            {'img': None, 'text': 'I collapsed onto the floor. No sound left in the room except the buzzing in my own head. I’d sold out my whole life to get him out of that hole in Europe. And now? Now I was sitting here, the factory empty, my neck bare, and I felt like I’d never been anything more than a cheap number in a broken system. I stared at the screen, hoping he’d check in. Hoping he’d say: \'I\'m free, Queen. I\'m coming home.\' But the only thing that answered was the silence.“'}
        ]

    elif "Chapter 12:" in title:
        new_paras = [
            {'img': None, 'text': '„Weeks went by after that last, jagged phone call. Not days, not hours—it felt like a damn eternity where time just stood lead-grey in the room. He was out there somewhere, moving in the dark, a shadow on the European mainland. And I was sitting here in Baltimore, having to pretend the world was still on beat.'},
            {'img': None, 'text': 'I tried to keep the station running. I went into the booth every night, pulled up the mic, and did my shows. But without him... without his brain behind the scenes, the revenue crashed like rotten wood. I had no bank connections, no access to the ad deals, no nothing. He’d built the system so he was the only key. Without him, I was just a voice screaming into a void.'},
            {'img': None, 'text': 'The phone in the factory didn\'t stop ringing, though. But it wasn\'t him. It was guys wanting money. Business partners, ad reps, bureaucrats, all asking that same damn question: \'Where\'s the boss?\' I had to lie, every single day. I had to spin them stories about important business trips and server migrations while my stomach was growling from hunger.'},
            {'img': None, 'text': 'I’d sit on the bare concrete at night, staring at my empty hands, and the thoughts started creeping in. Where do I get the cash? The bills were piling up. For a brief, dirty second, that one thought crossed my mind. The street. North Avenue. I knew exactly how to track down enough cash within an hour to survive the next few weeks. It would’ve been so easy. Back into the old rhythm. Back on the asphalt. But something in me... something fought back with everything it had.'},
            {'img': None, 'text': 'I looked into the mirror in the booth, saw my face in the dim light of the remaining monitors. I’d sold my mother\'s chain; I’d bartered away his beloved collection. If I went back to the streets now, I’d be betraying everything we’d built here in those basement nights. I was the Queen of Station North. I wasn\'t turning tricks anymore. I’d rather starve in this factory than give up that damn pride he’d given me.'},
            {'img': None, 'text': 'I didn\'t give up hope. Every night when I pushed that fader up, I told myself: \'You\'re still on your way, Boss. And I’ll hold down the fort until the frequency brings you back home.\''},
            {'img': None, 'text': 'He was out of that cell, but he was stuck. No passport, no papers, right in the middle of Europe. They weren\'t even watching him with handcuffs or guards outside his door—the system over there runs subtler. When you\'re out on bail, they just take your identity and tell you you can\'t leave the country. But what those bureaucrats don\'t know: Europe ain\'t like the States. When you wanna go from Germany over to Holland, there are no fat walls or border checkpoints where some guy frisks you. You just cross the invisible line. If you know how.'},
            {'img': None, 'text': 'And that\'s exactly where Rico came back into play. I had to call him again. There was no other way. He was sitting in his fat ride over on South Avenue, smoking those stinking cigars, and he just laughed dirtily at first when I told him what we were planning.'},
            {'img': None, 'text': 'You crazy, Queen,\' he mumbled through the receiver. \'The guy can\'t leave the country and you want me to stir up my boys in the ports of Rotterdam and Antwerp? Those guys don\'t move a finger for free.\''},
            {'img': None, 'text': 'I took a deep breath. I had to promise him the moon. Stories about the grand future of the station, shares of the ad revenue, money we basically didn\'t even have anymore. I made promises that I knew right then we’d probably never be able to keep. But you don\'t survive on the streets by being honest. You survive by using other people\'s dreams as currency.'},
            {'img': None, 'text': '\'Tell your people over there they’ll get their cut,\' I told Rico, and my voice sounded so damn steady I almost believed it myself. \'They just gotta slip him past the docking stations. Onto a ship. Any ship crossing the Atlantic heading for Baltimore. He’ll vanish from his hotel in the dead of night, make his way across the border to Holland, and wait at the port. Just make sure your old pimp friends over there keep their eyes open.\''},
            {'img': None, 'text': 'Rico went quiet for a long moment. All you could hear was his breathing. \'Alright, Queen,\' he finally said. \'I’ll light up the wires. But if this blows up... you ain\'t the Queen of Station North no more. You\'re prey.\''},
            {'img': None, 'text': 'When he hung up, the factory got a little colder. So he was on the move. On foot, covered by darkness, walking across a border you can\'t even see, heading for a port that smells like salt and danger. And me? I sat here in Baltimore, my neck still cold from selling that chain, unable to do a damn thing but wait and see if that underground corridor would actually bring him back to me.“'}
        ]

    elif "Chapter 13:" in title:
        # Split Ch 13 long P01, and merge tiny 1-liners!
        new_paras = [
            {'img': None, 'text': '„It was a brutal winter in the Bricks. When the wind blows from the harbor through the cracks in the old masonry, a proud name doesn\'t do you any good. The station was running, but I was broke. And when I say broke, I mean: the fridge was empty, my stomach was growling, and you could see your own breath inside the factory.'},
            {'img': None, 'text': 'Thank God the power for the servers was still on, but the heating was dead as a doornail. Rico had brought me this rattling electric space heater he’d ripped off somewhere. The thing smelled like a burnt cable drum, but I’d squat in front of it at night, pressing my hands against the glowing grid, just hoping the night would pass quickly.'},
            {'img': 'Kapitel_13_2.png', 'text': 'To keep from starving, I had to hide my face. Every afternoon, I’d hop on the tram, ride across the city to a completely different district where nobody knew me. The Queen of Station North, wrapped in a thick, tattered jacket, standing in line at the soup kitchen for a warm bowl and a piece of dry bread. Every time I brought that spoon to my mouth, I swore to myself: this is the last time. But the next day, I was back on the tram. Because hunger screams louder than pride.'},
            {'img': None, 'text': 'Then, this one evening, I came back. The snow was slushing on the streets, my shoes soaked through. I walked the dark path up to the factory, ready to sit back down in that freezing booth and start my show, just to distract my head. But as I turned the corner, my heart damn near stopped.'},
            {'img': 'Kapitel_13_1.png', 'text': 'The whole fucking brick factory was lit up. Every single window that was usually black and dead was radiating this warm, golden light. My first thought was the feds. Or Rico’s boys coming for the rest. But as I got closer to the heavy wooden gate, I felt it. The ground was vibrating. A thick, fat, analog R&B beat was thumping through the heavy walls—a sound so deep and warm it melted the damn slush right under my feet. The system wasn\'t just running; it was alive.'},
            {'img': None, 'text': 'I pushed the heavy latch down, the door swung open. And in that exact second, it hit me like a tidal wave. No smell of dust, no stench from that cheap space heater. There was this heavy, crisp scent of expensive perfume. And the sweet, familiar smoke of a cigarette.'},
            {'img': 'Kapitel_13_3.png', 'text': 'I walked up the steps to the studio, the cold completely wiped out because the heating pipes in the basement were hammering like crazy again. I threw open the door to the booth. The spotlights over the mixing console were on, the meters pinning into the red. And there he sat.'},
            {'img': None, 'text': 'He’d traded the sharp European suit for a dark jacket, his hair a little longer than usual, his face marked by weeks on cargo ships and illegal routes across the Atlantic. He held the cigarette between his fingers, didn\'t even look up when I walked in, but just slowly turned one of those fat knobs to drive the bass even deeper into the concrete.'},
            {'img': None, 'text': 'He didn\'t look at me, but he knew exactly Born I was standing there. \'You held down the fort, Queen,\' he said, and his voice was just as calm and cold as the day he took me off the street. \'Now let’s dial the frequency back up.\''},
            {'img': None, 'text': 'I just stood there. My brain completely shut down right then. I had no idea what the hell was going on. The power was back at full capacity, the heat blasting through the old pipes like he’d never been gone. And the bass... God, that bass. It hit so deep in the concrete that my entire body vibrated. Everything was exactly like he’d never left. Like the cell in Europe, Rico, the pawn shop, and the hunger were just a bad dream.'},
            {'img': None, 'text': 'I looked at him. His fingers rested calmly on the mixer housing. He looked like the absolute ruler of those machines, no word about the escape, no word about the ships or how he’d managed to bring the station back to life. And me? I didn\'t dare ask. On the streets, you learn one thing: when a miracle is standing right in front of you, you don\'t mess up the parade with questions. You just take it.'},
            {'img': None, 'text': 'I let my soaked jacket drop to the floor. My neck was bare, my mother’s chain was gone, but as I pulled up the chair, I felt like the Queen again for the first time in weeks.'},
            {'img': None, 'text': 'I sat down at the microphone. He still didn\'t look up, but he slid the master fader up to the millimeter and gave me the cue. The red light snapped on: ON AIR.'},
            {'img': None, 'text': 'I took a deep breath, sucked the beat into my lungs, and opened the fader. And then I started the best show Station North had ever seen. My voice wasn\'t just the voice of that girl from North Avenue anymore. It was the voice of a woman who had burned her own world to ashes just to buy this exact moment. Baltimore didn\'t just hear music that night. They heard that the Boss was back home and the Queen had her crown back.'},
            {'img': None, 'text': 'I flipped the fader back down. The red light died, and the booth sank back into that familiar, warm twilight. The beat kept pulsing in the background, lower now, like a quiet heartbeat. I was completely wiped, sweat on my forehead, but for the first time in months, there was absolute clarity in my head.'},
            {'img': 'Kapitel_13_4.png', 'text': 'I was just about to stand up, just to look at him, when my eyes caught the small console next to my headphone jack. A spot I usually never look at. I didn\'t notice it immediately. My brain was still way too wired from the show. But something was lying there. A tiny, golden glint in the dim light of the monitor. I caught my breath. My hand shook as I reached for it.'},
            {'img': None, 'text': 'It was the chain. My mother\'s gold chain. It was lying right there, neatly coiled up, like it had never left. Every single link was clean, the clasp intact. The exact same pendant I’d slammed onto the counter in that dirty pawn shop to buy his freedom.'},
            {'img': None, 'text': 'I stared at the gold in my palm, and suddenly the chill of downtown shot right back into my bones—but this time it was different. I didn\'t comprehend it. I didn\'t know how he’d pulled it off. He’d barely been in this factory for an hour, he came across the Atlantic without papers, and yet the very first thing he did was track down that abrufuckte shop to buy my backbone back for me.'},
            {'img': None, 'text': 'I slowly looked over at him. He was still sitting there, the cigarette almost burnt down, eyes locked onto the audio meters. He didn\'t say a word. He didn\'t grin proudly, he didn\'t offer an explanation. He just mixed in the next track.'},
            {'img': None, 'text': 'And in that moment, I knew: the Boss wasn\'t just back. He’d taken control of the system before I even knew he was breathing. I put the chain around my neck, felt the cold metal hit my skin, and knew that from this day on, nobody was ever gonna shut us down.“'}
        ]

    balanced_chapters.append({'title': title, 'paragraphs': new_paras})

total_cards = sum(len(c['paragraphs']) for c in balanced_chapters)
print(f"Total balanced paragraph cards across all chapters: {total_cards}")

# Write to chronicles_script.txt
script_output = "THE MANJARO LOUNGE CHRONICLES // VOL. 01\n\n"
script_output += "==================================================\n"
script_output += "[IMAGE: Cover_Vol01.png]\n"
script_output += "==================================================\n\n"

for ch in balanced_chapters:
    script_output += f"{ch['title']}\n\n"
    for p in ch['paragraphs']:
        if p['img']:
            script_output += f"[IMAGE: {p['img']}]\n"
        script_output += f"{p['text']}\n\n"
    script_output += "\n"

with open(SCRIPT_PATH, "w", encoding="utf-8") as f:
    f.write(script_output.strip() + "\n")

# Generate HTML
html_content = """<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>The Manjaro Lounge Chronicles // Vol. 01</title>
    <style>
        :root {
            --bg-color: #0b0c10;
            --card-bg: #14161d;
            --card-border: #222633;
            --text-main: #e1e4ed;
            --text-dim: #949ab1;
            --accent-red: #ff2a5f;
            --accent-gold: #e5b94c;
            --accent-blue: #00d2ff;
        }

        body {
            background-color: var(--bg-color);
            color: var(--text-main);
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            line-height: 1.6;
        }

        header {
            text-align: center;
            padding: 30px 10px 20px 10px;
            border-bottom: 2px solid var(--card-border);
            margin-bottom: 30px;
            position: relative;
        }

        h1 {
            font-size: 2.2rem;
            letter-spacing: 2px;
            margin: 0 0 10px 0;
            color: #ffffff;
            text-transform: uppercase;
        }

        .subtitle {
            color: var(--accent-gold);
            font-size: 1.1rem;
            letter-spacing: 1.5px;
            text-transform: uppercase;
            font-weight: 600;
        }

        .on-air-badge {
            display: inline-block;
            background-color: var(--accent-red);
            color: white;
            font-weight: 800;
            padding: 4px 12px;
            border-radius: 4px;
            font-size: 0.85rem;
            letter-spacing: 1.5px;
            margin-top: 10px;
            box-shadow: 0 0 12px rgba(255, 42, 95, 0.6);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; }
            50% { opacity: 0.6; }
            100% { opacity: 1; }
        }

        .container {
            max-width: 850px;
            margin: 0 auto;
        }

        .cover-box {
            text-align: center;
            margin-bottom: 40px;
        }

        .cover-box img {
            max-width: 100%;
            height: auto;
            border-radius: 12px;
            border: 2px solid var(--card-border);
            box-shadow: 0 10px 30px rgba(0,0,0,0.8);
        }

        .chapter-title {
            font-size: 1.6rem;
            font-weight: 700;
            color: var(--accent-gold);
            border-left: 4px solid var(--accent-red);
            padding-left: 14px;
            margin: 45px 0 20px 0;
            letter-spacing: 0.5px;
        }

        .paragraph-card {
            background-color: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 22px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.4);
            transition: transform 0.2s ease, border-color 0.2s ease;
        }

        .paragraph-card:hover {
            border-color: #333a4e;
            transform: translateY(-2px);
        }

        .paragraph-img {
            width: 100%;
            height: auto;
            border-radius: 8px;
            margin-bottom: 16px;
            border: 1px solid #2a2f42;
            display: block;
        }

        .paragraph-text {
            font-size: 1.05rem;
            color: var(--text-main);
            margin-bottom: 16px;
            white-space: pre-wrap;
        }

        .audio-player-box {
            background-color: #0a0b0f;
            border: 1px solid #1a1d28;
            border-radius: 8px;
            padding: 10px 14px;
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .audio-label {
            font-size: 0.82rem;
            font-weight: 700;
            color: var(--accent-blue);
            white-space: nowrap;
            letter-spacing: 0.5px;
        }

        audio {
            width: 100%;
            height: 36px;
            outline: none;
        }

        footer {
            text-align: center;
            padding: 40px 0;
            color: var(--text-dim);
            font-size: 0.9rem;
            border-top: 1px solid var(--card-border);
            margin-top: 50px;
        }
    </style>
</head>
<body>

    <header>
        <h1>The Manjaro Lounge Chronicles</h1>
        <div class="subtitle">Vol. 01 // Noir E-Book & Voice Audio Player</div>
        <div><span class="on-air-badge">ON AIR</span></div>
    </header>

    <div class="container">
        <div class="cover-box">
            <img src="Cover_Vol01.png" alt="Manjaro Lounge Chronicles Vol 01 Cover">
        </div>
"""

global_p_count = 1

for ch in balanced_chapters:
    title_escaped = ch['title'].replace('<', '&lt;').replace('>', '&gt;')
    html_content += f'\n        <div class="chapter-title">{title_escaped}</div>\n'
    
    for p in ch['paragraphs']:
        img_tag = f'            <img class="paragraph-img" src="{p["img"]}" alt="{p["img"]}">\n' if p['img'] else ''
        txt_escaped = p['text'].replace('<', '&lt;').replace('>', '&gt;')
        audio_num = f'p_{global_p_count:03d}.mp3'
        
        html_content += f"""        <div class="paragraph-card">
{img_tag}            <div class="paragraph-content">
                <div class="paragraph-text">{txt_escaped}</div>
                <div class="audio-player-box">
                    <span class="audio-label">🎧 AUDIO #{global_p_count}</span>
                    <audio controls><source src="audio/{audio_num}" type="audio/mpeg"></audio>
                </div>
            </div>
        </div>
"""
        global_p_count += 1

html_content += """
    </div>

    <footer>
        <p>Station North Media &copy; 2026 // The Manjaro Lounge Chronicles</p>
    </footer>

</body>
</html>
"""

with open(HTML_PATH, "w", encoding="utf-8") as f:
    f.write(html_content)

print(f"Successfully generated balanced HTML with {global_p_count - 1} paragraph cards.")
