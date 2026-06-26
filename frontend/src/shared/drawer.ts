import { createElement } from './components';

export function createDrawerSkeleton(title: string, notes: string[]): HTMLElement {
  return createElement(
    'aside',
    { className: 'drawer-skeleton' },
    createElement('header', {}, createElement('strong', {}, title)),
    createElement(
      'ul',
      {},
      ...notes.map((note) => createElement('li', {}, note)),
    ),
  );
}
