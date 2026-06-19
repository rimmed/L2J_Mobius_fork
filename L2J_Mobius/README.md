# L2jMobius - An Open-Source Server Emulator

## Features

- Multiple chronicle support (early through latest clients)
- Community-driven bug fixes and improvements
- Active development and regular updates
- Full database-driven game mechanics
- Extensive configuration options

---

## Important Legal Notice

**L2jMobius is an open-source software project created through legal reverse engineering methods. This document explains the legal basis for the project. However, this is NOT legal advice. Users should consult with a qualified attorney before using this software, especially for public server operation.**

## Introduction

L2jMobius is a fully independent, open-source server emulator created entirely from scratch by volunteer contributors. Every line of code is original work. We have never copied, decompiled, or used any proprietary server code.

**Development Model:**
L2jMobius operates on an open-source development model with public releases made available three times per year. Contributors who actively share code improvements receive early access to ongoing development work. Voluntary donations support project infrastructure and development, with donors receiving temporary early access as a thank-you gesture. This model is common in open-source projects (similar to early access for Patreon supporters) and does not constitute commercial software sales.

**Important Distinction:**
- **The L2jMobius project itself is legal** - Creating and sharing server emulator code through reverse engineering.
- **How individuals use the software varies** - Operating public game servers may have different legal considerations depending on jurisdiction and how they're run.

**What we provide:**
- Original source code for server functionality.
- Educational resources about server architecture.
- A collaborative development community.

**What we do NOT provide:**
- Game client software.
- Game assets (models, textures, sounds, artwork).
- Any copyrighted content from the original game.
- Links to download copyrighted materials.
- Legal advice for server operators.

**Transitioning to MIT License:** We are moving to the MIT License to provide maximum legal clarity and align with industry standards for open-source software.

---

## How L2jMobius is Legal

### 1. We Write 100% Original Code

Every single line of code in L2jMobius is written from scratch by our contributors. This means:
- We own the copyright to our own code.
- We never copied proprietary server code.
- We never decompiled the original servers.
- All our work is independent creation.

**Legal principle:** You can't infringe copyright on something you created yourself. Original code is legally distinct from the original game's code, even if it produces similar results.

### 2. We Use Clean-Room Engineering

Clean-room engineering is a well-established legal method used throughout the software industry:

**How it works:**
1. **Observe:** We watch how the client and server communicate (network packets, protocols).
2. **Document:** We write down what we observe (data formats, message structures).
3. **Implement:** We write completely new code based only on our observations.

**What we DON'T do:**
- Access or look at proprietary source code.
- Decompile server binaries.
- Use leaked or stolen code.
- Copy any existing implementation.

**Legal precedent:** This exact method has been upheld in courts for over 30 years.

### 3. The Law Explicitly Protects Reverse Engineering for Interoperability

**United States - 17 U.S.C. § 1201(f):**
Congress wrote into law that you CAN reverse engineer software to figure out how to make programs work together (interoperability). This is not illegal. It's explicitly permitted.

**European Union - Software Directive Article 6:**
EU law states that reverse engineering to achieve interoperability is legal and contracts cannot take away this right.

**Many other countries have similar laws:** Canada, Japan, Australia, South Korea and most developed nations protect reverse engineering for compatibility.

### 4. Network Protocols and Functional Elements Aren't Copyrightable

Copyright law protects creative expression, NOT:
- How things work (methods and processes).
- Network communication protocols.
- Data formats and structures.
- Game rules and mechanics.
- System interfaces.

**Example:** You can't copyright the rules of chess, only a specific book explaining chess. Similarly, you can't copyright how a server communicates with a client, only the specific code that does it.

**Legal basis:** U.S. Copyright Law, 17 U.S.C. § 102(b) explicitly excludes "any idea, procedure, process, system, method of operation" from copyright protection.

### 5. This is How the Entire Software Industry Works

Legal server emulators and reimplementations are everywhere:

**Operating Systems:**
- **FreeBSD/OpenBSD** - Unix-like systems.
- **Linux** - Reimplemented Unix functionality.
- **ReactOS** - Reimplements Windows (20+ years of development).

**Compatibility Software:**
- **Samba** - Windows network compatibility for Linux.
- **Wine** - Runs Windows programs on Linux (30+ years).

**Programming Environments:**
- **Mono** - Open-source .NET implementation.
- **OpenJDK** - Open-source Java (now the official version!).

**Game Emulators:**
- **Dolphin** - GameCube/Wii emulator.
- **PCSX2** - PlayStation 2 emulator.
- **RPCS3** - PlayStation 3 emulator.

**Game Engine Reimplementations:**
- **OpenMW** - Morrowind engine.
- **OpenTTD** - Transport Tycoon engine.
- **ScummVM** - LucasArts adventure games.

