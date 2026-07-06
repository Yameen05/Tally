import {
  AlertTriangle, Landmark, Building2, Clock, Trash2,
  CreditCard, Wallet,
} from 'lucide-react';
import { ConnectedItem } from '../../types';
import { formatCurrency, formatRelative } from '../../utils/dashboard';
import { d } from '../../pages/dashboard.styles';
import ConnectBank from '../ConnectBank';

interface Props {
  items: ConnectedItem[];
  plaidConfigured: boolean | null;
  onConnected: () => void;
  onDisconnect: (itemId: number) => void;
}

export default function AccountsTab({ items, plaidConfigured, onConnected, onDisconnect }: Props) {
  if (plaidConfigured === false) {
    return (
      <div className="fade-in">
        <div style={d.warnBanner}>
          <AlertTriangle size={16} color="#f59e0b" />
          <span>Plaid is not configured on the server. Set <code style={d.codeChip}>PLAID_CLIENT_ID</code> and <code style={d.codeChip}>PLAID_SECRET</code> env vars.</span>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="fade-in">
        <div style={d.connectEmpty}>
          <div style={d.connectEmptyIcon}><Landmark size={32} color="#818cf8" /></div>
          <h3 style={d.connectEmptyTitle}>No banks connected yet</h3>
          <p style={d.connectEmptySub}>
            Connect your bank to automatically import transactions and balances —
            no manual entry, no spreadsheets, just clarity.
          </p>
          <div style={{ marginTop: 24 }}>
            <ConnectBank onConnected={onConnected} />
          </div>
          <div style={d.trustRow}>
            <span style={d.trustItem}>🔒 256-bit encryption</span>
            <span style={d.trustItem}>🏦 Powered by Plaid</span>
            <span style={d.trustItem}>✓ Read-only access</span>
          </div>
        </div>
      </div>
    );
  }

  const accountCount = items.reduce((sum, i) => sum + i.accounts.length, 0);

  return (
    <div className="fade-in">
      <div style={d.accountsHeader}>
        <span style={{ color: 'var(--text-3)', fontSize: 13 }}>
          {items.length} bank{items.length === 1 ? '' : 's'} connected ·{' '}
          {accountCount} account{accountCount === 1 ? '' : 's'}
        </span>
        <ConnectBank variant="secondary" label="Add another bank" onConnected={onConnected} />
      </div>
      <div style={d.itemsGrid}>
        {items.map(item => (
          <div key={item.id} style={d.itemCard}>
            <div style={d.itemHeader}>
              <div style={d.itemHeaderLeft}>
                <div style={d.itemIcon}><Building2 size={18} color="#a5b4fc" /></div>
                <div>
                  <div style={d.itemName}>{item.institutionName ?? 'Bank'}</div>
                  <div style={d.itemMeta}>
                    <Clock size={11} />
                    {item.lastSyncedAt ? `Synced ${formatRelative(item.lastSyncedAt)}` : 'Not yet synced'}
                  </div>
                </div>
              </div>
              <button style={d.disconnectBtn} onClick={() => onDisconnect(item.id)}>
                <Trash2 size={14} />
              </button>
            </div>
            {item.syncError && (
              <div style={d.itemError}>
                <AlertTriangle size={13} /> {item.syncError}
              </div>
            )}
            <div style={d.accountList}>
              {item.accounts.length === 0 ? (
                <p style={{ color: 'var(--text-3)', fontSize: 13 }}>No accounts yet — try syncing.</p>
              ) : (
                item.accounts.map(acc => (
                  <div key={acc.id} style={d.accountRow}>
                    <div style={d.accountLeft}>
                      <div style={d.accountIcon}>
                        {acc.type === 'credit' ? <CreditCard size={14} /> : <Wallet size={14} />}
                      </div>
                      <div>
                        <div style={d.accountName}>{acc.name}</div>
                        <div style={d.accountSubtype}>
                          {acc.subtype ?? acc.type ?? 'Account'}
                          {acc.mask && ` · ••${acc.mask}`}
                        </div>
                      </div>
                    </div>
                    <div style={d.accountBalance}>
                      {acc.currentBalance != null ? formatCurrency(acc.currentBalance) : '—'}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
