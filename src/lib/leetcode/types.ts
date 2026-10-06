export type LeetCodeEnvelope<T> = {
	status: 'success' | 'error';
	message?: string;
	username?: string;
	cached?: boolean;
	data: T | null;
};

export type LeetCodeProfile = {
	displayName: string | null;
	username: string;
	avatar: string | null;
	country: string | null;
	bio: string | null;
};

export type LeetCodeStats = {
	totalSolved: number;
	totalQuestions: number;
	acceptanceRate: number;
	byDifficulty: {
		easy: number;
		medium: number;
		hard: number;
	};
};

export type LeetCodeContestHistory = {
	count: number;
	rating: number | null;
	maxRating: number | null;
	rank: string | null;
	globalRanking: number | null;
	topPercentage: number | null;
};

export type HeatmapDay = {
	date: string;
	count: number;
	level: number;
};

export type YearlyContribution = {
	year: number;
	totalSubmissions: number;
	activeDays: number;
};

export type LeetCodeHeatmap = {
	totalSubmissions: number;
	totalActiveDays: number;
	currentStreak: number;
	longestStreak: number;
	maxDailySubmissions: number;
	firstActiveDate: string | null;
	lastActiveDate: string | null;
	dailyContributions: HeatmapDay[];
	yearlyContributions: YearlyContribution[];
	availableYears: number[];
};

export type DailyContribution = {
	date: string;
	count: number;
};

export type MemberSnapshot = {
	username: string;
	displayName: string;
	avatar: string | null;
	country: string | null;
	totalSolved: number;
	totalQuestions: number;
	easy: number;
	medium: number;
	hard: number;
	acceptanceRate: number;
	contestRating: number | null;
	maxRating: number | null;
	contestRank: string | null;
	globalContestRanking: number | null;
	elo: number;
	eloSource: 'contest' | 'derived';
	currentStreak: number;
	longestStreak: number;
	year: number;
	contributions: DailyContribution[];
	availableYears: number[];
	error?: string;
};
