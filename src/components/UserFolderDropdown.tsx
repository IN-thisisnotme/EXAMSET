import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { FolderItem } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import {
  buildUserFolderSuggestions,
  FolderPathSuggestion,
  searchUserFolders,
} from '../utils/folderPathHelper';

interface UserFolderDropdownInputProps {
  value: string;
  onChange: (next: string) => void;
  onSelectEntry: (entry: FolderPathSuggestion) => void;
  folders: FolderItem[];
  placeholder?: string;
  queryOverride?: string;
  buttonAriaLabel?: string;
  inputClassName?: string;
  inputStyle?: React.CSSProperties;
}

/**
 * Text input paired with an explicit on-demand folder dropdown trigger.
 * Never opens automatically while typing; clicking the chevron button opens
 * the user's folder hierarchy in Parent -> Child -> Sub-Child -> L4+ order.
 */
export const UserFolderDropdownInput: React.FC<UserFolderDropdownInputProps> = ({
  value,
  onChange,
  onSelectEntry,
  folders,
  placeholder,
  queryOverride,
  buttonAriaLabel = 'Choose from your folders',
  inputClassName = 'w-full px-3 py-1.5 pr-9 rounded-lg border',
  inputStyle,
}) => {
  const { colors } = useTheme();
  const [open, setOpen] = useState(false);
  const [showAllOverride, setShowAllOverride] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const effectiveQuery = queryOverride !== undefined ? queryOverride : value;

  const allFolders = useMemo(() => buildUserFolderSuggestions(folders), [folders]);
  const prefixMatches = useMemo(
    () => searchUserFolders(effectiveQuery, folders),
    [effectiveQuery, folders]
  );

  const displayedEntries =
    showAllOverride || !effectiveQuery.trim() ? allFolders : prefixMatches;

  useEffect(() => {
    if (!open) return;
    const handleOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setShowAllOverride(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <div className="relative flex items-center">
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className={inputClassName}
          style={inputStyle}
        />
        <button
          type="button"
          aria-label={buttonAriaLabel}
          title={buttonAriaLabel}
          onClick={() => {
            setShowAllOverride(false);
            setOpen((prev) => !prev);
          }}
          className="absolute right-1.5 p-1 rounded-md transition"
          style={{ color: open ? colors.teal : colors.muted }}
        >
          <ChevronDown className={`w-4 h-4 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      </div>

      {open && (
        <div
          className="absolute z-50 left-0 right-0 mt-1 rounded-xl border shadow-xl max-h-64 overflow-y-auto py-1"
          style={{
            backgroundColor: colors.surface,
            borderColor: colors.border,
          }}
        >
          {allFolders.length === 0 ? (
            <div className="px-3 py-2 text-[0.6875rem]" style={{ color: colors.muted }}>
              No folders created yet. Type any custom name above.
            </div>
          ) : displayedEntries.length === 0 ? (
            <div className="py-1">
              <div className="px-3 py-1.5 text-[0.6875rem]" style={{ color: colors.muted }}>
                No folders starting with &ldquo;{effectiveQuery.trim()}&rdquo;
              </div>
              <button
                type="button"
                onClick={() => setShowAllOverride(true)}
                className="w-full text-left px-3 py-1.5 text-[0.6875rem] font-bold hover:opacity-80"
                style={{ color: colors.teal }}
              >
                Show all folders ({allFolders.length})
              </button>
            </div>
          ) : (
            <>
              {!showAllOverride && effectiveQuery.trim() && prefixMatches.length < allFolders.length && (
                <button
                  type="button"
                  onClick={() => setShowAllOverride(true)}
                  className="w-full text-left px-3 py-1.5 text-[0.6875rem] font-bold border-b"
                  style={{ color: colors.teal, borderColor: colors.borderLight }}
                >
                  Show all folders ({allFolders.length})
                </button>
              )}
              {displayedEntries.map((entry) => {
                const indentPx = Math.min(entry.depth - 1, 4) * 12;
                return (
                  <button
                    key={entry.id}
                    type="button"
                    onClick={() => {
                      onSelectEntry(entry);
                      setOpen(false);
                      setShowAllOverride(false);
                    }}
                    className="w-full text-left px-3 py-2 flex items-start gap-2 transition hover:opacity-85"
                    style={{
                      paddingLeft: `${12 + indentPx}px`,
                      borderBottom: `1px solid ${colors.borderLight}`,
                    }}
                  >
                    <span
                      className="px-1.5 py-0.5 rounded text-[0.6rem] font-bold shrink-0 mt-0.5"
                      style={{
                        backgroundColor: colors.tealLight,
                        color: colors.tealDark,
                      }}
                    >
                      {entry.levelLabel}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div
                        className={`text-xs truncate ${entry.depth === 1 ? 'font-bold' : 'font-semibold'}`}
                        style={{ color: colors.inkBold }}
                      >
                        {entry.depth > 1 ? `└─ ${entry.leafName}` : entry.leafName}
                      </div>
                      {entry.depth > 1 && (
                        <div className="text-[0.625rem] truncate" style={{ color: colors.muted }}>
                          {entry.fullPath}
                        </div>
                      )}
                    </div>
                  </button>
                );
              })}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default UserFolderDropdownInput;
