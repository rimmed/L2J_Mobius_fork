#!/usr/bin/env python3
"""
L2J Mobius Interlude client bot — connects, enters world, reads full character data.
"""

import math
import socket
import struct
import time
import random

import crypto
import packets
import npc_data
from character import L2Character

# ─── CONFIGURATION ───
SERVER_IP = "192.168.1.200"
LOGIN_PORT = 2106
ACCOUNT_LOGIN = "gather7"
ACCOUNT_PASSWORD = "S!lver=drag0n"
SERVER_ID = 0
CHAR_SLOT = 0


# ── Login server flow ──

def login_server_flow(sock: socket.socket):
    di = crypto.decrypt_init_packet(crypto.recv_packet(sock), crypto.L2MOBIUS_STATIC_KEY)
    assert di[0] == 0x00
    sid = struct.unpack_from("<I", di, 1)[0]
    rm = bytearray(di[9:9 + 128])
    crypto.unscramble_modulus(rm)
    dk = di[9 + 128 + 16:9 + 128 + 16 + 16]
    print(f"[LS]  Session: {sid}")
    crypto.send_encrypted(sock, packets.build_auth_game_guard(sid), dk)
    crypto.decrypt_l2_packet(crypto.recv_packet(sock), dk)
    print("[LS]  GGAuth OK")
    crypto.send_encrypted(sock, packets.build_request_auth_login(ACCOUNT_LOGIN, ACCOUNT_PASSWORD, sid, rm), dk)
    dr = crypto.decrypt_l2_packet(crypto.recv_packet(sock), dk)
    if dr[0] == 0x01:
        raise RuntimeError(f"Login failed: {dr[1]}")
    assert dr[0] == 0x03
    l1 = struct.unpack_from("<I", dr, 1)[0]
    l2 = struct.unpack_from("<I", dr, 5)[0]
    print("[LS]  LoginOk!")
    crypto.send_encrypted(sock, packets.build_request_server_list(l1, l2), dk)
    ds = crypto.decrypt_l2_packet(crypto.recv_packet(sock), dk)
    assert ds[0] == 0x04
    sc = ds[1]
    pos = 3
    servers = []
    for i in range(sc):
        si = ds[pos]; pos += 1
        ib = ds[pos:pos + 4]; pos += 4
        port = struct.unpack_from("<I", ds, pos)[0]; pos += 4
        pos += 1; pos += 1; pos += 2; pos += 2; pos += 1; pos += 4; pos += 1
        servers.append({'id': si, 'ip': f"{ib[0]}.{ib[1]}.{ib[2]}.{ib[3]}", 'port': port})
    ch = servers[SERVER_ID]
    print(f"[LS]  Selected: {ch['ip']}:{ch['port']}")
    crypto.send_encrypted(sock, packets.build_request_server_login(l1, l2, ch['id']), dk)
    dp = crypto.decrypt_l2_packet(crypto.recv_packet(sock), dk)
    assert dp[0] == 0x07
    p1 = struct.unpack_from("<I", dp, 1)[0]
    p2 = struct.unpack_from("<I", dp, 5)[0]
    print("[LS]  PlayOk!")
    return (p1, p2, ch['ip'], ch['port'], ACCOUNT_LOGIN, l1, l2)


# ── GS flow ──

