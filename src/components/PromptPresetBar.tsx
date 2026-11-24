import React, { useMemo, useState } from 'react';
import {
  Bookmark,
  Check,
  CopyPlus,
  Edit3,
  Pin,
  Plus,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import { PromptPreset, PromptPresetMode } from '../types/phase4';

interface PromptPresetBarProps {
  mode: PromptPresetMode;
  presets: PromptPreset[];
  activePresetId: string | null;
  onSelectPreset: (preset: PromptPreset) => void;
  onSaveNewPreset: (name: string) => void;
  onUpdateActivePreset: () => void;
  onRenamePreset: (presetId: string, newName: string) => void;
  onDuplicatePreset: (presetId: string) => void;
  onTogglePinPreset: (presetId: string) => void;
  onDeletePreset: (presetId: string) => void;
}

export const PromptPresetBar: React.FC<PromptPresetBarProps> = ({
  mode,
  presets,
  activePresetId,
  onSelectPreset,
  onSaveNewPreset,
  onUpdateActivePreset,
  onRenamePreset,
  onDuplicatePreset,
  onTogglePinPreset,
  onDeletePreset,
}) => {
  const { colors } = useTheme();
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isNamingNew, setIsNamingNew] = useState<boolean>(false);
  const [newNameInput, setNewNameInput] = useState<string>('');
  const [isRenaming, setIsRenaming] = useState<boolean>(false);
  const [renameInput, setRenameInput] = useState<string>('');
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);

  const modePresets = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return presets
      .filter((p) => p.mode === mode)
      .filter((p) => !q || p.name.toLowerCase().includes(q))
      .sort((a, b) => {
        if (a.isPinned !== b.isPinned) return a.isPinned ? -1 : 1;
        return b.updatedAt - a.updatedAt;
      });
  }, [presets, mode, searchQuery]);

  const activePreset = useMemo(
    () => presets.find((p) => p.id === activePresetId && p.mode === mode) || null,
    [presets, activePresetId, mode]
  );

  const handleConfirmSaveNew = () => {
    const clean = newNameInput.trim();
    if (!clean) return;
    onSaveNewPreset(clean);
    setNewNameInput('');
    setIsNamingNew(false);
  };

  const handleConfirmRename = () => {
    const clean = renameInput.trim();
    if (!clean || !activePreset) return;
    onRenamePreset(activePreset.id, clean);
    setIsRenaming(false);
    setRenameInput('');
  };

  return (
    <div
      className="rounded-xl border p-3.5 space-y-2.5 text-xs"
      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Bookmark className="w-4 h-4" style={{ color: colors.teal }} />
          <span className="font-bold uppercase tracking-wider text-[0.6875rem]" style={{ color: colors.inkBold }}>
            Saved Prompt Presets ({modePresets.length})
          </span>
          {activePreset && (
            <span
              className="px-2 py-0.5 rounded-full text-[0.625rem] font-bold border"
              style={{
                backgroundColor: colors.tealLight,
                color: colors.tealDark,
                borderColor: colors.teal,
              }}
            >
              Active: {activePreset.name}
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          {activePreset && (
            <>
              <button
                type="button"
                onClick={onUpdateActivePreset}
                className="flex items-center gap-1 px-2.5 py-1 rounded-lg border font-bold transition"
                style={{
                  backgroundColor: colors.surface,
                  borderColor: colors.teal,
                  color: colors.tealDark,
                }}
                title="Overwrite active preset with current settings"
              >
                <Check className="w-3.5 h-3.5" />
                Update Active
              </button>
              <button
                type="button"
                onClick={() => {
                  setRenameInput(activePreset.name);
                  setIsRenaming(true);
                  setIsNamingNew(false);
                }}
                className="p-1.5 rounded-lg border transition"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                title="Rename active preset"
              >
                <Edit3 className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => onDuplicatePreset(activePreset.id)}
                className="p-1.5 rounded-lg border transition"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                title="Duplicate active preset"
              >
                <CopyPlus className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => onTogglePinPreset(activePreset.id)}
                className="p-1.5 rounded-lg border transition"
                style={
                  activePreset.isPinned
                    ? { backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.amber }
                    : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }
                }
                title={activePreset.isPinned ? 'Unpin preset' : 'Pin preset'}
              >
                <Pin className="w-3.5 h-3.5" />
              </button>
              {pendingDeleteId === activePreset.id ? (
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => {
                      onDeletePreset(activePreset.id);
                      setPendingDeleteId(null);
                    }}
                    className="px-2 py-1 rounded-lg font-bold text-white"
                    style={{ backgroundColor: colors.red }}
                  >
                    Confirm Delete
                  </button>
                  <button
                    type="button"
                    onClick={() => setPendingDeleteId(null)}
                    className="p-1 rounded-lg border"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setPendingDeleteId(activePreset.id)}
                  className="p-1.5 rounded-lg border transition"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.red }}
                  title="Delete active preset"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              )}
            </>
          )}

          <button
            type="button"
            onClick={() => {
              setIsNamingNew((v) => !v);
              setIsRenaming(false);
            }}
            className="flex items-center gap-1 px-2.5 py-1 rounded-lg font-bold transition"
            style={{
              backgroundColor: colors.primaryButtonBg,
              color: colors.primaryButtonText,
            }}
          >
            <Plus className="w-3.5 h-3.5" />
            Save New Preset
          </button>
        </div>
      </div>

      {/* Inline Save New or Rename Form */}
      {isNamingNew && (
        <div className="flex items-center gap-2 pt-1">
          <input
            type="text"
            value={newNameInput}
            onChange={(e) => setNewNameInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleConfirmSaveNew();
              if (e.key === 'Escape') setIsNamingNew(false);
            }}
            placeholder="Preset name (e.g., UPSC Polity Mains 10Q)..."
            className="flex-1 px-2.5 py-1.5 rounded-lg border text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            autoFocus
          />
          <button
            type="button"
            onClick={handleConfirmSaveNew}
            className="px-3 py-1.5 rounded-lg font-bold"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Save
          </button>
          <button
            type="button"
            onClick={() => setIsNamingNew(false)}
            className="px-2.5 py-1.5 rounded-lg border font-semibold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
          >
            Cancel
          </button>
        </div>
      )}

      {isRenaming && activePreset && (
        <div className="flex items-center gap-2 pt-1">
          <input
            type="text"
            value={renameInput}
            onChange={(e) => setRenameInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleConfirmRename();
              if (e.key === 'Escape') setIsRenaming(false);
            }}
            placeholder="New preset name..."
            className="flex-1 px-2.5 py-1.5 rounded-lg border text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            autoFocus
          />
          <button
            type="button"
            onClick={handleConfirmRename}
            className="px-3 py-1.5 rounded-lg font-bold"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Rename
          </button>
          <button
            type="button"
            onClick={() => setIsRenaming(false)}
            className="px-2.5 py-1.5 rounded-lg border font-semibold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
          >
            Cancel
          </button>
        </div>
      )}

      {/* Search + Preset Chips */}
      {(presets.filter((p) => p.mode === mode).length > 3 || searchQuery.trim().length > 0) && (
        <div className="relative">
          <Search
            className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2"
            style={{ color: colors.muted }}
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Filter presets for this mode..."
            className="w-full pl-8 pr-3 py-1.5 rounded-lg border text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          />
        </div>
      )}

      {modePresets.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 pt-0.5">
          {modePresets.map((preset) => {
            const isSel = preset.id === activePresetId;
            return (
              <button
                key={preset.id}
                type="button"
                onClick={() => onSelectPreset(preset)}
                className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg border font-semibold transition text-[0.6875rem]"
                style={
                  isSel
                    ? {
                        backgroundColor: colors.primaryButtonBg,
                        color: colors.primaryButtonText,
                        borderColor: colors.primaryButtonBg,
                      }
                    : {
                        backgroundColor: colors.surface,
                        color: colors.ink,
                        borderColor: colors.border,
                      }
                }
              >
                {preset.isPinned && <Pin className="w-3 h-3 text-amber-400" />}
                <span>{preset.name}</span>
              </button>
            );
          })}
        </div>
      ) : (
        <p className="text-[0.6875rem]" style={{ color: colors.muted }}>
          No saved presets in this mode yet. Configure parameters below and click <strong>Save New Preset</strong>.
        </p>
      )}
    </div>
  );
};

export default PromptPresetBar;
