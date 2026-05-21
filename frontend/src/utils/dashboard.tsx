import type { ReactNode } from 'react';
import {
  ShoppingCart, Home, Car, Gamepad2, Heart, Zap,
  GraduationCap, PiggyBank, Briefcase, TrendingUp, MoreHorizontal,
} from 'lucide-react';

export const PALETTE = [
  '#6366f1','#8b5cf6','#ec4899','#f59e0b','#10b981',
  '#3b82f6','#ef4444','#14b8a6','#f97316','#a855f7',
];

export const CATEGORY_ICONS: Record<string, ReactNode> = {
  Food: <ShoppingCart size={14} />,
  Housing: <Home size={14} />,
  Transport: <Car size={14} />,
  Entertainment: <Gamepad2 size={14} />,
  Healthcare: <Heart size={14} />,
  Shopping: <ShoppingCart size={14} />,
  Utilities: <Zap size={14} />,
  Education: <GraduationCap size={14} />,
  Savings: <PiggyBank size={14} />,
  Salary: <Briefcase size={14} />,
  Freelance: <TrendingUp size={14} />,
  Other: <MoreHorizontal size={14} />,
};

export const MONTH_NAMES = [
  'January','February','March','April','May','June',
  'July','August','September','October','November','December',
];

export const SHORT_MONTHS = [
  'Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec',
];

export function formatCurrency(n: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', minimumFractionDigits: 2,
  }).format(n);
}

export function formatDate(d: string): string {
  return new Date(d + 'T00:00:00').toLocaleDateString('en-US', {
    month: 'short', day: 'numeric',
  });
}

export function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function getTimeOfDay(): string {
  const h = new Date().getHours();
  if (h < 12) return 'morning';
  if (h < 17) return 'afternoon';
  return 'evening';
}
