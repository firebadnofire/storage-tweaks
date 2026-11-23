/* Run with NODE_PATH pointing at an isolated installation of minecraft-protocol@1.68.0. */
'use strict';
const mc = require('minecraft-protocol');
const port = Number(process.argv[2] || 25589);
if (![25589, 25590].includes(port)) throw new Error('This harness only connects to the isolated loopback test ports');
const client = mc.createClient({ host: '127.0.0.1', port, username: 'StorageProbe', version: '1.12.2', auth: 'offline' });
let loggedIn = false;
let failed = false;
let finished = false;
const timeout = setTimeout(() => fail(new Error('Login timed out')), 15000);
function fail(error) {
  if (finished) return;
  failed = true;
  finished = true;
  clearTimeout(timeout);
  console.error(error);
  client.end();
  process.exitCode = 1;
}
client.on('error', fail);
client.on('kick_disconnect', packet => fail(new Error('Kicked: ' + JSON.stringify(packet))));
client.on('custom_payload', packet => {
  if (packet.channel === 'fbstorage') fail(new Error('Server sent Storage Tweaks capability payload to an unmodded client'));
});
client.on('login', () => {
  loggedIn = true;
  console.log('Vanilla protocol client logged in to ' + port);
  setTimeout(() => {
    if (failed) return;
    clearTimeout(timeout);
    finished = true;
    console.log('PASS: unmodded client accepted; no Storage Tweaks handshake required or sent');
    client.end('Validation complete');
  }, 2500);
});
client.on('position', packet => {
  client.write('teleport_confirm', { teleportId: packet.teleportId });
  client.write('position_look', { x: packet.x, y: packet.y, z: packet.z, yaw: packet.yaw, pitch: packet.pitch, onGround: true });
});
client.on('end', reason => {
  if (!finished) fail(new Error('Disconnected before validation completed: ' + reason));
  if (!loggedIn) process.exitCode = 1;
});
