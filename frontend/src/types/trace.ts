export interface TraceListItem {
  traceId: string;
  serviceName: string;
  fpId: string | null;
  fpModuleId: string | null;
  startedAt: string;
  endedAt: string | null;
  spanCount: number;
  status: string;
  createdAt: string;
}

export interface SpanDetail {
  spanId: string;
  parentSpanId: string | null;
  operationName: string;
  spanKind: string;
  startedAt: string;
  durationMicros: number;
  statusCode: string | null;
  httpUrl: string | null;
  httpMethod: string | null;
  httpStatus: string | null;
  clientName: string | null;
  requestBody: string | null;
  responseBody: string | null;
  outcome: string | null;
}

export interface TraceDetail {
  traceId: string;
  serviceName: string;
  fpId: string | null;
  fpModuleId: string | null;
  startedAt: string;
  endedAt: string | null;
  spanCount: number;
  status: string;
  createdAt: string;
  spans: SpanDetail[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
