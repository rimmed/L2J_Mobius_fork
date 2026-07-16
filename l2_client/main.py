#!/usr/bin/env python3
"""
L2J Mobius Interlude client bot — connects, enters world, moves around and chats.
"""

import socket
import struct
import time
import random

from Crypto.Cipher import Blowfish

# ─── CONFIGURATION ───
SERVER_IP = ""
LOGIN_PORT = 2106
ACCOUNT_LOGIN = ""
ACCOUNT_PASSWORD = ""
SERVER_ID = 0
CHAR_SLOT = 0

L2MOBIUS_STATIC_KEY = b'\x6b\x60\xcb\x5b\x82\xce\x90\xb1\xcc\x2b\x6c\x55\x6c\x6c\x6c\x6c'

PACKET_NAMES = {
    0x00: "KeyPacket", 0x01: "MoveToLoc", 0x03: "CharInfo", 0x04: "UserInfo",
    0x0B: "SpawnItem", 0x0E: "StatusUpd", 0x13: "CharSelInfo", 0x14: "LoginFail",
    0x15: "CharSelected", 0x16: "NpcInfo", 0x1B: "ItemList",
    0x1C: "Sunrise", 0x1D: "Sunset", 0x26: "ServerClose",
    0x27: "InvUpdate", 0x4A: "CreatureSay", 0x58: "SkillList",
    0x64: "SysMsg", 0x7F: "AbnormalSt", 0x80: "QuestList",
    0xA8: "NetPing", 0xC1: "SkillCool", 0xF3: "EtcStatus",
    0xF9: "GGQuery", 0xFE: "ExPacket",
}


def blowfish_ecb_crypt(data: bytearray, key: bytes, encrypt: bool) -> bytearray:
    """Blowfish ECB with LE ↔ BE conversion to match L2J BlowfishEngine."""
    be = bytearray(len(data))
    for i in range(0, len(data), 8):
        w0, w1 = struct.unpack_from("<II", data, i)
        struct.pack_into(">II", be, i, w0, w1)
    c = Blowfish.new(key, Blowfish.MODE_ECB)
    r = c.encrypt(bytes(be)) if encrypt else c.decrypt(bytes(be))
    for i in range(0, len(data), 8):
        w0, w1 = struct.unpack_from(">II", r, i)
        struct.pack_into("<II", data, i, w0, w1)
    return data


# ── Crypto ──

def unscramble_modulus(mod: bytearray):
    for i in range(0x40): mod[0x40 + i] ^= mod[i]
    for i in range(4): mod[0x0d + i] ^= mod[0x34 + i]
    for i in range(0x40): mod[i] ^= mod[0x40 + i]
    for i in range(4): mod[0x00 + i], mod[0x4d + i] = mod[0x4d + i], mod[0x00 + i]

def dec_xor_pass(b: bytearray):
    s, sp, pk = len(b), len(b) - 8, struct.unpack_from("<I", b, len(b) - 8)[0]
    cp = sp - 4
    while cp >= 4:
        ev = struct.unpack_from("<I", b, cp)[0]
        dv = ev ^ pk; pk = (pk - dv) & 0xFFFFFFFF
        struct.pack_into("<I", b, cp, dv); cp -= 4
    return b

def append_l2_checksum(b: bytearray):
    s, cs, cnt = len(b), 0, len(b) - 4
    for i in range(0, cnt, 4): cs ^= struct.unpack_from("<I", b, i)[0]
    struct.pack_into("<I", b, cnt, cs)

def decrypt_init_packet(raw: bytes, key: bytes) -> bytearray:
    b = bytearray(raw); blowfish_ecb_crypt(b, key, False); return dec_xor_pass(b)

def decrypt_l2_packet(raw: bytes, key: bytes) -> bytearray:
    b = bytearray(raw); return blowfish_ecb_crypt(b, key, False)

def encrypt_l2_packet(raw: bytes, key: bytes) -> bytes:
    b = bytearray(raw); b.extend(b'\x00' * 4)
    if len(b) % 8: b.extend(b'\x00' * (8 - len(b) % 8))
    append_l2_checksum(b); blowfish_ecb_crypt(b, key, True)
    return struct.pack("<H", len(b) + 2) + b

