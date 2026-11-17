#!/usr/bin/env node
import { hostServer } from './server.js';
import { config } from './config.js';

console.log('==============================================');
console.log('   REGNANT — Personal Linux Host Control     ');
console.log('==============================================');
console.log(`Host ID:   ${config.hostId}`);
console.log(`Host Name: ${config.hostName}`);
console.log(`Data Dir:  ${config.dataDir}`);

hostServer.start(config.port);

const shutdown = () => {
  console.log('\n[Regnant Host] Gracefully shutting down...');
  hostServer.stop();
  process.exit(0);
};

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
