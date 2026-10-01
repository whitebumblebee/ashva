package com.openinglab.shared.data

import com.openinglab.shared.model.Difficulty
import com.openinglab.shared.model.HistoricalGame
import com.openinglab.shared.model.MoveStep
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.OpeningSide
import com.openinglab.shared.model.Variation

/**
 * Offline-first seed catalog. The repository boundary lets this data move to Room and a
 * versioned opening service later without changing either the trainer or the UI.
 */
object OpeningCatalog {
    val openings: List<Opening> = listOf(
        Opening(
            id = "ruy-lopez",
            recognitionPly = 5,
            name = "Ruy López",
            family = "Open games",
            eco = "C60–C99",
            side = OpeningSide.BOTH,
            difficulty = Difficulty.INTERMEDIATE,
            description = "A timeless fight for the centre. Pressure the e5 pawn, develop with purpose, and keep tension until your pieces are ready.",
            identity = "Strategic · rich · enduring",
            accentHex = 0xFFE8B86D,
            progress = 42,
            keyIdeas = listOf("Pressure e5", "Prepare d4", "Preserve the bishop", "Choose the right pawn break"),
            variations = listOf(
                variation(
                    "ruy-main", "Morphy Defence", "MAIN LINE", "The classical tabiya: secure the bishop, castle, then build the d4 break.",
                    m("e2e4", "e4", "Claim the centre", "The king pawn opens both bishop and queen while controlling d5 and f5.", "Space before tactics"),
                    m("e7e5", "e5", "Meet space with space", "Black mirrors the central claim and frees the dark-squared bishop.", "Fight for equal space"),
                    m("g1f3", "Nf3", "Develop with tempo", "The knight attacks e5 and makes castling possible.", "Improve a piece with every move"),
                    m("b8c6", "Nc6", "Defend naturally", "Black protects e5 without blocking a bishop.", "Development solves problems"),
                    m("f1b5", "Bb5", "Create a pin", "The bishop questions the knight that guards e5. The threat is positional, not an immediate pawn win.", "Pressure the defender"),
                    m("a7a6", "a6", "Ask the bishop", "Black gains space and makes White reveal the bishop's plan.", "Gain a useful tempo"),
                    m("b5a4", "Ba4", "Keep the tension", "The Spanish bishop stays on the a4–e8 diagonal and preserves long-term pressure.", "Do not clarify too early"),
                    m("g8f6", "Nf6", "Counterattack e4", "Black develops and attacks White's centre immediately.", "Create a second problem"),
                    m("e1g1", "O-O", "Make the king safe", "Castling unlocks the rook and removes tactics against the king.", "Safety enables ambition"),
                    m("f8e7", "Be7", "Prepare to castle", "A quiet square keeps the bishop safe and completes kingside development.", "Finish development"),
                    m("f1e1", "Re1", "Reinforce e4", "The rook supports the centre so White can consider c3 and d4.", "Build the pawn break"),
                    m("b7b5", "b5", "Gain queenside space", "Black pushes the bishop to its ideal Spanish diagonal.", "Expand with tempo"),
                    m("a4b3", "Bb3", "Aim at f7", "The bishop settles on b3, where it quietly eyes the sensitive f7 square.", "Keep the good bishop"),
                    m("d7d6", "d6", "Hold the centre", "Black supports e5 and releases the light-squared bishop.", "A solid base first"),
                    m("c2c3", "c3", "Prepare d4", "White builds the thematic central break and gives the bishop a retreat square.", "Prepare before striking"),
                    m("e8g8", "O-O", "Connect the position", "Black's king reaches safety before the centre opens.", "Castle before contact"),
                ),
                variation(
                    "ruy-berlin", "Berlin Defence", "MAJOR LINE", "A resilient defence where Black attacks e4 before committing the f8 bishop.",
                    m("e2e4", "e4", "Take space", "White starts with the most direct central claim.", "Claim the centre"),
                    m("e7e5", "e5", "Mirror the centre", "Black contests d4 and f4.", "Equal space"),
                    m("g1f3", "Nf3", "Develop with threat", "The knight attacks e5.", "Tempo matters"),
                    m("b8c6", "Nc6", "Defend e5", "Natural development keeps balance.", "Useful defence"),
                    m("f1b5", "Bb5", "Enter the Spanish", "White applies indirect pressure to e5.", "Pressure the chain"),
                    m("g8f6", "Nf6", "The Berlin move", "Black ignores the pin and counterattacks e4.", "Counterattack beats passivity"),
                    m("e1g1", "O-O", "Offer the pawn", "White castles and allows the famous endgame route.", "Activity over material"),
                    m("f6e4", "Nxe4", "Accept the challenge", "Black temporarily wins e4, ready for forcing play.", "Calculate the forcing line"),
                ),
                variation(
                    "ruy-exchange", "Exchange Variation", "MINOR LINE", "White exchanges on c6 to damage Black's pawn structure and target the endgame.",
                    m("e2e4", "e4", "Central space", "White opens lines for rapid development.", "Space"),
                    m("e7e5", "e5", "Contest the centre", "Black takes an equal share.", "Balance"),
                    m("g1f3", "Nf3", "Attack e5", "Natural development creates a question.", "Develop with purpose"),
                    m("b8c6", "Nc6", "Protect e5", "The knight defends the centre.", "Natural defence"),
                    m("f1b5", "Bb5", "Pin the defender", "White enters the Ruy López.", "Indirect pressure"),
                    m("a7a6", "a6", "Question the bishop", "Black gains the bishop pair if White exchanges.", "Force a decision"),
                    m("b5c6", "Bxc6", "Damage the structure", "White gives up the bishop pair to create doubled c-pawns.", "Trade assets for targets"),
                    m("d7c6", "dxc6", "Open the bishop", "The d-pawn recaptures to keep active central possibilities.", "Recapture with a plan"),
                ),
            ),
            historicalGame = HistoricalGame("José Capablanca", "Savielly Tartakower", "New York", 1924, "1–0", "Capablanca turns a tiny Spanish edge into a model rook ending."),
        ),
        Opening(
            id = "london",
            recognitionPly = 5,
            name = "London System",
            family = "Queen's pawn systems",
            eco = "D02",
            side = OpeningSide.WHITE,
            difficulty = Difficulty.FOUNDATION,
            description = "A dependable setup with a clear development scheme. Learn when to hold the pyramid—and when to break it open.",
            identity = "Reliable · flexible · practical",
            accentHex = 0xFFA8CBE8,
            progress = 68,
            keyIdeas = listOf("Bf4 before e3", "Build the pawn pyramid", "Ne5 outpost", "Attack with h4–h5"),
            variations = listOf(
                variation(
                    "london-main", "Classical Setup", "CORE SYSTEM", "Build the signature triangle, finish development, and place a knight on e5.",
                    m("d2d4", "d4", "Take queen-side space", "The d-pawn claims e5 and gives the c1 bishop room to develop.", "Claim useful space"),
                    m("d7d5", "d5", "Meet the centre", "Black establishes a symmetrical foothold.", "Contest key squares"),
                    m("g1f3", "Nf3", "Control e5", "The knight supports the centre without blocking the c-pawn.", "Keep options open"),
                    m("g8f6", "Nf6", "Develop naturally", "Black controls e4 and prepares castling.", "Natural development"),
                    m("c1f4", "Bf4", "The London bishop", "Develop the bishop outside the pawn chain before playing e3.", "Good bishop first"),
                    m("e7e6", "e6", "Build a solid centre", "Black supports d5 and opens the dark bishop.", "Solid foundations"),
                    m("e2e3", "e3", "Complete the pyramid", "White reinforces d4 and opens the light bishop.", "Support the centre"),
                    m("c7c5", "c5", "Challenge d4", "Black immediately asks whether White can maintain the centre.", "Attack the base"),
                    m("c2c3", "c3", "Reinforce d4", "The London triangle is complete.", "Build before expanding"),
                    m("b8c6", "Nc6", "Add pressure", "Black develops and increases pressure on d4.", "Pile up attackers"),
                    m("b1d2", "Nbd2", "Keep the structure flexible", "The knight supports e4 and prepares Bd3.", "Choose the right knight"),
                    m("f8d6", "Bd6", "Offer a bishop trade", "Black challenges London's best-developed piece.", "Trade the active piece"),
                ),
                variation(
                    "london-early-c5", "Early ...c5", "BLACK CHALLENGE", "Respond accurately when Black attacks the d4 base before completing development.",
                    m("d2d4", "d4", "Set the system", "White claims e5.", "Stable centre"),
                    m("g8f6", "Nf6", "Stay flexible", "Black withholds the d-pawn.", "Delay commitment"),
                    m("g1f3", "Nf3", "Develop", "White keeps the usual setup.", "Consistency"),
                    m("c7c5", "c5", "Immediate pressure", "Black attacks d4 at once.", "Challenge the base"),
                    m("e2e3", "e3", "Keep d4 supported", "White stays compact and prepares c3.", "Reinforce first"),
                    m("e7e6", "e6", "Prepare ...d5", "Black can transpose into a sound central structure.", "Flexible setup"),
                ),
            ),
            historicalGame = HistoricalGame("Gata Kamsky", "Samuel Shankland", "St Louis", 2014, "1–0", "A modern demonstration of the London as a genuine attacking system."),
        ),
        Opening(
            id = "sicilian",
            name = "Sicilian Defence",
            family = "Semi-open games",
            eco = "B20–B99",
            side = OpeningSide.BLACK,
            difficulty = Difficulty.ADVANCED,
            description = "Create an asymmetrical fight from move one. Black trades symmetry for dynamic chances and a queenside majority.",
            identity = "Dynamic · sharp · ambitious",
            accentHex = 0xFFE7988E,
            progress = 18,
            keyIdeas = listOf("Trade c-pawn for d-pawn", "Pressure the c-file", "Counterattack on the queenside", "Time ...d5"),
            variations = listOf(
                variation(
                    "sicilian-najdorf", "Najdorf Variation", "MAIN LINE", "The flexible ...a6 system: stop Bb5+, prepare ...b5, and keep every central option alive.",
                    m("e2e4", "e4", "White takes the centre", "White invites an open battle.", "Space"),
                    m("c7c5", "c5", "Create asymmetry", "Black attacks d4 with a flank pawn and avoids mirroring.", "Unbalance the game"),
                    m("g1f3", "Nf3", "Prepare d4", "White develops and readies the Open Sicilian.", "Develop with a break"),
                    m("d7d6", "d6", "Control e5", "Black prepares Nf6 without allowing e5.", "Restrain before developing"),
                    m("d2d4", "d4", "Open the centre", "White offers the d-pawn to accelerate development.", "Spend a tempo for activity"),
                    m("c5d4", "cxd4", "Exchange flank for centre", "Black trades the c-pawn for White's central pawn.", "Improve the pawn majority"),
                    m("f3d4", "Nxd4", "Restore material", "The knight centralizes with tempo.", "Recapture by developing"),
                    m("g8f6", "Nf6", "Attack e4", "Black develops with an immediate threat.", "Tempo"),
                    m("b1c3", "Nc3", "Protect e4", "White adds a defender and completes the ideal knight pair.", "Natural defence"),
                    m("a7a6", "a6", "The Najdorf signature", "A small move with big ideas: ...b5, ...e5, and no Bb5+.", "Keep the centre flexible"),
                ),
                variation(
                    "sicilian-dragon", "Dragon Variation", "MAJOR LINE", "Fianchetto the dark bishop and race attacks on opposite wings.",
                    m("e2e4", "e4", "Take the centre", "White claims space.", "Space"),
                    m("c7c5", "c5", "Fight asymmetrically", "Black challenges d4.", "Counterplay"),
                    m("g1f3", "Nf3", "Prepare d4", "White develops.", "Development"),
                    m("d7d6", "d6", "Control e5", "Black steadies the position.", "Restraint"),
                    m("d2d4", "d4", "Open Sicilian", "The centre is opened by force.", "Open lines"),
                    m("c5d4", "cxd4", "Trade toward the centre", "Black creates the half-open c-file.", "Structural gain"),
                    m("f3d4", "Nxd4", "Centralize", "White's knight reaches its best square.", "Active recapture"),
                    m("g8f6", "Nf6", "Attack e4", "Black develops with tempo.", "Tempo"),
                    m("b1c3", "Nc3", "Defend", "White supports e4.", "Development"),
                    m("g7g6", "g6", "Build the Dragon", "Black prepares the long diagonal bishop.", "Fianchetto with purpose"),
                ),
            ),
            historicalGame = HistoricalGame("Garry Kasparov", "Viswanathan Anand", "Riga", 1995, "1–0", "A reference point for initiative and calculation in the Sicilian."),
        ),
        Opening(
            id = "french",
            name = "French Defence",
            family = "Semi-open games",
            eco = "C00–C19",
            side = OpeningSide.BLACK,
            difficulty = Difficulty.INTERMEDIATE,
            description = "Invite White to advance, then undermine the pawn chain. The French is a lesson in timing pawn breaks.",
            identity = "Resilient · thematic · counterpunching",
            accentHex = 0xFFD5AFDF,
            progress = 0,
            keyIdeas = listOf("Attack d4 with ...c5", "Pressure the pawn chain", "Free the c8 bishop", "Counter with ...f6"),
            variations = listOf(
                variation(
                    "french-classical", "Classical Variation", "MAIN LINE", "Develop quickly against White's advanced e-pawn and attack its base.",
                    m("e2e4", "e4", "White claims space", "The king pawn controls the centre.", "Space"),
                    m("e7e6", "e6", "Prepare ...d5", "Black builds a strong central challenge.", "Challenge, don't copy"),
                    m("d2d4", "d4", "Build the duo", "White occupies the full centre.", "Central space"),
                    m("d7d5", "d5", "Strike the centre", "Black immediately questions e4.", "Challenge the pawn chain"),
                    m("b1c3", "Nc3", "Defend e4", "White develops while maintaining the centre.", "Development with purpose"),
                    m("g8f6", "Nf6", "Attack e4 again", "Black develops and forces White to decide.", "Increase pressure"),
                    m("e4e5", "e5", "Gain space", "White closes the centre and drives the knight's ambitions.", "Space has a cost"),
                    m("f6d7", "Nfd7", "Reroute to attack", "The knight supports ...c5 and later ...f6.", "Retreat with purpose"),
                    m("f2f4", "f4", "Reinforce e5", "White builds a broad kingside chain.", "Support the spearhead"),
                    m("c7c5", "c5", "Attack the base", "Black strikes d4, the base of White's pawn chain.", "Attack the base, not the head"),
                ),
                variation(
                    "french-exchange", "Exchange Variation", "QUIET LINE", "Avoid symmetry by choosing active piece placement and timely central pressure.",
                    m("e2e4", "e4", "Take space", "White claims the centre.", "Space"),
                    m("e7e6", "e6", "Prepare the challenge", "Black readies ...d5.", "Counter"),
                    m("d2d4", "d4", "Build the centre", "White forms a pawn duo.", "Space"),
                    m("d7d5", "d5", "Challenge", "Black attacks e4.", "Immediate contact"),
                    m("e4d5", "exd5", "Release the tension", "White chooses a symmetrical structure.", "Clarity over space"),
                    m("e6d5", "exd5", "Restore the centre", "Black opens the e-file and frees the bishop.", "Active recapture"),
                ),
            ),
            historicalGame = HistoricalGame("Mikhail Botvinnik", "José Capablanca", "AVRO", 1938, "1–0", "A legendary example of long-term planning becoming a tactical finish."),
        ),
        Opening(
            id = "kings-indian",
            recognitionPly = 8,
            name = "King's Indian",
            family = "Indian defences",
            eco = "E60–E99",
            side = OpeningSide.BLACK,
            difficulty = Difficulty.ADVANCED,
            description = "Let White build the centre, then attack it with everything. A repertoire for players who want decisive games.",
            identity = "Fierce · complex · uncompromising",
            accentHex = 0xFFE0CD72,
            progress = 0,
            keyIdeas = listOf("Invite the big centre", "Break with ...e5 or ...c5", "Attack the king with ...f5", "Value activity over space"),
            variations = listOf(
                variation(
                    "kid-classical", "Classical Variation", "MAIN LINE", "Castle quickly, challenge the centre, and prepare a kingside pawn storm.",
                    m("d2d4", "d4", "White takes space", "White controls e5.", "Space"),
                    m("g8f6", "Nf6", "Control e4", "Black develops without committing a central pawn.", "Flexible control"),
                    m("c2c4", "c4", "Expand", "White grips d5.", "Broad centre"),
                    m("g7g6", "g6", "Prepare the fianchetto", "Black plans a powerful bishop on g7.", "Long diagonal"),
                    m("b1c3", "Nc3", "Support e4", "White prepares a full pawn centre.", "Build the centre"),
                    m("f8g7", "Bg7", "Aim through the centre", "The bishop may look blocked now, but becomes an attacker after the centre moves.", "Think in future diagonals"),
                    m("e2e4", "e4", "Claim maximum space", "White builds the ideal centre.", "Space"),
                    m("d7d6", "d6", "Prepare ...e5", "Black supports the coming central strike.", "Undermine the centre"),
                    m("g1f3", "Nf3", "Develop safely", "White prepares castling.", "King safety"),
                    m("e8g8", "O-O", "Castle early", "Black secures the king before attacking the centre.", "Safety first"),
                    m("f1e2", "Be2", "Complete development", "White is ready to castle.", "Finish development"),
                    m("e7e5", "e5", "Challenge the centre", "Black fixes the pawn structure and defines the battle.", "Strike at the right moment"),
                ),
            ),
            historicalGame = HistoricalGame("John Nunn", "Ilya Smirin", "European Teams", 1992, "0–1", "A model kingside attack where every black piece joins the assault."),
        ),
        Opening(
            id = "queens-gambit",
            recognitionPly = 3,
            name = "Queen's Gambit",
            family = "Closed games",
            eco = "D06–D69",
            side = OpeningSide.BOTH,
            difficulty = Difficulty.FOUNDATION,
            description = "Offer a wing pawn to deflect Black's centre. Learn classical development, minority attacks, and isolated queen's pawn play.",
            identity = "Classical · principled · instructive",
            accentHex = 0xFF88D0BC,
            progress = 24,
            keyIdeas = listOf("Pressure d5", "Develop with tempo", "Use the c-file", "Choose e4 at the right time"),
            variations = listOf(
                variation(
                    "qgd", "Queen's Gambit Declined", "MAIN LINE", "Black keeps d5 and builds a compact position; White develops pressure naturally.",
                    m("d2d4", "d4", "Claim d4", "White controls e5 and c5.", "Space"),
                    m("d7d5", "d5", "Meet the centre", "Black claims an equal share.", "Symmetry"),
                    m("c2c4", "c4", "The gambit", "White attacks d5 with a wing pawn.", "Deflect the defender"),
                    m("e7e6", "e6", "Decline solidly", "Black protects d5 and opens the dark bishop.", "Hold the centre"),
                    m("b1c3", "Nc3", "Add pressure", "White develops and attacks d5 again.", "Develop with pressure"),
                    m("g8f6", "Nf6", "Defend naturally", "Black adds control to d5 and e4.", "Natural development"),
                    m("c1g5", "Bg5", "Pin the knight", "White makes it harder for Black to resolve central tension.", "Pressure the defender"),
                    m("f8e7", "Be7", "Break the pin", "Black prepares to castle safely.", "Calm development"),
                    m("e2e3", "e3", "Open the bishop", "White supports d4 and prepares Nf3.", "Complete the centre"),
                    m("e8g8", "O-O", "Secure the king", "Black is ready for c5 or Nbd7.", "King safety"),
                ),
                variation(
                    "qga", "Queen's Gambit Accepted", "MAJOR LINE", "Black takes c4 but must return material or concede development.",
                    m("d2d4", "d4", "Take the centre", "White controls key squares.", "Space"),
                    m("d7d5", "d5", "Mirror", "Black establishes a foothold.", "Balance"),
                    m("c2c4", "c4", "Offer the pawn", "White invites Black away from the centre.", "Temporary investment"),
                    m("d5c4", "dxc4", "Accept", "Black takes the pawn and asks White to prove compensation.", "Material versus time"),
                    m("g1f3", "Nf3", "Do not rush recovery", "White develops before trying to win c4 back.", "Development over pawns"),
                    m("g8f6", "Nf6", "Match development", "Black prepares to defend actively.", "Keep pace"),
                ),
            ),
            historicalGame = HistoricalGame("Anatoly Karpov", "Boris Spassky", "Montreal", 1979, "1–0", "Karpov's positional squeeze shows how small advantages accumulate."),
        ),
        Opening(
            id = "caro-kann",
            name = "Caro–Kann",
            family = "Semi-open games",
            eco = "B10–B19",
            side = OpeningSide.BLACK,
            difficulty = Difficulty.FOUNDATION,
            description = "Challenge e4 without locking in your light bishop. Solid structure meets precise, active development.",
            identity = "Sound · clear · durable",
            accentHex = 0xFFB7B0E6,
            progress = 0,
            keyIdeas = listOf("Play ...d5 safely", "Develop Bf5 before ...e6", "Challenge the centre", "Use the c-file"),
            variations = listOf(
                variation(
                    "caro-classical", "Classical Variation", "MAIN LINE", "Exchange on e4, develop the bishop actively, then complete the structure.",
                    m("e2e4", "e4", "White takes space", "White builds the centre.", "Space"),
                    m("c7c6", "c6", "Prepare ...d5", "Black supports the central challenge without blocking the c8 bishop.", "Solve the bad bishop"),
                    m("d2d4", "d4", "Build the centre", "White claims maximum space.", "Space"),
                    m("d7d5", "d5", "Challenge directly", "Black contests e4 with full support.", "Break the centre"),
                    m("b1c3", "Nc3", "Defend e4", "White develops naturally.", "Development"),
                    m("d5e4", "dxe4", "Clarify the centre", "Black releases the tension on favorable terms.", "Choose the moment"),
                    m("c3e4", "Nxe4", "Centralize", "White recaptures with an active knight.", "Active recapture"),
                    m("c8f5", "Bf5", "Free the bishop", "The defining Caro–Kann idea: activate the bishop before ...e6.", "Best piece first"),
                    m("e4g3", "Ng3", "Chase the bishop", "White gains time while keeping the knight active.", "Tempo"),
                    m("f5g6", "Bg6", "Keep the bishop", "Black preserves the good bishop and prepares ...e6.", "Protect the strategic asset"),
                ),
            ),
            historicalGame = HistoricalGame("Vladimir Kramnik", "Deep Blue", "New York", 2002, "½–½", "A clean model of Caro–Kann solidity under sustained pressure."),
        ),
    )

