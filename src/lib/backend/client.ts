// Single owner of frontend -> backend routing. When the backend (EC2) is stopped
// or unhealthy, callers fall back to the direct MongoDB + LeetCode stats flow.

const PROBE_TIMEOUT_MS = 1_500;
const REQUEST_TIMEOUT_MS = 8_000;
const HEALTH_TTL_MS = 30_000;

export const BACKEND_URL = process.env.BACKEND_URL ?? 'http://wukode.duckdns.org:8080';
export const BACKEND_ENABLED =
	(process.env.ENABLE_BACKEND_API ?? 'true') === 'true' && Boolean(BACKEND_URL);

export class BackendUnavailableError extends Error {
	constructor(reason: string) {
		super(`Backend unavailable: ${reason}`);
		this.name = 'BackendUnavailableError';
	}
}

// Per warm serverless instance; each instance re-probes at most once per TTL.
let health: { up: boolean; checkedAt: number } | undefined;
let inflightProbe: Promise<boolean> | undefined;

const markHealth = (up: boolean): void => {
	if (health?.up !== up) {
		console.info(`[backend] health: ${up ? 'up' : 'down'} (${BACKEND_URL})`);
	}
	health = { up, checkedAt: Date.now() };
};

const probe = async (): Promise<boolean> => {
	try {
		const response = await fetch(`${BACKEND_URL}/health`, {
			cache: 'no-store',
			signal: AbortSignal.timeout(PROBE_TIMEOUT_MS),
		});
		await response.body?.cancel();
		return response.ok;
	} catch {
		return false;
	}
};

export const isBackendAvailable = async (): Promise<boolean> => {
	if (!BACKEND_ENABLED) return false;
	if (health && Date.now() - health.checkedAt < HEALTH_TTL_MS) return health.up;

	inflightProbe ??= probe()
		.then((up) => {
			markHealth(up);
			return up;
		})
		.finally(() => {
			inflightProbe = undefined;
		});
	return inflightProbe;
};

// Throws BackendUnavailableError on network errors, timeouts and 5xx; any other
// status (including 4xx business errors) is returned to the caller.
export const backendFetch = async (path: string, init: RequestInit = {}): Promise<Response> => {
	let response: Response;
	try {
		response = await fetch(`${BACKEND_URL}${path}`, {
			cache: 'no-store',
			...init,
			signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
		});
	} catch (error) {
		markHealth(false);
		throw new BackendUnavailableError(error instanceof Error ? error.message : String(error));
	}

	if (response.status >= 500) {
		markHealth(false);
		throw new BackendUnavailableError(`${path} returned ${response.status}`);
	}
	return response;
};

export const withBackendFallback = async <T>(
	label: string,
	viaBackend: () => Promise<T>,
	direct: () => Promise<T>
): Promise<T> => {
	if (!(await isBackendAvailable())) return direct();

	try {
		return await viaBackend();
	} catch (error) {
		if (!(error instanceof BackendUnavailableError)) throw error;
		console.warn(`[backend] ${label}: ${error.message}; falling back to direct`);
		return direct();
	}
};
