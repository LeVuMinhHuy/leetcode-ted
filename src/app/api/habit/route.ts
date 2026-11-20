import { LC_USERNAME } from '@/constants/data-source';
import { backendFetch, withBackendFallback } from '@/lib/backend/client';
import {
	fetchMemberSnapshot,
	fetchMemberYearContributions,
} from '@/lib/leetcode/client';
import { NextResponse } from 'next/server';

type HeatmapResult = { status: number; body: unknown };

const heatmapViaBackend = async (username: string, year: number): Promise<HeatmapResult> => {
	const response = await backendFetch(
		`/api/members/${encodeURIComponent(username)}/heatmap?year=${year}`
	);
	if (!response.ok) {
		return { status: response.status, body: { error: 'Failed to fetch heatmap from backend' } };
	}
	return { status: 200, body: await response.json() };
};

const heatmapDirect = async (username: string, year: number): Promise<HeatmapResult> => ({
	status: 200,
	body: await fetchMemberYearContributions(username, year),
});

export const GET = async (request: Request) => {
	try {
		const { searchParams } = new URL(request.url);
		const username = (searchParams.get('username') || LC_USERNAME).trim().toLowerCase();
		const yearParam = searchParams.get('year');
		const year = yearParam ? Number(yearParam) : undefined;

		if (year && Number.isFinite(year)) {
			const result = await withBackendFallback(
				'heatmap',
				() => heatmapViaBackend(username, year),
				() => heatmapDirect(username, year)
			);
			return NextResponse.json(result.body, { status: result.status });
		}

		const snapshot = await fetchMemberSnapshot(username);
		return NextResponse.json({
			dates: snapshot.contributions.flatMap((day) =>
				Array.from({ length: day.count }, () => day.date)
			),
			year: snapshot.year,
			streak: snapshot.currentStreak,
			longestStreak: snapshot.longestStreak,
			totalSolved: snapshot.totalSolved,
			totalQuestions: snapshot.totalQuestions,
			contributions: snapshot.contributions,
		});
	} catch (error) {
		console.error('Error fetching calendar data:', error);
		return NextResponse.json({ error: 'Failed to fetch calendar data' }, { status: 500 });
	}
};
