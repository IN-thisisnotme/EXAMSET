import { FolderItem } from '../types/exam';

export interface FolderPathSuggestion {
  id: string;
  levelLabel: string;
  fullPath: string;
  leafName: string;
  accentColor: string;
  depth: number;
}

const LITERAL_SLASH_PLACEHOLDER = '\u0000LITERAL_SLASH\u0000';

export function levelLabelForDepth(depth: number): string {
  if (depth === 1) return 'Parent';
  if (depth === 2) return 'Child';
  if (depth === 3) return 'Sub-Child';
  return `L${depth}`;
}

/**
 * Formats a stored subject/folder string for UI display using `>` separators instead of `/`,
 * while preserving escaped `//` as a literal `/` inside folder names.
 */
export function formatDisplayPath(raw?: string | null): string {
  if (!raw || !raw.trim()) return '';
  const withEscaped = raw.replace(/\/\//g, LITERAL_SLASH_PLACEHOLDER);
  return withEscaped
    .replace(/\//g, ' > ')
    .split('>')
    .map((seg) => seg.replace(new RegExp(LITERAL_SLASH_PLACEHOLDER, 'g'), '/').trim())
    .filter((seg) => seg.length > 0)
    .join(' > ');
}

/**
 * Resolves the full `Parent > Child > Sub-Child` breadcrumb path for a user folder ID.
 * Skips the synthetic `'default'` root node used by Web storage.
 */
export function getFolderHierarchyPath(folderId: string | undefined | null, folders: FolderItem[]): string {
  if (!folderId || folderId === 'all' || folderId === 'default' || folders.length === 0) return '';
  const byId = new Map(folders.map((f) => [f.id, f]));
  const chain: string[] = [];
  const visited = new Set<string>();
  let curr = byId.get(folderId);
  while (curr && !visited.has(curr.id)) {
    visited.add(curr.id);
    if (curr.id === 'default') break;
    const clean = curr.name.trim();
    if (clean) chain.unshift(clean);
    curr = curr.parentId && curr.parentId !== 'default' ? byId.get(curr.parentId) : undefined;
  }
  return chain.join(' > ');
}

/**
 * Builds all user folder paths preserving unambiguous
 * 1. Parent -> 2. Child -> 3. Sub-Child -> L4... tree order.
 */
export function buildUserFolderSuggestions(folders: FolderItem[]): FolderPathSuggestion[] {
  const userFolders = folders.filter((f) => f.id !== 'default');
  if (userFolders.length === 0) return [];

  const ordered: FolderPathSuggestion[] = [];
  const visited = new Set<string>();
  const seenPaths = new Set<string>();

  const traverse = (parentId: string | null, prefixSegments: string[]) => {
    const children = userFolders.filter((f) => {
      if (parentId === null) {
        return !f.parentId || f.parentId === 'default';
      }
      return f.parentId === parentId;
    });

    for (const child of children) {
      if (visited.has(child.id)) continue;
      visited.add(child.id);
      const cleanName = child.name.trim();
      if (!cleanName) continue;
      const nextSegments = [...prefixSegments, cleanName];
      const fullPath = nextSegments.join(' > ');
      const depth = nextSegments.length;
      if (!seenPaths.has(fullPath)) {
        seenPaths.add(fullPath);
        ordered.push({
          id: child.id,
          levelLabel: levelLabelForDepth(depth),
          fullPath,
          leafName: cleanName,
          accentColor: child.color || '#0284C7',
          depth,
        });
      }
      if (depth < 25) {
        traverse(child.id, nextSegments);
      }
    }
  };

  traverse(null, []);

  // Fallback for any orphaned folder whose parentId is missing
  for (const folder of userFolders) {
    if (!visited.has(folder.id)) {
      visited.add(folder.id);
      const fullPath = getFolderHierarchyPath(folder.id, folders) || folder.name.trim();
      if (fullPath && !seenPaths.has(fullPath)) {
        seenPaths.add(fullPath);
        const segments = fullPath
          .split('>')
          .map((s) => s.trim())
          .filter(Boolean);
        const depth = Math.max(1, segments.length);
        ordered.push({
          id: folder.id,
          levelLabel: levelLabelForDepth(depth),
          fullPath,
          leafName: segments[segments.length - 1] || fullPath,
          accentColor: folder.color || '#0284C7',
          depth,
        });
      }
    }
  }

  return ordered;
}

/**
 * Strict starting-character prefix search across the user's folder hierarchy:
 * 1. Parent folder name first
 * 2. Followed by Child folder name
 * 3. Then Sub-Child / L4+
 * Never falls back to substring includes(); typing "y" will NOT match "xyz".
 */
export function searchUserFolders(
  query: string,
  folders: FolderItem[],
  maxResults: number = Number.POSITIVE_INFINITY
): FolderPathSuggestion[] {
  const pool = buildUserFolderSuggestions(folders);
  if (pool.length === 0) return [];

  const trimmed = query.trim();
  if (!trimmed) {
    return Number.isFinite(maxResults) ? pool.slice(0, maxResults) : pool;
  }

  const escaped = trimmed.replace(/\/\//g, LITERAL_SLASH_PLACEHOLDER);
  const hasTrailingSeparator = escaped.endsWith('>') || escaped.endsWith('/');
  const rawSegments = escaped
    .replace(/\//g, '>')
    .split('>')
    .map((s) => s.replace(new RegExp(LITERAL_SLASH_PLACEHOLDER, 'g'), '/').trim())
    .filter((s) => s.length > 0);

  if (rawSegments.length === 0) {
    return Number.isFinite(maxResults) ? pool.slice(0, maxResults) : pool;
  }

  const querySegments = hasTrailingSeparator ? [...rawSegments, ''] : rawSegments;

  const matches = pool.filter((entry) => {
    const entrySegments = entry.fullPath.split('>').map((s) => s.trim());
    if (entrySegments.length < querySegments.length) return false;

    for (let i = 0; i < querySegments.length - 1; i++) {
      if (entrySegments[i].toLowerCase() !== querySegments[i].toLowerCase()) {
        return false;
      }
    }

    const activePrefix = querySegments[querySegments.length - 1];
    if (!activePrefix) return true;
    return entrySegments[querySegments.length - 1].toLowerCase().startsWith(activePrefix.toLowerCase());
  });

  return Number.isFinite(maxResults) ? matches.slice(0, maxResults) : matches;
}
