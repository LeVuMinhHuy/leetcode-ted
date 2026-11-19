import { LC_USERNAME } from '@/constants/data-source';
import {
	fetchMemberSnapshot,
	fetchMemberYearContributions,
} from '@/lib/leetcode/client';
import { NextResponse } from 'next/server';

const useBackend = process.env.ENABLE_BACKEND_API === 'true';
const BACKEND_URL = process.env.BACKEND_URL;

export const GET = async (request: Request) => {
	try {
		const { searchParams } = new URL(request.url);
		const username = (searchParams.get('username') || LC_USERNAME).trim().toLowerCase();
		const yearParam = searchParams.get('year');
		const year = yearParam ? Number(yearParam) : undefined;

		if (useBackend && BACKEND_URL && year && Number.isFinite(year)) {
			const backendRes = await fetch(
				`${BACKEND_URL}/api/members/${encodeURIComponent(username)}/heatmap?year=${year}`,
				{ cache: 'no-store' }
			);
			if (!backendRes.ok) {
				return NextResponse.json({ error: 'Failed to fetch heatmap from backend' }, { status: backendRes.status });
			}
			return NextResponse.json(await backendRes.json());
		}

		if (year && Number.isFinite(year)) {
			const heatmap = await fetchMemberYearContributions(username, year);
			return NextResponse.json(heatmap);
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
