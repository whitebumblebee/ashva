import json, sys, os, importlib.util
here = os.path.dirname(__file__)
def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(here, name + ".py")); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m
_im = load("ruy_intros"); intros = _im.I; intros_by_path = getattr(_im, "IP", {})
plans = load("ruy_plans").P if os.path.exists(os.path.join(here, "ruy_plans.py")) else {}
nodes_w = load("ruy_nodes").N if os.path.exists(os.path.join(here, "ruy_nodes.py")) else {}
ROOT = sys.argv[1]
WRITER = "Claude Code (claude-opus-5-5), agent mode: written from master/club statistics, winners' move patterns and engine facts, then machine-checked"
toc = json.load(open(f"{ROOT}/map/toc.json"))
chapter_intro = {
 "berlin": ["The Berlin Defence is Black's most solid answer to the Ruy Lopez and a favourite of world champions. White chooses between the forcing main line, where queens often come off early, and calmer protecting setups that keep the pieces on the board."],
 "exchange": ["In the Exchange Variation White gives up the Spanish bishop to damage Black's pawn structure. The play revolves around White's healthy kingside majority against Black's bishop pair and quick development."],
 "open": ["In the Open Ruy Lopez Black takes White's centre pawn and keeps it, accepting an open, dynamic position with free piece play in return for a slightly loose centre."],
 "closed": ["The Closed Ruy Lopez is the classical heart of the opening: long manoeuvring battles where White builds a strong centre and Black chooses between several famous regrouping systems before the central break."],
 "marshall": ["Here Black castles early to prepare a central pawn sacrifice. White either allows the Marshall Attack and defends, or avoids it with an anti-Marshall move that attacks Black's queenside first."],
 "morphy-other": ["Other main systems after Black asks the bishop: modern active bishop lines, the solid Steinitz setups, early protecting moves for White and less common knight moves."],
 "schliemann": ["The Schliemann is Black's sharpest reply: an immediate strike with the f-pawn that leads to open, tactical positions where both sides must know the critical ideas."],
 "classical": ["In the Classical lines Black develops the dark-squared bishop actively at once; White usually gains time by building a big pawn centre."],
 "steinitz": ["The old Steinitz Defence protects the centre pawn at once with a pawn, solid but passive, giving White a comfortable space advantage."],
 "other": ["Less common third moves for Black. Each has a clear idea, and knowing the best reply gives White an easy, comfortable game."],
}
def idea(t): return {"type": "IDEA", "text": t}
for c in toc:
    if c["id"] == "firouzja-carlsen-2020": continue  # GAME chapter: writing comes from the pilot claims file
    vw = {}
    for tv in c["variations"]:
        name = tv["name"]; entry = {}
        it = intros.get(name) or intros_by_path.get(tv["path"])
        if it: entry["intro"] = [idea(t) for t in it]
        pl = plans.get(name) or plans.get(tv["path"])
        if pl:
            entry["white"] = pl.get("white", []); entry["black"] = pl.get("black", [])
        if entry:
            assert name not in vw, f"duplicate variation name {name}"
            vw[name] = entry
    nodes = {k: v for k, v in nodes_w.items() if v.get("chapter") == c["id"]}
    for v in nodes.values(): v.pop("chapter", None)
    data = {"writer": WRITER, "intro": [idea(t) for t in chapter_intro.get(c["id"], [])], "nodes": nodes, "variations": vw}
    os.makedirs(f"{ROOT}/claims", exist_ok=True)
    json.dump(data, open(f"{ROOT}/claims/{c['id']}.json", "w"), indent=1, ensure_ascii=False)
    print(c["id"], "variation intros", sum(1 for e in vw.values() if "intro" in e), "plans", sum(1 for e in vw.values() if "white" in e or "black" in e), "nodes", len(nodes))
