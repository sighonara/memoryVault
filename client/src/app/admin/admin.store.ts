import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { inject } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GetJobsDocument,
  GetLogsDocument,
  GetAdminStatsDocument,
  GetCostsDocument,
  RefreshCostsDocument,
  SyncJob,
  LogEntry,
  SystemStats,
  CostSummary,
} from '../shared/graphql/generated';
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
      setCostMonths: (months: number) => { patchState(store, { costMonths: months }); loadCosts(); },
      setJobTypeFilter: (type: string | null) => { patchState(store, { jobTypeFilter: type }); loadJobs(); },
      setLogLevelFilter: (level: string | null) => { patchState(store, { logLevelFilter: level }); loadLogs(); },
      setLogServiceFilter: (service: string) => { patchState(store, { logServiceFilter: service || null }); loadLogs(); },
      setFollowActive: (active: boolean) => { patchState(store, { followActive: active }); },
    };
  })
);
