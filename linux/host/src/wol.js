import fs from 'node:fs';
import path from 'node:path';
import dgram from 'node:dgram';
import { execSync } from 'node:child_process';

export class WakeOnLanManager {
  getNetworkInterfaces() {
    const netPath = '/sys/class/net';
    if (!fs.existsSync(netPath)) return [];

    const interfaces = [];
    try {
      const dirs = fs.readdirSync(netPath);
      for (const name of dirs) {
        if (name === 'lo') continue;
        const addrPath = path.join(netPath, name, 'address');
        const wirelessPath = path.join(netPath, name, 'wireless');
        const isWifi = fs.existsSync(wirelessPath);

        let mac = null;
        if (fs.existsSync(addrPath)) {
          mac = fs.readFileSync(addrPath, 'utf8').trim();
        }

        let wowlanSupported = false;
        if (isWifi) {
          try {
            const iwOut = execSync('iw phy 2>/dev/null || true', { encoding: 'utf8' });
            wowlanSupported = iwOut.includes('wake up on magic packet') || iwOut.includes('WoWLAN');
          } catch {
            wowlanSupported = false;
          }
        }

        if (mac && mac !== '00:00:00:00:00:00') {
          interfaces.push({
            name,
            mac,
            type: isWifi ? 'wifi' : 'ethernet',
            wowlan: wowlanSupported
          });
        }
      }
    } catch {
      // ignore read error
    }

    return interfaces;
  }

  getPrimaryTarget() {
    const ifaces = this.getNetworkInterfaces();
    const wifi = ifaces.find(i => i.type === 'wifi');
    const eth = ifaces.find(i => i.type === 'ethernet');
    const primary = wifi || eth || ifaces[0] || null;

    return {
      interfaces: ifaces,
      primaryMac: primary ? primary.mac : null
    };
  }

  createMagicPacket(mac) {
    const cleanMac = mac.replace(/[:-]/g, '');
    if (cleanMac.length !== 12 || !/^[0-9a-fA-F]{12}$/.test(cleanMac)) {
      throw new Error(`Invalid MAC address: ${mac}`);
    }

    const macBytes = Buffer.from(cleanMac, 'hex');
    const packet = Buffer.alloc(6 + (16 * 6));

    // 6 bytes of 0xFF
    packet.fill(0xff, 0, 6);

    // 16 repetitions of MAC
    for (let i = 0; i < 16; i++) {
      macBytes.copy(packet, 6 + (i * 6), 0, 6);
    }

    return packet;
  }

  async sendMagicPacket(mac, broadcastIp = '255.255.255.255', port = 9) {
    const packet = this.createMagicPacket(mac);

    return new Promise((resolve, reject) => {
      const socket = dgram.createSocket('udp4');
      socket.bind(() => {
        socket.setBroadcast(true);
        socket.send(packet, 0, packet.length, port, broadcastIp, (err) => {
          socket.close();
          if (err) {
            reject(err);
          } else {
            resolve({ success: true, mac, broadcastIp, port });
          }
        });
      });
    });
  }
}

let _wolInstance = null;
export const wakeOnLanManager = new Proxy({}, {
  get(target, prop) {
    if (!_wolInstance) {
      _wolInstance = new WakeOnLanManager();
    }
    const val = _wolInstance[prop];
    return typeof val === 'function' ? val.bind(_wolInstance) : val;
  }
});