def send_encrypted(sock, body: bytes, key: bytes):
    sock.sendall(encrypt_l2_packet(body, key))

def recv_packet(sock: socket.socket) -> bytes:
    h = sock.recv(2)
    if not h or len(h) != 2: raise ConnectionError("recv header")
    sz = struct.unpack_from("<H", h)[0]
    p = bytearray()
    while len(p) < sz - 2:
        c = sock.recv(sz - 2 - len(p))
        if not c: raise ConnectionError("recv payload")
        p.extend(c)
    return bytes(p)

def send_raw(sock, data): sock.sendall(data)

# ── Packet builders ──

def pack(p: bytearray) -> bytes:
    return struct.pack("<H", len(p) + 2) + p

def build_auth_game_guard(sid: int) -> bytes:
    return bytes(bytearray([0x07]) + struct.pack("<I", sid) + b'\x00' * 16)

def build_request_auth_login(login: str, pw: str, sid: int, rsa_mod: bytes) -> bytes:
    r = bytearray(128); r[0] = 0x80
    r[0x5E:0x5E+14] = login.encode('utf-8').ljust(14, b'\x00')
    r[0x6C:0x6C+16] = pw.encode('utf-8').ljust(16, b'\x00')
    struct.pack_into("<I", r, 124, sid)
    m = int.from_bytes(rsa_mod, 'big')
    return bytes(bytearray([0x00]) + pow(int.from_bytes(r, 'big'), 65537, m).to_bytes(128, 'big') + b'\x00' * 8)

def build_request_server_list(l1: int, l2: int) -> bytes:
    return bytes(bytearray([0x05]) + struct.pack("<II", l1, l2))

def build_request_server_login(l1: int, l2: int, sid: int) -> bytes:
    return bytes(bytearray([0x02]) + struct.pack("<II", l1, l2) + bytes([sid]))

def build_proto_version(v: int = 746) -> bytes:
    return pack(bytearray([0x00]) + struct.pack("<I", v))

def build_auth_login_gs(name: str, pk2: int, pk1: int, lk1: int, lk2: int) -> bytes:
    p = bytearray([0x08]) + name.encode('utf-16-le') + b'\x00\x00'
    p += struct.pack("<IIII", pk2, pk1, lk1, lk2)
    return pack(p)

def build_character_select(slot: int) -> bytes:
    p = bytearray([0x0D]) + struct.pack("<I", slot)
    p += struct.pack("<H", 0) + struct.pack("<III", 0, 0, 0)
    return pack(p)

def build_enter_world() -> bytes:
    p = bytearray([0x03]) + b'\x00' * 32 + struct.pack("<IIII", 0, 0, 0, 0)
    p += b'\x00' * 32 + struct.pack("<I", 0) + b'\x00' * 20
    return pack(p)

def build_net_ping(pid: int) -> bytes:
    return pack(bytearray([0xA8]) + struct.pack("<I", pid))

# ── Game action packet builders (0x01 MoveToLocation, 0x38 Say2) ──

def build_move_to_location(tx: int, ty: int, tz: int,
                           ox: int, oy: int, oz: int, mode: int = 1) -> bytes:
    """Packet 0x01: MoveToLocation — 7 ints (target+origin x/y/z, movementMode)."""
    return pack(bytearray([0x01]) + struct.pack("<iiiiiii", tx, ty, tz, ox, oy, oz, mode))

def build_say2(msg: str, chat_type: int = 0, target: str = "") -> bytes:
    """
    Packet 0x38: Say2.
    chat_type: 0=ALL 1=SHOUT 2=WHISPER 3=PARTY 4=CLAN 8=TRADE
    """
    p = bytearray([0x38])
    p += msg.encode('utf-16-le') + b'\x00\x00'
    p += struct.pack("<I", chat_type)
    if chat_type == 2 and target:
        p += target.encode('utf-16-le') + b'\x00\x00'
    return pack(p)

# ── Login server flow ──

