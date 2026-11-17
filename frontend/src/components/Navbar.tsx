import React from 'react';
import { Smartphone, LogOut, ShieldCheck } from 'lucide-react';
import { User } from '../types';

interface NavbarProps {
  user: User | null;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onLogout }) => {
  return (
    <header className="bg-slate-900 border-b border-slate-800 px-6 py-4 flex items-center justify-between">
      <div className="flex items-center space-x-3">
        <div className="bg-blue-600 p-2 rounded-lg text-white">
          <Smartphone className="w-5 h-5" />
        </div>
        <div>
          <h1 className="font-bold text-lg text-white leading-tight">Antigravity Remote</h1>
          <p className="text-xs text-slate-400">Secure Android Remote Control Platform</p>
        </div>
      </div>

      <div className="flex items-center space-x-4">
        {user && (
          <div className="flex items-center space-x-3 bg-slate-800 px-3 py-1.5 rounded-full border border-slate-700">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span className="text-sm font-medium text-slate-200">{user.email}</span>
          </div>
        )}

        <button
          onClick={onLogout}
          className="flex items-center space-x-1.5 text-slate-400 hover:text-red-400 text-sm font-medium transition-colors"
        >
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </div>
    </header>
  );
};
