export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export function renderStatusPill(label: string, tone: StatusTone): HTMLSpanElement {
  const pill = document.createElement('span');
  pill.className = `status-pill ${tone}`;
  pill.textContent = label;
  return pill;
}
