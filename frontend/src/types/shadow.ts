export interface ShadowConfig {
  id: number;
  serviceName: string;
  providerName: string;
  modelId: string;
  modelParams: Record<string, unknown> | null;
  samplingRate: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ShadowConfigRequest {
  serviceName: string;
  providerName: string;
  modelId: string;
  modelParams?: Record<string, unknown>;
  samplingRate: number;
}

export interface ShadowResult {
  id: number;
  sourceSpanId: string;
  shadowConfigId: number;
  modelId: string;
  requestBody: string | null;
  responseBody: string | null;
  latencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  costUsd: number | null;
  status: string;
  errorMessage: string | null;
  executedAt: string | null;
  createdAt: string;
  semanticSimilarity: number | null;
  correctnessScore: number | null;
}

export interface ShadowEvaluation {
  id: number;
  shadowResultId: number;
  semanticSimilarity: number | null;
  correctnessScore: number | null;
  evaluationDetail: string | null;
  evaluatedAt: string | null;
}

export interface ModelInfo {
  id: string;
  name: string;
  provider: string;
  description: string | null;
  contextLength: number | null;
}

export interface ProviderInfo {
  name: string;
  enabled: boolean;
  baseUrl: string | null;
}
