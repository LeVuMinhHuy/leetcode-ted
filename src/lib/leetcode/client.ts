import { LC_STATS_API } from '@/constants/data-source';
import type {
	HeatmapDay,
	LeetCodeContestHistory,
	LeetCodeEnvelope,
	LeetCodeHeatmap,
	LeetCodeProfile,
	LeetCodeStats,
	MemberSnapshot,
} from '@/lib/leetcode/types';
import { resolveElo } from '@/lib/leetcode/elo';

const REVALIDATE_SECONDS = 300;

const fetchEnvelope = async <T>(path: string): Promise<LeetCodeEnvelope<T>> => {
	const response = await fetch(`${LC_STATS_API}${path}`, {
		headers: { Accept: 'application/json' },
		next: { revalidate: REVALIDATE_SECONDS },
	});

	if (!response.ok) {
		throw new Error(`LeetCode stats request failed: ${path}`);
	}

	return (await response.json()) as LeetCodeEnvelope<T>;
};

const userPath = (username: string, suffix: string): string =>
	`/${encodeURIComponent(username)}${suffix}`;

const unwrap = <T>(envelope: LeetCodeEnvelope<T>, label: string): T => {
	if (envelope.status !== 'success' || envelope.data == null) {
		throw new Error(envelope.message || `Failed to load ${label}`);
	}

	if (label === 'profile') {
		return {
			...envelope.data,
			recentSubmissions: envelope.recentSubmissions,
		};
	}

	return envelope.data;
};

export const fetchLeetCodeProfile = async (username: string): Promise<LeetCodeProfile> =>
	unwrap(await fetchEnvelope<LeetCodeProfile>(userPath(username, '/profile')), 'profile');

export const fetchLeetCodeStats = async (username: string): Promise<LeetCodeStats> =>
	unwrap(await fetchEnvelope<LeetCodeStats>(userPath(username, '/stats')), 'stats');

const EMPTY_CONTESTS: LeetCodeContestHistory = {
	count: 0,
	rating: null,
	maxRating: null,
	rank: null,
	globalRanking: null,
	topPercentage: null,
};

export const fetchLeetCodeContests = async (username: string) => {
	const envelope = await fetchEnvelope<LeetCodeContestHistory>(userPath(username, '/contests'));
	return envelope.status === 'success' && envelope.data ? envelope.data : EMPTY_CONTESTS;
};

export const fetchLeetCodeHeatmap = async (
	username: string,
	year?: number
): Promise<LeetCodeHeatmap> => {
	const query = year ? `?year=${year}` : '';
	return unwrap(
		await fetchEnvelope<LeetCodeHeatmap>(userPath(username, `/heatmap${query}`)),
		'heatmap'
	);
};

export const leetCodeUserExists = async (username: string): Promise<boolean> => {
	try {
		const envelope = await fetchEnvelope<LeetCodeProfile>(userPath(username, '/profile'));
		return envelope.status === 'success' && envelope.data != null;
	} catch {
		return false;
	}
};

const pickDefaultYear = (heatmap: LeetCodeHeatmap): number => {
	const currentYear = new Date().getFullYear();
	const activeYears = heatmap.yearlyContributions
		.filter((entry) => entry.totalSubmissions > 0)
		.map((entry) => entry.year)
		.sort((a, b) => b - a);

	if (heatmap.lastActiveDate) {
		const lastActiveYear = Number(heatmap.lastActiveDate.slice(0, 4));
		if (Number.isFinite(lastActiveYear)) {
			return lastActiveYear;
		}
	}

	return activeYears[0] ?? currentYear;
};

const contributionsForYear = (days: HeatmapDay[], year: number) =>
	days
		.filter((day) => day.date.startsWith(String(year)) && day.count > 0)
		.map((day) => ({ date: day.date, count: day.count }));

export const fetchMemberSnapshot = async (
	username: string,
	displayNameHint?: string
): Promise<MemberSnapshot> => {
	const [profile, stats, contests, heatmap] = await Promise.all([
		fetchLeetCodeProfile(username),
		fetchLeetCodeStats(username),
		fetchLeetCodeContests(username),
		fetchLeetCodeHeatmap(username),
	]);

	const year = pickDefaultYear(heatmap);
	const { elo, eloSource } = resolveElo({
		contestRating: contests.rating,
		easy: stats.byDifficulty.easy,
		medium: stats.byDifficulty.medium,
		hard: stats.byDifficulty.hard,
		currentStreak: heatmap.currentStreak,
	});

	return {
		username,
		displayName: displayNameHint || profile.displayName || username,
		avatar: profile.avatar,
		country: profile.country,
		totalSolved: stats.totalSolved,
		totalQuestions: stats.totalQuestions,
		easy: stats.byDifficulty.easy,
		medium: stats.byDifficulty.medium,
		hard: stats.byDifficulty.hard,
		acceptanceRate: stats.acceptanceRate,
		contestRating: contests.rating,
		maxRating: contests.maxRating,
		contestRank: contests.rank,
		globalContestRanking: contests.globalRanking,
		elo,
		eloSource,
		currentStreak: heatmap.currentStreak,
		longestStreak: heatmap.longestStreak,
		year,
		contributions: contributionsForYear(heatmap.dailyContributions, year),
		availableYears: heatmap.availableYears.length
			? heatmap.availableYears
			: heatmap.yearlyContributions.map((entry) => entry.year),
		recentSubmissions: [
			...profile.recentSubmissions
				.filter(
					(s) => +s.timestamp >= Math.floor((Date.now() + 25200000) / 86400000) * 86400 - 25200
				)
				.sort((a, b) => +b.timestamp - +a.timestamp)
				.reduce(
					(m, s) =>
						m.set(s.titleSlug, {
							title: s.title,
							url: `https://leetcode.com/problems/${s.titleSlug}/`,
							status:
								s.statusDisplay === 'Accepted' || m.get(s.titleSlug)?.status === 'Accepted'
									? 'Accepted'
									: 'In progress',
						}),
					new Map<string, { title: string; url: string; status: 'Accepted' | 'In progress' }>()
				)
				.values(),
		],
	};
};

export const fetchMemberYearContributions = async (username: string, year: number) => {
	const heatmap = await fetchLeetCodeHeatmap(username, year);

	return {
		year,
		currentStreak: heatmap.currentStreak,
		longestStreak: heatmap.longestStreak,
		contributions: contributionsForYear(heatmap.dailyContributions, year),
		availableYears: heatmap.availableYears,
	};
};
