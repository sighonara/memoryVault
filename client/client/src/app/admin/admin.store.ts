import { inject } from '@angular/core';
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { Apollo } from 'apollo-angular';
import { GetAdminDataDocument, SystemStats, SyncJob, LogEntry } from '../shared/graphql/generated';
import { firstValueFrom } from 'rxjs';

interface AdminState {
  stats: SystemStats | null;
  jobs: SyncJob[];
  logs: LogEntry[];
  loading: boolean;
  error: string | null;
}

const initialState: AdminState = {
  stats: null,
  jobs: [],
  logs: [],
  loading: false,
  error: null,
};

export const AdminStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store, apollo = inject(Apollo)) => ({
    async loadAdminData(limit: number = 20) {
      patchState(store, { loading: true, error: null });
      try {
        const result = await firstValueFrom(
          apollo.query({
            query: GetAdminDataDocument,
            variables: { limit },
            fetchPolicy: 'network-only'
          })
        );

        if (result.data) {
          patchState(store, {
            stats: result.data.stats,
            jobs: result.data.jobs,
            logs: result.data.logs,
            loading: false
          });
        }
      } catch (e: any) {
        patchState(store, {
          error: e.message || 'Failed to load admin data',
          loading: false
        });
      }
    }
  }))
);
