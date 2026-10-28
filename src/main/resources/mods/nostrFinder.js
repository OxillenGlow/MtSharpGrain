// Drop in: worlds/<your_world>/mod/UTILnostr/nostr.js
// UTIL prefix keeps the button drawn. Do not suffix MAIN — signing stalls a frame.
//
// No public WebSocket tells you your own IP. HOST_IP stays a saved setting.
// Chat is a list. Keypad types a message or a username. "block" hides that pubkey.

var HOST_IP = "127.0.0.1";
var TAG = "msgplayerfinder";
var RELAYS = [
    "wss://nos.lol",
    "wss://relay.damus.io",
    "wss://nostr.mom",
    "wss://relay.snort.social",
    "wss://nostr.bitcoiner.social",
    "wss://relay.nostr.com"
];

function hexToBytes(hex) {
    var out = new Uint8Array(hex.length / 2);
    for (var i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
}
function bytesToHex(bytes) {
    var s = "";
    for (var i = 0; i < bytes.length; i++) {
        var h = bytes[i].toString(16);
        s += h.length === 1 ? "0" + h : h;
    }
    return s;
}
function utf8(str) {
    var out = [];
    for (var i = 0; i < str.length; i++) {
        var c = str.charCodeAt(i);
        if (c < 0x80) out.push(c);
        else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
        else out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
    }
    return Uint8Array.from(out);
}
function concatBytes(parts) {
    var n = 0;
    for (var i = 0; i < parts.length; i++) n += parts[i].length;
    var out = new Uint8Array(n);
    var o = 0;
    for (var p = 0; p < parts.length; p++) { out.set(parts[p], o); o += parts[p].length; }
    return out;
}
function rotr(x, n) { return ((x >>> n) | (x << (32 - n))) >>> 0; }
function sha256(msg) {
    var K = [0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
    var bitLen = msg.length * 8;
    var withOne = msg.length + 1;
    var padLen = (withOne % 64 <= 56) ? (56 - (withOne % 64)) : (56 + 64 - (withOne % 64));
    var buf = new Uint8Array(msg.length + 1 + padLen + 8);
    buf.set(msg, 0);
    buf[msg.length] = 0x80;
    var hi = Math.floor(bitLen / 0x100000000);
    var lo = bitLen >>> 0;
    var end = buf.length;
    buf[end-8]=(hi>>>24)&255; buf[end-7]=(hi>>>16)&255; buf[end-6]=(hi>>>8)&255; buf[end-5]=hi&255;
    buf[end-4]=(lo>>>24)&255; buf[end-3]=(lo>>>16)&255; buf[end-2]=(lo>>>8)&255; buf[end-1]=lo&255;
    var H = [0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
    var w = new Array(64);
    for (var off = 0; off < buf.length; off += 64) {
        for (var i = 0; i < 16; i++) {
            var j = off + i * 4;
            w[i] = ((buf[j]<<24)|(buf[j+1]<<16)|(buf[j+2]<<8)|buf[j+3])>>>0;
        }
        for (var t = 16; t < 64; t++) {
            var s0 = rotr(w[t-15],7)^rotr(w[t-15],18)^(w[t-15]>>>3);
            var s1 = rotr(w[t-2],17)^rotr(w[t-2],19)^(w[t-2]>>>10);
            w[t] = (w[t-16]+s0+w[t-7]+s1)>>>0;
        }
        var a=H[0],b=H[1],c=H[2],d=H[3],e=H[4],f=H[5],g=H[6],h=H[7];
        for (var t2 = 0; t2 < 64; t2++) {
            var S1 = rotr(e,6)^rotr(e,11)^rotr(e,25);
            var ch = (e&f)^(~e&g);
            var t1 = (h+S1+ch+K[t2]+w[t2])>>>0;
            var S0 = rotr(a,2)^rotr(a,13)^rotr(a,22);
            var maj = (a&b)^(a&c)^(b&c);
            var t2n = (S0+maj)>>>0;
            h=g; g=f; f=e; e=(d+t1)>>>0; d=c; c=b; b=a; a=(t1+t2n)>>>0;
        }
        H[0]=(H[0]+a)>>>0; H[1]=(H[1]+b)>>>0; H[2]=(H[2]+c)>>>0; H[3]=(H[3]+d)>>>0;
        H[4]=(H[4]+e)>>>0; H[5]=(H[5]+f)>>>0; H[6]=(H[6]+g)>>>0; H[7]=(H[7]+h)>>>0;
    }
    var out = new Uint8Array(32);
    for (var k = 0; k < 8; k++) {
        out[k*4]=(H[k]>>>24)&255; out[k*4+1]=(H[k]>>>16)&255; out[k*4+2]=(H[k]>>>8)&255; out[k*4+3]=H[k]&255;
    }
    return out;
}

var P = BigInt("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F");
var N = BigInt("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141");
var G = [BigInt("55066263022277343669578718895168534326250603453777594175500187360389116729240"), BigInt("32670510020758816978083085130507043184471273380659243275938904335757337482424")];
function mod(a) { var r = a % P; return r >= 0n ? r : r + P; }
function modN(a) { var r = a % N; return r >= 0n ? r : r + N; }
function modPow(base, exp, m) {
    var result = 1n, b = base % m, e = exp;
    while (e > 0n) { if (e & 1n) result = (result * b) % m; b = (b * b) % m; e >>= 1n; }
    return result;
}
function inv(a) { return modPow(mod(a), P - 2n, P); }
function pointAdd(A, B) {
    if (!A) return B; if (!B) return A;
    var x1=A[0], y1=A[1], x2=B[0], y2=B[1];
    if (x1 === x2) { if (y1 !== y2 || y1 === 0n) return null; return pointDbl(A); }
    var slope = mod((y2 - y1) * inv(x2 - x1));
    var x3 = mod(slope * slope - x1 - x2);
    var y3 = mod(slope * (x1 - x3) - y1);
    return [x3, y3];
}
function pointDbl(A) {
    if (!A || A[1] === 0n) return null;
    var slope = mod((3n * A[0] * A[0]) * inv(2n * A[1]));
    var x3 = mod(slope * slope - 2n * A[0]);
    var y3 = mod(slope * (A[0] - x3) - A[1]);
    return [x3, y3];
}
function pointMul(k, Point) {
    var result = null, addend = Point, scalar = k;
    while (scalar > 0n) {
        if (scalar & 1n) result = pointAdd(result, addend);
        addend = pointDbl(addend);
        scalar >>= 1n;
    }
    return result;
}
function bytes32FromBig(n) {
    var hex = n.toString(16);
    while (hex.length < 64) hex = "0" + hex;
    return hexToBytes(hex);
}
function bigFromBytes(bytes) { return BigInt("0x" + bytesToHex(bytes)); }
function taggedHash(tag, msg) {
    var tagHash = sha256(utf8(tag));
    return sha256(concatBytes([tagHash, tagHash, msg]));
}
function hasEvenY(point) { return (point[1] & 1n) === 0n; }
function xOnly(point) { return bytes32FromBig(point[0]); }
function xorBytes(a, b) {
    var out = new Uint8Array(a.length);
    for (var i = 0; i < a.length; i++) out[i] = a[i] ^ b[i];
    return out;
}
function pubkeyFromSecret(skBytes) {
    var d0 = bigFromBytes(skBytes);
    if (d0 <= 0n || d0 >= N) throw new Error("bad secret key");
    return bytesToHex(xOnly(pointMul(d0, G)));
}
function schnorrSign(msg32, skBytes, aux32) {
    var d0 = bigFromBytes(skBytes);
    if (d0 <= 0n || d0 >= N) throw new Error("bad secret key");
    var Ppoint = pointMul(d0, G);
    var d = hasEvenY(Ppoint) ? d0 : N - d0;
    var t = xorBytes(bytes32FromBig(d), taggedHash("BIP0340/aux", aux32));
    var k0 = modN(bigFromBytes(taggedHash("BIP0340/nonce", concatBytes([t, xOnly(Ppoint), msg32]))));
    if (k0 === 0n) throw new Error("bad nonce");
    var R = pointMul(k0, G);
    var k = hasEvenY(R) ? k0 : N - k0;
    var e = modN(bigFromBytes(taggedHash("BIP0340/challenge", concatBytes([xOnly(R), xOnly(Ppoint), msg32]))));
    var sig = new Uint8Array(64);
    sig.set(xOnly(R), 0);
    sig.set(bytes32FromBig(modN(k + e * d)), 32);
    return sig;
}
function randomBytes(n) {
    var out = new Uint8Array(n);
    for (var i = 0; i < n; i++) out[i] = Math.floor(Math.random() * 256);
    return out;
}
function loadOrCreateSecret() {
    var saved = Data.get("sk");
    if (saved && saved.length === 64 && /^[0-9a-fA-F]+$/.test(saved)) return saved.toLowerCase();
    var sk = bytesToHex(randomBytes(32));
    var d = BigInt("0x" + sk);
    if (d <= 0n || d >= N) sk = "3".padStart(64, "0");
    Data.save(sk, "sk");
    return sk;
}
function loadHostIp() {
    var saved = Data.get("hostIp");
    if (saved && saved.length > 0) return saved;
    Data.save(HOST_IP, "hostIp");
    return HOST_IP;
}
function signEvent(skHex, content, nameTag) {
    var pk = pubkeyFromSecret(hexToBytes(skHex));
    var tags = [["t", TAG]];
    if (nameTag) tags.push(["n", nameTag]);
    var created = Math.floor(Date.now() / 1000);
    var body = JSON.stringify([0, pk, created, 1, tags, content]);
    var idBytes = sha256(utf8(body));
    var sig = schnorrSign(idBytes, hexToBytes(skHex), randomBytes(32));
    return { id: bytesToHex(idBytes), pubkey: pk, created_at: created, kind: 1, tags: tags, content: content, sig: bytesToHex(sig) };
}

// ---------------------------------------------------------------------------
// relays + chat + keypad
// ---------------------------------------------------------------------------

var VISIBLE = 6;
var secretHex = loadOrCreateSecret();
var publicHex = pubkeyFromSecret(hexToBytes(secretHex));
var hostIp = loadHostIp();
var username = Data.get("name");
if (!username) username = "anon";
var sockets = {};
var seen = {};
var openCount = 0;
var history = [];
var blocked = {};
var draft = "";
var naming = false;
var statusText = "connecting";

(function loadBlocked() {
    var raw = Data.get("blocked");
    if (!raw) return;
    var parts = raw.split(",");
    for (var i = 0; i < parts.length; i++) {
        if (parts[i]) blocked[parts[i]] = true;
    }
})();

function shortHex(hex) {
    if (!hex || hex.length < 12) return hex || "?";
    return hex.slice(0, 8);
}

function saveBlocked() {
    var keys = [];
    for (var k in blocked) {
        if (blocked[k]) keys.push(k);
    }
    Data.save(keys.join(","), "blocked");
}

function paint() {
    Gui.guiWord(statusText, 0.5, 0.96, 0, 0.018, "nostr_status");
    Gui.guiWord("chat anonymously with other online mtsharpgrain players via nostr", 0.5, 0.90, 0, 0.015, "somevoid");
    var mode = naming ? "name: " : (username + ": ");
    Gui.guiWord(mode + (draft || "_"), 0.5, 0.42, 0, 0.022, "nostr_draft");

    var start = history.length > VISIBLE ? history.length - VISIBLE : 0;
    for (var i = 0; i < VISIBLE; i++) {
        var row = history[start + i];
        var y = 0.90 - i * 0.07;
        var label = row ? (row.name + "  " + row.text) : "";
        if (label.length > 42) label = label.slice(0, 42);
        var line = Gui.guiWord(label, 0.40, y, 0, 0.02, "nostr_line_" + i);
        Gui.setColor(line, 1, 1, 1, 1);
        var blockLabel = row ? "block" : "";
        var blk = Gui.guiWord(blockLabel, 0.86, y, 0, 0.018, "nostr_blk_" + i);
        Gui.setColor(blk, 1, 0.45, 0.4, 1);
    }

    var rows = ["abcdefghi", "jklmnopqr", "stuvwxyz"];
    for (var r = 0; r < rows.length; r++) {
        var letters = rows[r];
        for (var c = 0; c < letters.length; c++) {
            var x = 0.14 + c * 0.09;
            Gui.guiWord("< " + letters.charAt(c) + " >", x, 0.34 - r * 0.045, 0, 0.022, "key_" + letters.charAt(c));
        }
    }
    for (var n = 0; n < 10; n++) {
        Gui.guiWord(String(n), 0.10 + n * 0.08, 0.20, 0, 0.022, "key_" + n);
    }
    Gui.guiWord("del", 0.18, 0.14, 0, 0.02, "key_del");
    Gui.guiWord("send", 0.38, 0.14, 0, 0.02, "key_send");
    Gui.guiWord("space", 0.28, 0.14, 0, 0.02, "key_space");
    Gui.guiWord(naming ? "done" : "name", 0.56, 0.14, 0, 0.02, "key_name");
    var hostBtn = Gui.guiWord("MSGHOST " + hostIp, 0.78, 0.14, 0, 0.018, "nostr_broadcast");
    Gui.setColor(hostBtn, 0.45, 0.75, 1, 1);
}

function pushChat(pk, name, text) {
    if (blocked[pk]) return;
    history.push({ pk: pk, name: name || shortHex(pk), text: text || "" });
    if (history.length > 40) history.shift();
    paint();
}

function nameFromEvent(ev) {
    var tags = ev.tags || [];
    for (var i = 0; i < tags.length; i++) {
        if (tags[i] && tags[i][0] === "n" && tags[i][1]) return tags[i][1];
    }
    return shortHex(ev.pubkey);
}

function onNostrMessage(text) {
    var msg;
    try { msg = JSON.parse(text); } catch (e) { return; }
    if (!msg || !msg.length) return;
    var type = msg[0];
    if (type === "EVENT") {
        var ev = msg[2];
        if (!ev || !ev.id || seen[ev.id]) return;
        if (ev.kind !== 1) return;
        seen[ev.id] = true;
        if (blocked[ev.pubkey]) return;
        pushChat(ev.pubkey, nameFromEvent(ev), ev.content || "");
    } else if (type === "OK" && !msg[2]) {
        statusText = "rejected " + (msg[3] || "");
        paint();
    } else if (type === "NOTICE") {
        statusText = "notice " + msg[1];
        paint();
    }
}

function subscribe(ws) {
    var since = Math.floor(Date.now() / 1000) - 3600;
    ws.send(JSON.stringify(["REQ", "mpf", { kinds: [1], "#t": [TAG], since: since }]));
}

function connectRelay(url) {
    if (sockets[url] && sockets[url].isOpen()) return;
    var ws = Ws.connect(url);
    sockets[url] = ws;
    ws.onOpen(function () {
        openCount++;
        statusText = "relays " + openCount + "/" + RELAYS.length;
        subscribe(ws);
        paint();
    });
    ws.onMessage(function (text) {
        onNostrMessage(text);
    });
    ws.onError(function (message) {
        statusText = message;
        paint();
    });
    ws.onClose(function () {
        if (sockets[url] === ws) {
            if (openCount > 0) openCount--;
            delete sockets[url];
            statusText = "relays " + openCount + "/" + RELAYS.length;
            paint();
            Engine.setTimeout(function () { connectRelay(url); }, 5000);
        }
    });
}

function publish(content, nameTag) {
    var event;
    try {
        event = signEvent(secretHex, content, nameTag);
    } catch (e) {
        statusText = "sign failed";
        paint();
        return 0;
    }
    var payload = JSON.stringify(["EVENT", event]);
    var sent = 0;
    for (var i = 0; i < RELAYS.length; i++) {
        var ws = sockets[RELAYS[i]];
        if (ws && ws.isOpen()) {
            ws.send(payload);
            sent++;
        }
    }
    seen[event.id] = true;
    statusText = sent === 0 ? "no open relay" : ("sent to " + sent);
    paint();
    return sent;
}

function typeChar(ch) {
    var cap = naming ? 12 : 48;
    if (draft.length >= cap) return;
    draft += ch;
    paint();
}

function blockSlot(slot) {
    var start = history.length > VISIBLE ? history.length - VISIBLE : 0;
    var row = history[start + slot];
    if (!row || !row.pk) return;
    blocked[row.pk] = true;
    saveBlocked();
    var kept = [];
    for (var i = 0; i < history.length; i++) {
        if (!blocked[history[i].pk]) kept.push(history[i]);
    }
    history = kept;
    paint();
}

var alphabet = "abcdefghijklmnopqrstuvwxyz";
for (var a = 0; a < alphabet.length; a++) {
    (function (ch) {
        Engine.onTick(function () { typeChar(ch); }, "key_" + ch);
    })(alphabet.charAt(a));
}
for (var d = 0; d < 10; d++) {
    (function (ch) {
        Engine.onTick(function () { typeChar(ch); }, "key_" + ch);
    })(String(d));
}

Engine.onTick(function () {
    if (draft.length > 0) draft = draft.slice(0, draft.length - 1);
    paint();
}, "key_del");

Engine.onTick(function () {
    if (naming) {
        if (draft.length > 0) {
            username = draft;
            Data.save(username, "name");
        }
        draft = "";
        naming = false;
        paint();
        return;
    }
    if (!draft) return;
    var text = draft;
    draft = "";
    if (publish(text, username)) pushChat(publicHex, username, text);
}, "key_send");

Engine.onTick(function () {
    if (naming) {
        // press "done"
        if (draft.length > 0) {
            username = draft;
            Data.save(username, "name");
        }
        draft = "";
        naming = false;
    } else {
        // press "name"
        naming = true;
        draft = username;
    }
    paint();
}, "key_name");

Engine.onTick(function () {
    typeChar(" ");
}, "key_space");

Engine.onTick(function () {
    publish("MSGHOST: " + hostIp, username);
    pushChat(publicHex, username, "MSGHOST: " + hostIp);
}, "nostr_broadcast");

for (var s = 0; s < VISIBLE; s++) {
    (function (slot) {
        Engine.onTick(function () { blockSlot(slot); }, "nostr_blk_" + s);
    })(s);
}

paint();
for (var r = 0; r < RELAYS.length; r++) connectRelay(RELAYS[r]);