**Other MMORPG Server Emulators:**
- **EQEmu** - EverQuest (published by Sony Online Entertainment).
- **MaNGOS/TrinityCore** - World of Warcraft (published by Blizzard Entertainment).
- **Various others** - Ultima Online, RuneScape, etc.

All of these projects are legal because they follow the same principles L2jMobius does.

---

## Key Court Cases That Protect Projects Like Ours

### Sega v. Accolade (1992)
**What happened:** Accolade reverse-engineered Sega's console to make compatible games without a license.

**Court's ruling:** Reverse engineering to understand how to make compatible software is **legal and protected as fair use**. The court specifically said that when reverse engineering is the only way to access functional information needed for compatibility, it's lawful.

**Why it matters:** This established that making compatible products through reverse engineering is legal, not copyright infringement.

### Sony v. Connectix (2000)
**What happened:** Connectix created a PlayStation emulator by reverse-engineering the PlayStation BIOS.

**Court's ruling:** Creating an emulator through reverse engineering is **legal**. Even though they made temporary copies during development, the final product (which contained no Sony code) was lawful.

**Why it matters:** Direct precedent that game emulators created through reverse engineering are legal.

### Google v. Oracle (2021)
**What happened:** Google copied Java API declarations for Android to let programmers use their existing Java knowledge.

**Court's ruling:** The Supreme Court ruled this was **fair use**, emphasizing that functional elements used for interoperability and enabling developers to use their knowledge receives special protection.

**Why it matters:** Most recent Supreme Court case affirming that functional compatibility receives strong fair use protection.

---

## What About EULAs and Terms of Service?

**The Reality:** Yes, running a private server probably violates the game's Terms of Service.

**But here's what that means legally:**

### EULA Violations Are NOT Copyright Infringement

- **Contract vs. Copyright:** Breaking a contract (EULA) is different from breaking copyright law.
- **Who it applies to:** EULAs only bind people who agreed to them.
- **What it means:** Publishers can ban your accounts, but that's not a criminal matter.

### The Law Overrides Contracts in Many Places

**European Union:** The Software Directive explicitly states that contracts cannot override the right to reverse engineer for interoperability. Those contract terms are "null and void."

