import { BackendUnavailableError, backendFetch, withBackendFallback } from '@/lib/backend/client';
import { listBoardMembers } from '@/lib/board/members';
import { fetchMemberSnapshot } from '@/lib/leetcode/client';
import type { MemberSnapshot } from '@/lib/leetcode/types';

const compareMembers = (a: MemberSnapshot, b: MemberSnapshot): number => {
	if (b.elo !== a.elo) return b.elo - a.elo;
	if (b.currentStreak !== a.currentStreak) return b.currentStreak - a.currentStreak;
	return b.totalSolved - a.totalSolved;
};

const getBoardFromBackend = async (): Promise<MemberSnapshot[]> => {
	const response = await backendFetch('/api/board', { headers: { Accept: 'application/json' } });
	// Read-only path: any non-2xx means this host can't serve the board, so use the direct flow.
	if (!response.ok) throw new BackendUnavailableError(`/api/board returned ${response.status}`);
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

export const getBoard = (): Promise<MemberSnapshot[]> =>
	withBackendFallback('board', getBoardFromBackend, getBoardDirect);