    fun byId(id: String): Opening = openings.first { it.id == id }
    fun search(query: String): List<Opening> = if (query.isBlank()) openings else openings.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.family.contains(query, ignoreCase = true) ||
            it.eco.contains(query, ignoreCase = true) ||
            it.keyIdeas.any { idea -> idea.contains(query, ignoreCase = true) }
    }

    private fun variation(
        id: String,
        name: String,
        category: String,
        description: String,
        vararg steps: MoveStep,
    ) = Variation(
        id, name, category, description, steps.toList(),
        recognitionPly = when (id) {
            "ruy-main", "ruy-berlin", "french-classical" -> 6
            "ruy-exchange" -> 7
            "london-main" -> 9
            "qgd", "qga" -> 4
            "caro-classical" -> 8
            "kid-classical" -> 11
            else -> steps.size
        },
        // This short challenge lesson never reaches Bf4: it alone cannot establish a London.
        identifiesOpening = id != "london-early-c5",
        whiteIdea = when (id) {
            "ruy-main" -> "Preserve the Spanish bishop, castle, reinforce e4 with Re1, and prepare c3–d4. Black counters with pressure on e4 and queenside space; do not assume e5 is a free pawn."
            "ruy-berlin" -> "Castle while Black counterattacks e4. Activity matters alongside material. This short seed stops after ...Nxe4; the subsequent recovery and Berlin endgame lines are not included yet."
            "ruy-exchange" -> "Exchange on c6 to trade the bishop pair for a pawn-structure target. This seed shows the recapture, not a forced win or a complete endgame."
            else -> steps.filterIndexed { index, _ -> index % 2 == 0 }.map { it.principle }.distinct().joinToString(" · ")
        },
        blackIdea = when (id) {
            "ruy-main" -> "Question the bishop with ...a6 and ...b5, develop with pressure on e4, support e5 with ...d6, and castle. White is preparing c3–d4, so watch the centre as you finish development."
            "ruy-berlin" -> "Use ...Nf6 to counterattack e4 instead of immediately playing ...a6. The seed demonstrates ...Nxe4 after White castles; calculate follow-ups before treating the extra pawn as secure."
            "ruy-exchange" -> "After Bxc6, recapture with the d-pawn and use the bishop pair and opened lines as compensation for the doubled c-pawns. White will target your structure."
            else -> steps.filterIndexed { index, _ -> index % 2 == 1 }.map { it.principle }.distinct().joinToString(" · ")
        },
    )

    private fun m(uci: String, san: String, title: String, explanation: String, principle: String) =
        MoveStep(uci, san, title, explanation, principle)
}