**United States:** More complex, but many courts have held that statutory rights (like the DMCA's reverse engineering exception) cannot be eliminated by private contracts.

### Not Everyone Agreed to the EULA

- Contributors who never played the game are not bound by its EULA.
- Observing network traffic doesn't require agreeing to terms.
- Information obtained lawfully by non-parties is not "tainted".

### The Law Overrides Contracts in Many Places

Even if someone argued that some elements were copyrightable (which we dispute), our use would still be **fair use** under copyright law.

Fair use considers four factors:

### 1. Purpose and Character of Use
✓ **Educational** - Teaching server architecture, networking, programming.  
✓ **Research** - Academic study of game systems.  
✓ **Preservation** - Maintaining access to legacy game versions.  
✓ **Transformative** - Used for learning and research, not just playing.  
✓ **Non-commercial** - Open-source project with no profit motive.  

### 2. Nature of Copyrighted Work
✓ **Highly functional** - Server software is functional, not creative.  
✓ **Published** - Game is publicly available.  
✓ **Less protection** - Functional works get less copyright protection.  

### 3. Amount Used
✓ **No verbatim copying** - We copy zero actual code.  
✓ **Only functional specs** - Just what's necessary for compatibility.  
✓ **Original implementation** - Everything is rewritten from scratch.  

### 4. Market Effect
✓ **No substitution** - Users still need legitimate game client.  
✓ **Potential benefits** - Extends product life, maintains community.  
✓ **No harm to current sales** - Often used for deprecated versions.  
✓ **Competition is lawful** - Courts have said competition through interoperability is legal, not infringement.  

**Legal precedent:** Courts have consistently found that reverse engineering for compatibility has minimal market impact and is protected.

---

## Why We Don't Distribute Game Assets

We maintain **strict separation** between our code and copyrighted game content:

**We NEVER provide:**
- Game client software.
- 3D models or textures.
- Sounds or music.
- Artwork or animations.
- Proprietary data files.
- Links to download any of the above.

**Users must:**
- Obtain a legitimate copy of the game client themselves.
- Accept responsibility for their own compliance with the client's license.
- Understand that they may be violating Terms of Service by connecting to unofficial servers.

**This separation is exactly like:**
- Emulators requiring users to provide their own game ROMs.
- Linux requiring users to provide proprietary firmware.
- Wine requiring users to provide Windows software.

**Legal principle:** The emulator itself doesn't infringe. Users must comply with licenses for the client software they independently obtain.

---

## Educational and Research Value

L2jMobius serves important purposes protected by law:

**Educational Uses:**
- Teaching server architecture and design.
- Learning network programming and protocols.
- Studying database design and optimization.
- Understanding client-server architectures.
- Training in multi-threaded programming.

**Research Uses:**
- Academic study of MMORPG mechanics.
- Research into virtual economies.
- Security research and analysis.
- Network protocol documentation.

**Preservation:**
- Maintaining knowledge of legacy systems.
- Documenting game version history.
- Preserving cultural heritage of digital entertainment.

**Legal protection:** Copyright law explicitly protects educational and research uses. U.S. law lists "teaching, scholarship, or research" as examples of fair use.

---

## Our Commitment to Legality

### What We Do to Stay Legal

1. **Code Audits:** Regular reviews to ensure no proprietary code in our repository.
2. **No Asset Distribution:** Strict policy against distributing copyrighted assets.
3. **Educational Focus:** Emphasizing research, education and preservation.
4. **Transparency:** Fully open-source with public development.
5. **Responsive:** We address legitimate legal concerns promptly.
6. **Clean-Room Documentation:** We maintain records of our development process.

### Community Standards

We expect all contributors and users to:
- Never distribute copyrighted game assets.
- Respect intellectual property rights.
- Use the software responsibly and legally.
- Report any compliance concerns.
- Contribute in good faith.

---

## International Perspective

### Strong Legal Protection Countries

**United States:**
- DMCA § 1201(f) explicitly protects reverse engineering.
- Fair use doctrine.
- First Amendment protections for code.
- Strong precedent (Sega, Sony, Google cases).

**European Union:**
- Software Directive mandatory exceptions.
- Contracts cannot override interoperability rights.
- Competition law supports compatibility.
- Recent court decisions expanding protections.

**Other Countries with Good Protection:**
- Canada - Strong reverse engineering rights.
- Australia - Copyright Act protections.
- Japan - Interoperability exceptions.
- South Korea - Legal reverse engineering framework.

### Countries with Less Clear Laws

Some jurisdictions have less developed case law or different legal frameworks. **If you're in a country not listed above, consult local legal counsel before using this software.**

---

## License

**L2jMobius is transitioning to the MIT License.**

```
MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense and/or sell
copies of the Software and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

**Note:** Some legacy code may remain under GPLv3. See individual file headers.

---

## Contributing

We welcome contributions from the community!

**Our Development Model:**
- **Public releases** - Code made publicly available three times per year.
- **Active development** - Ongoing work accessible to contributors and supporters.
- **Contributor access** - Those who share code improvements get early access to development.
- **Supporter access** - Voluntary donations support infrastructure; donors receive temporary early access as appreciation.
- **Always eventually free** - All code becomes publicly available.

This model is common in open-source projects (WordPress plugins, Blender add-ons, etc.) and helps sustain development while keeping the project open.

**How to contribute:**
- Report bugs and issues on our forum.
- Submit code improvements and bug fixes.
- Help with documentation and testing.
- Share your knowledge with other developers.

**Remember - All contributions must be original code. Never submit:**
- Decompiled or reverse-engineered proprietary code.
- Leaked server files.
- Copyrighted game assets.

**For server operators:**
- Understand that operating public servers may violate game Terms of Service.
- This is a separate issue from the legality of the emulator code itself.
- Commercial server operation carries additional legal considerations.
- You are responsible for your own compliance with local laws.
- L2jMobius developers are not responsible for how users deploy the software.

**Project policy:** We do not encourage or support commercial server operation. Our project exists for education, research, preservation and collaborative development.

## Support & Community

- **Forum:** Get help, share ideas and discuss development.
- **Discord:** Real-time chat with developers and users.

---

## Disclaimer

**THIS IS NOT LEGAL ADVICE.**

This software is provided "as is" without warranty of any kind. 

**About the L2jMobius Code:**
- The L2jMobius emulator code is created through legal reverse engineering methods.
- We believe the code itself is legal based on established precedent and statutory protections.
- We distribute only original code, never copyrighted game assets.

**About Using This Software:**
Users are responsible for ensuring their use complies with applicable laws in their jurisdiction. 

**Important distinctions:**
- **Creating emulator code** (what L2jMobius does) is protected by reverse engineering laws.
- **Operating game servers** (what a user can do) may violate Terms of Service and raise different legal issues.
- **Commercial server operation** is particularly legally complex and not encouraged by this project.

**The developers and contributors:**
- Make no guarantees about legality in all jurisdictions.
- Are not responsible for how third parties use this software.
- Disclaim all liability for legal consequences of server operation.
- Do not encourage commercial server operation.
- Recommend this software for educational, research and preservation purposes.
- Strongly recommend consulting qualified legal counsel before operating any public servers.

**If you operate servers:**
- Understand you may be violating game Terms of Service (contract issue).
- Terms of Service violations can result in account bans.
- Commercial operation may face additional legal scrutiny.
- You accept all legal risks and responsibilities.
- Consult an attorney in your jurisdiction.

Laws vary by country and state. **When in doubt, consult a qualified attorney in your jurisdiction.**

---

## Thank you!

Thanks to all the people that helped with the development and contributed other the years.