def game_server_flow(ip: str, port: int, login_name: str,
                     play_ok1: int, play_ok2: int,
                     login_ok1: int, login_ok2: int):
    gs = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    gs.settimeout(60.0)
    try:
        print(f"\n[GS] Connecting to {ip}:{port}...")
        gs.connect((ip, port))

        crypto.send_raw(gs, packets.build_proto_version(746))
        kp = crypto.recv_packet(gs)
        assert kp[0] == 0x00
        print(f"[GS]  KeyPacket: key={bytes(kp[2:10]).hex()}")

        crypto.send_raw(gs, packets.build_auth_login_gs(login_name, play_ok2, play_ok1, login_ok1, login_ok2))
        print("[GS]  AuthLogin sent")

        csi = bytearray(crypto.recv_packet(gs))
        if csi[0] == 0x14:
            raise RuntimeError(f"GS Auth failed, reason: {csi[1]}")
        assert csi[0] == 0x13
        cc = struct.unpack_from("<I", csi, 1)[0]
        print(f"[GS]  Characters: {cc}")
        if cc == 0:
            raise RuntimeError("No characters")

        pos = 5
        for i in range(cc):
            nb = bytearray()
            while True:
                if pos + 2 > len(csi):
                    break
                ch = struct.unpack_from("<H", csi, pos)[0]; pos += 2
                if ch == 0:
                    break
                nb += struct.pack("<H", ch)
            name = nb.decode('utf-16-le', errors='replace')
            oid = struct.unpack_from("<I", csi, pos)[0]; pos += 4
            print(f"[GS]   Slot {i}: '{name}' (objId={oid})")
            if i >= CHAR_SLOT:
                break
            while True:
                if pos + 2 > len(csi):
                    break
                if struct.unpack_from("<H", csi, pos)[0] == 0:
                    pos += 2
                    break
                pos += 2
            pos += 200

        print(f"[GS] Selecting character slot {CHAR_SLOT}...")
        crypto.send_raw(gs, packets.build_character_select(CHAR_SLOT))
        assert crypto.recv_packet(gs)[0] == 0x15
        print("[GS]  CharSelected OK")

        print("[GS] Sending EnterWorld...")
        crypto.send_raw(gs, packets.build_enter_world())
        print("[GS]  Entered world!\n")

        # ── Collect character data into L2Character ──
        char = L2Character()
        received_user_info = False
        received_item_list = False
        received_skill_list = False
        received_etc_status = False
        data_dump_printed = False

        ping_counter = 0
        last_ping = time.time()
        cur_x, cur_y, cur_z = 0, 0, 0
        got_coords = False
        data_collection_timeout = 20.0
        enter_time = time.time()

        while True:
            try:
                if not data_dump_printed:
                    gs.settimeout(min(2.0, data_collection_timeout - (time.time() - enter_time)))
                else:
                    gs.settimeout(2.0)

                body = bytearray(crypto.recv_packet(gs))
                if len(body) == 0:
                    continue
                pid = body[0]

                # ── Data-collection phase ──
                if not data_dump_printed:
                    if pid == 0x04:  # UserInfo
                        ui = packets.parse_user_info(body)
                        char.apply_user_info(ui)
                        received_user_info = True
                        cur_x, cur_y, cur_z = char.x, char.y, char.z
                        got_coords = True
                        print(f"[GS]  ← UserInfo parsed: {char.name} lvl {char.level} "
                              f"HP {char.cur_hp:.0f}/{char.max_hp} "
                              f"MP {char.cur_mp:.0f}/{char.max_mp} "
                              f"CP {char.cur_cp:.0f}/{char.max_cp} "
                              f"SP {char.sp} "
                              f"pos ({cur_x},{cur_y},{cur_z})")

                    elif pid == 0x1B:  # ItemList
                        char.apply_item_list(packets.parse_item_list(body))
                        received_item_list = True
                        print(f"[GS]  ← ItemList parsed: {len(char.items)} items")

                    elif pid == 0x58:  # SkillList
                        char.apply_skill_list(packets.parse_skill_list(body))
                        received_skill_list = True
                        print(f"[GS]  ← SkillList parsed: {len(char.skills)} skills")

                    elif pid == 0xF3:  # EtcStatusUpdate
                        char.apply_etc_status_update(packets.parse_etc_status_update(body))
                        received_etc_status = True
                        print(f"[GS]  ← EtcStatusUpdate parsed: "
                              f"charges={char.charges} "
                              f"weight_penalty={char.weight_penalty}")

                    elif pid == 0x0E:  # StatusUpdate
                        su = packets.parse_status_update(body)
                        char.apply_status_update(su)
                        char.radar.update_from_status(su)

                    elif pid == 0x16:  # NpcInfo
                        npc = packets.parse_npc_info(body)
                        char.radar.add_or_update(npc)

                    elif pid == 0x06:  # Die
                        die = packets.parse_die(body)
                        if die:
                            char.radar.remove(die["object_id"])

                    elif pid == 0x08:  # DeleteObject
                        do = packets.parse_delete_object(body)
                        if do:
                            char.radar.remove(do["object_id"])

                    elif pid == 0xA8:  # NetPing
                        crypto.send_raw(gs, packets.build_net_ping(struct.unpack_from("<I", body, 1)[0]))
                        ping_counter += 1
                        last_ping = time.time()

                    elif pid == 0xF9:  # GameGuard
                        gg = bytearray([0xCA]) + struct.pack("<I", 0)
                        crypto.send_raw(gs, packets.pack(gg))

                    elif pid == 0x26:  # ServerClose
                        print("[GS]  [!] Server closing!")
                        break

                    all_core = (received_user_info and received_item_list and
                                received_skill_list and received_etc_status)
                    timed_out = (time.time() - enter_time) > data_collection_timeout
                    if all_core or timed_out:
                        if timed_out and not all_core:
                            print("[GS]  ⚠ Data collection timed out, printing partial data.")
                        char.dump()
                        data_dump_printed = True
                        last_ping = time.time()
                        print("\n[GS]  ── Game loop started ──\n")
                    continue

                # ── Post-collection: game loop ──

                if pid == 0x04 and len(body) >= 13:  # UserInfo update
                    cur_x = struct.unpack_from("<i", body, 1)[0]
                    cur_y = struct.unpack_from("<i", body, 5)[0]
                    cur_z = struct.unpack_from("<i", body, 9)[0]
                    char.x, char.y, char.z = cur_x, cur_y, cur_z
                    got_coords = True
                elif pid == 0x16:  # NpcInfo
                    npc = packets.parse_npc_info(body)
                    char.radar.add_or_update(npc)
                elif pid == 0x0E:  # StatusUpdate
                    su = packets.parse_status_update(body)
                    char.radar.update_from_status(su)
                elif pid == 0x06:  # Die
                    die = packets.parse_die(body)
                    if die:
                        char.radar.remove(die["object_id"])
                elif pid == 0x08:  # DeleteObject
                    do = packets.parse_delete_object(body)
                    if do:
                        char.radar.remove(do["object_id"])

                # Protocol handlers
                if pid == 0xA8:
                    crypto.send_raw(gs, packets.build_net_ping(struct.unpack_from("<I", body, 1)[0]))
                elif pid == 0x26:
                    print("[GS]  [!] Server closing!"); break
                elif pid == 0xF9:
                    gg = bytearray([0xCA]) + struct.pack("<I", 0)
                    crypto.send_raw(gs, packets.pack(gg))
                elif pid == 0x4A:
                    name, msg = packets.parse_creature_say(body)
                    print(f"[GS]      [{name}]: {msg}")

                # Ping every 5s
                if time.time() - last_ping > 5:
                    crypto.send_raw(gs, packets.build_net_ping(ping_counter))
                    ping_counter += 1
                    last_ping = time.time()

                # ── Radar maintenance ──
                if got_coords:
                    char.radar.prune(cur_x, cur_y)
                    char.radar.print_if_due(cur_x, cur_y, cur_z)

            except socket.timeout:
                if data_dump_printed and time.time() - last_ping > 5:
                    crypto.send_raw(gs, packets.build_net_ping(ping_counter))
                    ping_counter += 1
                    last_ping = time.time()
                if not data_dump_printed and (time.time() - enter_time) > data_collection_timeout:
                    print("[GS]  ⚠ Data collection timed out, printing partial data.")
                    char.dump()
                    data_dump_printed = True
                    last_ping = time.time()
                    print("\n[GS]  ── Game loop started ──\n")
            except ConnectionError:
                print("[GS]  Connection lost."); break

    except KeyboardInterrupt:
        print("\n[GS]  Disconnected by user.")
    finally:
        gs.close()


# ── Main ──

def main():
    npc_data.load_npc_names()
    ls_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    ls_sock.settimeout(10.0)
    try:
        print(f"[+] Connecting to {SERVER_IP}:{LOGIN_PORT} as '{ACCOUNT_LOGIN}'...")
        ls_sock.connect((SERVER_IP, LOGIN_PORT))
        p1, p2, gs_ip, gs_port, ln, l1, l2 = login_server_flow(ls_sock)
        ls_sock.close()
        game_server_flow(gs_ip, gs_port, ln, p1, p2, l1, l2)
    except Exception as e:
        print(f"[-] Error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        try:
            ls_sock.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()