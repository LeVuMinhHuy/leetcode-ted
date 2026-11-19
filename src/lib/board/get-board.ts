import { listBoardMembers } from '@/lib/board/members';
import { fetchMemberSnapshot } from '@/lib/leetcode/client';
import type { MemberSnapshot } from '@/lib/leetcode/types';

const useBackend = process.env.ENABLE_BACKEND_API === 'true';
const BACKEND_URL = process.env.BACKEND_URL;

const compareMembers = (a: MemberSnapshot, b: MemberSnapshot): number => {
	if (b.elo !== a.elo) return b.elo - a.elo;
	if (b.currentStreak !== a.currentStreak) return b.currentStreak - a.currentStreak;
	return b.totalSolved - a.totalSolved;
};

const getBoardFromBackend = async (): Promise<MemberSnapshot[]> => {
	const response = await fetch(`${BACKEND_URL}/api/board`, {
		headers: { Accept: 'application/json' },
		cache: 'no-store',
	});
	if (!response.ok) throw new Error(`Backend returned ${response.status}`);
	const { members } = (await response.json()) as { members: MemberSnapshot[] };
	return members;
};

const getBoardDirect = async (): Promise<MemberSnapshot[]> => {
	const members = await listBoardMembers();

	const snapshots = await Promise.all(
		members.map(async (member) => {
			try {
				const data = await fetchMemberSnapshot(
					member.username,
					member.displayName,
					member.excusedDates
				);
				return data;
			} catch (error) {
				const message = error instanceof Error ? error.message : 'Failed to load member';
				return {
					username: member.username,
					displayName: member.displayName || member.username,
					avatar: null,
					country: null,
					totalSolved: 0,
					totalQuestions: 0,
					easy: 0,
					medium: 0,
					hard: 0,
					acceptanceRate: 0,
					contestRating: null,
					maxRating: null,
					contestRank: null,
					globalContestRanking: null,
					elo: 0,
					currentStreak: 0,
					longestStreak: 0,
					year: new Date().getFullYear(),
					contributions: [],
					availableYears: [new Date().getFullYear()],
					error: message,
					recentSubmissions: [],
				} satisfies MemberSnapshot;
			}
		})
	);

	return snapshots.sort(compareMembers);
};

export const getBoard = async (): Promise<MemberSnapshot[]> => {
	if (useBackend && BACKEND_URL) return getBoardFromBackend();
	return getBoardDirect();
};
