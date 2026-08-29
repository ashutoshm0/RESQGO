import { useState, useEffect } from 'react';
import { collection, query, onSnapshot, orderBy } from 'firebase/firestore';
import { db } from './firebase';
import './index.css';

function App() {
  const [events, setEvents] = useState([]);
  const [isFirebaseConfigured, setIsFirebaseConfigured] = useState(true);

  useEffect(() => {
    try {
      const q = query(collection(db, 'crash_events'), orderBy('timestamp', 'desc'));
      
      const unsubscribe = onSnapshot(q, (querySnapshot) => {
        const eventsData = [];
        querySnapshot.forEach((doc) => {
          eventsData.push({ id: doc.id, ...doc.data() });
        });
        setEvents(eventsData);
      }, (error) => {
        console.error("Firestore error:", error);
        if (error.code === 'permission-denied' || error.message.includes('API key')) {
          setIsFirebaseConfigured(false);
        }
      });

      return () => unsubscribe();
    } catch (e) {
      console.error("Initialization error:", e);
      setIsFirebaseConfigured(false);
    }
  }, []);

  const formatDate = (timestamp) => {
    if (!timestamp) return 'Unknown time';
    const date = new Date(timestamp);
    return new Intl.DateTimeFormat('en-US', {
      hour: '2-digit', minute: '2-digit', second: '2-digit',
      month: 'short', day: 'numeric'
    }).format(date);
  };

  return (
    <div className="dashboard-container">
      <header>
        <h1>RESQGO <span style={{color: 'var(--text-secondary)', fontWeight: 300}}>Dashboard</span></h1>
        <div className="status-badge">
          <div className="dot"></div>
          Monitoring Live Systems
        </div>
      </header>

      {!isFirebaseConfigured ? (
        <div className="empty-state">
          <h3>⚠️ Firebase Not Configured</h3>
          <p>Please open <code>src/firebase.js</code> and add your Firebase config keys.</p>
        </div>
      ) : (
        <div className="alert-feed">
          {events.length === 0 ? (
            <div className="empty-state fade-in">
              <h3>No Active Alerts</h3>
              <p>All riders are currently safe.</p>
            </div>
          ) : (
            events.map((event) => (
              <div key={event.id} className="alert-card critical fade-in">
                <div className="alert-header">
                  <div className="alert-type">
                    <span className="icon">🚨</span>
                    CRASH DETECTED
                  </div>
                  <div className="alert-time">{formatDate(event.timestamp)}</div>
                </div>
                
                <div className="alert-details">
                  <p><strong>Status:</strong> <span style={{color: 'var(--accent-red)'}}>{event.status || 'Requires Immediate Attention'}</span></p>
                  <p><strong>Coordinates:</strong> {event.latitude?.toFixed(4)}, {event.longitude?.toFixed(4)}</p>
                  <p><strong>Source:</strong> Automated Sensor Trigger ({event.eventType})</p>
                </div>

                <a 
                  href={`https://maps.google.com/?q=${event.latitude},${event.longitude}`} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className="map-btn"
                >
                  View on Google Maps 📍
                </a>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

export default App;