def login_server_flow(sock: socket.socket):
    di = decrypt_init_packet(recv_packet(sock), L2MOBIUS_STATIC_KEY)
    assert di[0] == 0x00
    sid = struct.unpack_from("<I", di, 1)[0]
    rm = bytearray(di[9:9+128]); unscramble_modulus(rm)
    dk = di[9+128+16:9+128+16+16]
    print(f"[LS]  Session: {sid}")
    send_encrypted(sock, build_auth_game_guard(sid), dk)
    decrypt_l2_packet(recv_packet(sock), dk)
    print("[LS]  GGAuth OK")
    send_encrypted(sock, build_request_auth_login(ACCOUNT_LOGIN, ACCOUNT_PASSWORD, sid, rm), dk)
    dr = decrypt_l2_packet(recv_packet(sock), dk)
    if dr[0] == 0x01: raise RuntimeError(f"Login failed: {dr[1]}")
    assert dr[0] == 0x03
    l1 = struct.unpack_from("<I", dr, 1)[0]; l2 = struct.unpack_from("<I", dr, 5)[0]
    print("[LS]  LoginOk!")
    send_encrypted(sock, build_request_server_list(l1, l2), dk)
    ds = decrypt_l2_packet(recv_packet(sock), dk)
    assert ds[0] == 0x04
    sc = ds[1]; pos = 3; servers = []
    for i in range(sc):
        si = ds[pos]; pos += 1; ib = ds[pos:pos+4]; pos += 4
        port = struct.unpack_from("<I", ds, pos)[0]; pos += 4
        pos += 1; pos += 1; pos += 2; pos += 2; pos += 1; pos += 4; pos += 1
        servers.append({'id': si, 'ip': f"{ib[0]}.{ib[1]}.{ib[2]}.{ib[3]}", 'port': port})
    ch = servers[SERVER_ID]
    print(f"[LS]  Selected: {ch['ip']}:{ch['port']}")
    send_encrypted(sock, build_request_server_login(l1, l2, ch['id']), dk)
    dp = decrypt_l2_packet(recv_packet(sock), dk)
    assert dp[0] == 0x07
    p1 = struct.unpack_from("<I", dp, 1)[0]; p2 = struct.unpack_from("<I", dp, 5)[0]
    print("[LS]  PlayOk!")
    return (p1, p2, ch['ip'], ch['port'], ACCOUNT_LOGIN, l1, l2)

# ── GS flow (with movement & chat) ──

