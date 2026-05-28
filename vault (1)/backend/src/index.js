const express = require('express');
const http = require('http');
const cors = require('cors');
const dotenv = require('dotenv');
const { Server } = require('socket.io');
const path = require('fs');

dotenv.config();

const apiRouter = require('./routes/api');
const setupSupportSocket = require('./socket/support');

const app = express();
const server = http.createServer(app);

// Configure Socket.io with strong CORS support validation matches
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Device_ID', 'X-Device-ID']
  }
});

// Middleware standard stack pipeline setup
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Express Application request/traffic logger
app.use((req, res, next) => {
  console.log(`[HTTP INCOMING] [${new Date().toISOString()}] ${req.method} ${req.url} - IP: ${req.ip}`);
  next();
});

// Mount modular Backend route paths
app.use('/api', apiRouter);

// Set root metadata feedback response endpoint
app.get('/', (req, res) => {
  res.status(200).json({
    name: 'Secure Cloud Vault Administration API Backend',
    version: '1.0.0',
    local_time: new Date(),
    status: 'ONLINE',
    socket_endpoints: ['/support']
  });
});

// Initialize Websockets setup bindings
setupSupportSocket(io);

// Global operational fallback Error Handling middleware
app.use((err, req, res, next) => {
  console.error('[CRITICAL UNHANDLED ERROR IN PIPELINE]', err);
  
  const statusCode = err.status || err.statusCode || 500;
  res.status(statusCode).json({
    error: err.message || 'An unhandled server exception occurred during operations',
    dev_info: process.env.NODE_ENV !== 'production' ? err.stack : undefined
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`=========================================`);
  console.log(` Secure Cloud Vault Backend Engine      `);
  console.log(` Service successfully listening on:      `);
  console.log(` http://localhost:${PORT}               `);
  console.log(`=========================================`);
});
