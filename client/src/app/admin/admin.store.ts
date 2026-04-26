import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GetJobsDocument,
  GetLogsDocument,
  GetAdminStatsDocument,
  GetCostsDocument,
  RefreshCostsDocument,
  GetBackupProvidersDocument,
  GetBackupStatsDocument,
  AddBackupProviderDocument,
  DeleteBackupProviderDocument,
  TriggerBackfillDocument,
  SyncJob,
  LogEntry,
  SystemStats,
  CostSummary,
} from '../shared/graphql/generated';
import { BackupProviderView, BackupStatsView } from './backup-panel';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { WebSocketService } from '../core/services/websocket.service';
import { debounceTime } from 'rxjs/operators';

export interface AdminState {
  stats: SystemStats | null;
  jobs: SyncJob[];
  logs: LogEntry[];
  loading: boolean;
  jobTypeFilter: string | null;
  logLevelFilter: string | null;
  logServiceFilter: string | null;
  logLimit: number;
  jobLimit: number;
  followActive: boolean;
  costSummary: CostSummary | null;
  costMonths: number;
  refreshingCosts: boolean;
  backupProviders: BackupProviderView[];
  backupStats: BackupStatsView | null;
}

const initialState: AdminState = {
  stats: null,
  jobs: [],
  logs: [],
  loading: false,
  jobTypeFilter: null,
  logLevelFilter: null,
  logServiceFilter: null,
  logLimit: 100,
  jobLimit: 50,
  followActive: false,
  costSummary: null,
  costMonths: 6,
  refreshingCosts: false,
  backupProviders: [],
  backupStats: null,
};

export const AdminStore = signalStore(
  withState(initialState),
  withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
    const loadStats = rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() =>
          apollo.query({
            query: GetAdminStatsDocument,
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { stats: result.data.stats, loading: false });
        })
      )
    );

    const loadJobs = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetJobsDocument,
            variables: {
              type: store.jobTypeFilter() || null,
              limit: store.jobLimit(),
            },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { jobs: result.data.jobs });
        })
      )
    );

    const loadLogs = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetLogsDocument,
            variables: {
              level: store.logLevelFilter() || null,
              service: store.logServiceFilter() || null,
              limit: store.logLimit(),
            },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { logs: result.data.logs });
        })
      )
    );

    const loadCosts = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetCostsDocument,
            variables: { months: store.costMonths() },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { costSummary: result.data.costs });
        })
      )
    );

    const refreshCosts = rxMethod<void>(
      pipe(
        tap(() => patchState(store, { refreshingCosts: true })),
        switchMap(() =>
          apollo.mutate({
            mutation: RefreshCostsDocument,
          })
        ),
        tap(() => {
          patchState(store, { refreshingCosts: false });
          loadCosts();
        })
      )
    );

    const loadBackupProviders = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetBackupProvidersDocument,
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { backupProviders: result.data.backupProviders });
        })
      )
    );

    const loadBackupStats = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetBackupStatsDocument,
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { backupStats: result.data.backupStats });
        })
      )
    );

    const deleteBackupProvider = (id: string) => {
      apollo.mutate({ mutation: DeleteBackupProviderDocument, variables: { id } }).subscribe(() => {
        patchState(store, {
          backupProviders: store.backupProviders().filter((p: any) => p.id !== id)
        });
      });
    };

    const triggerBackfillFn = () => {
      apollo.mutate({ mutation: TriggerBackfillDocument }).subscribe(() => {
        loadBackupStats();
      });
    };

    const addBackupProvider = (input: { type: string; name: string; accessKey: string; secretKey: string; isPrimary: boolean }) => {
      apollo.mutate({
        mutation: AddBackupProviderDocument,
        variables: { input }
      }).subscribe(() => {
        loadBackupProviders();
      });
    };

    // WebSocket subscription for job updates
    ws.on('jobs').pipe(debounceTime(500)).subscribe(() => {
      loadJobs();
      loadStats();
    });

    return {
      loadStats,
      loadJobs,
      loadLogs,
      loadCosts,
      refreshCosts,
      loadBackupProviders,
      loadBackupStats,
      deleteBackupProvider,
      triggerBackfill: triggerBackfillFn,
      addBackupProvider,
      setCostMonths: (months: number) => { patchState(store, { costMonths: months }); loadCosts(); },
      setJobTypeFilter: (type: string | null) => { patchState(store, { jobTypeFilter: type }); loadJobs(); },
      setLogLevelFilter: (level: string | null) => { patchState(store, { logLevelFilter: level }); loadLogs(); },
      setLogServiceFilter: (service: string) => { patchState(store, { logServiceFilter: service || null }); loadLogs(); },
      setFollowActive: (active: boolean) => { patchState(store, { followActive: active }); },
    };
  })
);