def game_server_flow(ip: str, port: int, login_name: str,
                     play_ok1: int, play_ok2: int,
                     login_ok1: int, login_ok2: int):
    gs = socket.socket(socket.AF_INET, socket.SOCK_STREAM); gs.settimeout(60.0)
    try:
        print(f"\n[GS] Connecting to {ip}:{port}...")
        gs.connect((ip, port))

        send_raw(gs, build_proto_version(746))
        kp = recv_packet(gs)
        assert kp[0] == 0x00
        print(f"[GS]  KeyPacket: key={bytes(kp[2:10]).hex()}")

        send_raw(gs, build_auth_login_gs(login_name, play_ok2, play_ok1, login_ok1, login_ok2))
        print("[GS]  AuthLogin sent")

        csi = bytearray(recv_packet(gs))
        if csi[0] == 0x14: raise RuntimeError(f"GS Auth failed, reason: {csi[1]}")
        assert csi[0] == 0x13
        cc = struct.unpack_from("<I", csi, 1)[0]
        print(f"[GS]  Characters: {cc}")
        if cc == 0: raise RuntimeError("No characters")

        pos = 5
        for i in range(cc):
            nb = bytearray()
            while True:
                if pos + 2 > len(csi): break
                ch = struct.unpack_from("<H", csi, pos)[0]; pos += 2
                if ch == 0: break
                nb += struct.pack("<H", ch)
            name = nb.decode('utf-16-le', errors='replace')
            oid = struct.unpack_from("<I", csi, pos)[0]; pos += 4
            print(f"[GS]   Slot {i}: '{name}' (objId={oid})")
            if i >= CHAR_SLOT: break
            while True:
                if pos + 2 > len(csi): break
                if struct.unpack_from("<H", csi, pos)[0] == 0: pos += 2; break
                pos += 2
            pos += 200

        print(f"[GS] Selecting character slot {CHAR_SLOT}...")
        send_raw(gs, build_character_select(CHAR_SLOT))
        assert recv_packet(gs)[0] == 0x15
        print("[GS]  CharSelected OK")

        print("[GS] Sending EnterWorld...")
        send_raw(gs, build_enter_world())
        print("[GS]  Entered world!\n")

        ping_counter = 0; last_ping = time.time()
        last_action = time.time()
        cur_x, cur_y, cur_z = 0, 0, 0
        got_coords, said_hello = False, False

        while True:
            try:
                gs.settimeout(2.0)
                body = bytearray(recv_packet(gs))
                if len(body) == 0: continue
                pid = body[0]

                if pid in (0x04, 0x03, 0x64, 0x4A, 0xA8):
                    print(f"[GS]  ← 0x{pid:02X} {PACKET_NAMES.get(pid,'?')} ({len(body)}b)")

                # Extract position from UserInfo (0x04) — x at offset ~87 in Interlude
                if pid == 0x04 and not got_coords and len(body) > 98:
                    cur_x = struct.unpack_from("<i", body, 87)[0]
                    cur_y = struct.unpack_from("<i", body, 91)[0]
                    cur_z = struct.unpack_from("<i", body, 95)[0]
                    got_coords = True
                    print(f"[GS]  Spawn position: ({cur_x}, {cur_y}, {cur_z})")

                # Handle NetPing
                if pid == 0xA8:
                    send_raw(gs, build_net_ping(struct.unpack_from("<I", body, 1)[0]))
                elif pid == 0x26:
                    print("[GS]  [!] Server closing!"); break
                elif pid == 0xF9:
                    gg = bytearray([0xCA]) + struct.pack("<I", 0)
                    send_raw(gs, pack(gg))
                elif pid == 0x4A:
                    nb = bytearray(); pos = 6
                    while pos + 2 <= len(body):
                        ch = struct.unpack_from("<H", body, pos)[0]; pos += 2
                        if ch == 0: break
                        nb += struct.pack("<H", ch)
                    name = nb.decode('utf-16-le', errors='replace')
                    pos += 4
                    mb = bytearray()
                    while pos + 2 <= len(body):
                        ch = struct.unpack_from("<H", body, pos)[0]; pos += 2
                        if ch == 0: break
                        mb += struct.pack("<H", ch)
                    msg = mb.decode('utf-16-le', errors='replace')
                    print(f"[GS]      [{name}]: {msg}")

                # 👋 Say hello once after EnterWorld
                if got_coords and not said_hello:
                    send_raw(gs, build_say2("Hello world! I am a bot.", 0))
                    print("[GS]  → 'Hello world! I am a bot.'")
                    said_hello = True

                # 🏃 Move every 3 seconds
                if got_coords and time.time() - last_action > 3:
                    dx = random.randint(-300, 300)
                    dy = random.randint(-300, 300)
                    send_raw(gs, build_move_to_location(
                        cur_x + dx, cur_y + dy, cur_z,
                        cur_x, cur_y, cur_z, 1))
                    print(f"[GS]  → MoveTo ({cur_x},{cur_y}) → ({cur_x+dx},{cur_y+dy})")
                    cur_x += dx; cur_y += dy
                    last_action = time.time()

                # Ping every 5s
                if time.time() - last_ping > 5:
                    send_raw(gs, build_net_ping(ping_counter))
                    ping_counter += 1; last_ping = time.time()

            except socket.timeout:
                if time.time() - last_ping > 5:
                    send_raw(gs, build_net_ping(ping_counter))
                    ping_counter += 1; last_ping = time.time()
            except ConnectionError:
                print("[GS]  Connection lost."); break

    except KeyboardInterrupt:
        print("\n[GS]  Disconnected by user.")
    finally:
        gs.close()

# ── Main ──

def main():
    ls_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM); ls_sock.settimeout(10.0)
    try:
        print(f"[+] Connecting to {SERVER_IP}:{LOGIN_PORT} as '{ACCOUNT_LOGIN}'...")
        ls_sock.connect((SERVER_IP, LOGIN_PORT))
        p1, p2, gs_ip, gs_port, ln, l1, l2 = login_server_flow(ls_sock)
        ls_sock.close()
        game_server_flow(gs_ip, gs_port, ln, p1, p2, l1, l2)
    except Exception as e:
        print(f"[-] Error: {e}")
        import traceback; traceback.print_exc()
    finally:
        try: ls_sock.close()
        except: pass

if __name__ == "__main__":
    main()