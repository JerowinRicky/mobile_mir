import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { RemoteWorkspacePage } from './pages/RemoteWorkspacePage';
import { User, Device, RemoteSession, TurnConfig } from './types';
import { api } from './services/api';

export const App: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [activeSession, setActiveSession] = useState<{
    session: RemoteSession;
    turnConfig: TurnConfig;
    device: Device;
  } | null>(null);

  useEffect(() => {
    const token = localStorage.getItem('access_token');
    if (token) {
      api
        .getMe()
        .then((userData) => setUser(userData))
        .catch(() => {
          localStorage.removeItem('access_token');
          localStorage.removeItem('refresh_token');
        })
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    setUser(null);
    setActiveSession(null);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center text-slate-500 text-sm">
        Initializing Remote Control Console...
      </div>
    );
  }

  if (!user) {
    return <LoginPage onLoginSuccess={(u) => setUser(u)} />;
  }

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col">
      <Navbar user={user} onLogout={handleLogout} />

      <main className="flex-1">
        {activeSession ? (
          <RemoteWorkspacePage
            session={activeSession.session}
            turnConfig={activeSession.turnConfig}
            device={activeSession.device}
            onExit={() => setActiveSession(null)}
          />
        ) : (
          <DashboardPage
            onStartSession={(session, turnConfig, device) => {
              setActiveSession({ session, turnConfig, device });
            }}
          />
        )}
      </main>
    </div>
  );
};
