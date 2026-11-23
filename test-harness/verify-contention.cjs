/* Saved server data check; requires prismarine-nbt from the isolated protocol tools. */
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const nbt = require('prismarine-nbt');
async function main() {
  const directory = process.argv[2];
  if (!directory) throw new Error('Pass the isolated contention world playerdata directory');
  const totals = [];
  for (const name of ['StorageTestA', 'StorageTestB']) {
    const bytes = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest();
    bytes[6] = (bytes[6] & 15) | 48;
    bytes[8] = (bytes[8] & 63) | 128;
    const hex = bytes.toString('hex');
    const file = [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-') + '.dat';
    const { parsed } = await nbt.parse(fs.readFileSync(path.join(directory, file)));
    const player = nbt.simplify(parsed);
    const count = player.Inventory.filter(stack => stack.id === 'minecraft:diamond')
      .reduce((total, stack) => total + stack.Count, 0);
    totals.push(count);
  }
  totals.sort((a, b) => a - b);
  if (JSON.stringify(totals) !== '[6,7]') throw new Error('Expected StorageTestA/B holding 6 and 7 diamonds; got ' + totals);
  console.log('CONTENTION_PLAYER_COUNTS_PASSED:6,7');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
