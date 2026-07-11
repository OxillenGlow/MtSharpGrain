<img src="Pictures/Sc.png" align="center"/> 

# MtSharpGrain 
![Last comit](https://img.shields.io/github/last-commit/OxillenGlow/Mtsharpgrain) <sup>_<-- constantly improving!_</sup>

<sup><sup>_If you don't see any comits in the last few days, I have been lazy._</sup></sup>
![GitHub Repo stars](https://img.shields.io/github/stars/Oxillenglow/MtSharpGrain?label=Please%20help%20increase%20%E2%86%92%20stars)


<img src="Pictures/content-1.png" align="left" width="25" style="margin-right: 20px;" />
A sand box, non voxel game with slightly smooth interconnected blocks rather than traditional blocks. It is coded 100% in java (openGL lwjgl and jme).

### What's in it so far

- **Semi-smooth node meshes** — blocks connect with rounded transitions rather than hard cube edges
- **JavaScript modifier API** - much more powerful than JVisualScripting, mostly complete API with:
    - end-node place/destroy
    - gui management
    - procedural world generation
    - end-node placement listener
    - player location getter/setter
- **[jVisualScripting](https://github.com/openconcerto/jVisualScripting) engine & editor** (depricating) — block based visual coding system, mostly wired into the game now with console comand system.
- **Console command system** — `!place` / `!destroy` commands with role-based authorization (editor / manager / admin)

### Download [here](https://github.com/OxillenGlow/MtSharpGrain/releases)
---

## Screen shots
<details>
<summary> Click here to see screen shots</summary>

![](Pictures/jVS-ingame-demo.png)
Using jVisualScript to break and place blocks (too bad i did not do a GIF)

![](Pictures/CoolArtifacts.png) 
Just interesting to see how it is loading.

![](Pictures/v0.1.0-beta2.png)
This is a picture from current version.

</details>

---

#### Links

- 📖 [Wiki](https://github.com/OxillenGlow/MtSharpGrain/wiki)
- 💬 [Discussions](https://github.com/OxillenGlow/MtSharpGrain/discussions)

---

### Special points
<img src="Pictures/content-1.png" align="left" width="25" style="margin-right: 20px;" />
This is a project aimed at making a futuristic grided sanbox game using shaders, enviroment, and interconnected nodes. Of course, the current version falls short by a lot.

---


      m   m           s s s s            g g g
    m   m   m       s                  g
    m   m   m         s s s            g   g g g
    m       m               s          g       g
    m       m t     s s s s   harp       g g g   rain

---

### Important? stuff
#### What am i working on now?
<details>
<summary>What am i working on now?</summary>
    
My todo/doing list:
- simple world generation 70%
    - random generation 90%
    - World making / hand sculpting 30%
- More content: 0%
    - More blocks: ice, glass, metal, plastic 30%
    - Special blocks: ... 
    - Person model - i think i should use the java drake model for people
    - Randomly spawned builtings
- GUI > afterwards customisable with scripts 80%
- Npc with behavior > afterwards, scripts to control npc 0%
- PBR, idk if it will actually make the game look "better" 0% (not going to implement unless there is a big jump in computing power)
- JavaScript, to add some real and powerfull scripting (thanks a lot to claude)80%
- **IMPORTANT** Upgrade GUI to use newly uploaded images 0% (lemur is a bit too big for me right now, perhaps in the future.
- At the end of resizing, i should either find a IGUI function that recalculates the screen OR just make a new IGUI object and hope for the best of igui's code and java's gc janitor. 0%

<sup>80% here means it is basically done but could be improved</sup>

Done:
- Full screen ect 100%
- Default JavaSctipt mods to be placed in assets and unpacked at runtime 90%
- Add buttons for adjusting view distance

</details>

### ⭐ [Other Projects ✨](https://github.com/OxillenGlow)
[My other projects](https://github.com/OxillenGlow)
### This project uses:

- **JavaMonkeyEngine** (and everthing that LWJGL has)
Website: https://www.jmonkeyengine.org
GitHub organization: https://github.com/jmonkeyengine

- **Riccardobl's simple IGui** for jme
GitHub source: https://github.com/riccardobl/jme-igui

- **Neuroph** for mlp (unused)
GitHub source: https://github.com/neuroph/NeurophFramework 

- **jVisualScripting** for visual scripts and engine (unused) see
GitHub source: https://github.com/openconcerto/jVisualScripting

- **GraalVM's community GraalJS** for the javascript modules
GitHub source: https://github.com/oracle/graaljs

#### AI?

Yes, I use claude a lot, perhaps too much? idk just speeds things up and removes need for constantly checking the API of whatever thing is implemented.

This is only for code, all assets/ideas are human made (not that i use a lot of assets).

---
<details>
<summary>The dumb section</summary>
empty...
