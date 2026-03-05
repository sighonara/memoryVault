# CLAUDE.md

## Project
- Zoneless Angular 20 with standalone components
- State management: NgRx Signal Store
- UI: Angular Material with custom theme
- API: REST with HttpClient + httpResource()
- Testing: Vitest (unit), Playwright (e2e)

## Conventions
- Feature-based folder structure: `feature-name/{component,service,store,model,routes}`
- All components use `OnPush` change detection
- API calls through services, never directly in components
- Barrel exports (`index.ts`) for every feature folder

## Commands
- `npm run test` – Run Vitest
- `npm run e2e` – Run Playwright
- `npm run lint` – ESLint check
- `npm run build` – Production build

## Avoid
- No NgModules, no `CommonModule` imports
- No constructor injection
- No `*ngIf` / `*ngFor` (use `@if` / `@for`)
- No `subscribe()` in components   