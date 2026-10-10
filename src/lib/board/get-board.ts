import { listBoardMembers } from '@/lib/board/members';
import { fetchMemberSnapshot } from '@/lib/leetcode/client';
import type { MemberSnapshot } from '@/lib/leetcode/types';

const compareMembers = (a: MemberSnapshot, b: MemberSnapshot): number => {
	if (b.elo !== a.elo) return b.elo - a.elo;
	if (b.currentStreak !== a.currentStreak) return b.currentStreak - a.currentStreak;
	return b.totalSolved - a.totalSolved;
};

export const getBoard = async (): Promise<MemberSnapshot[]> => {
	const members = await listBoardMembers();

	const snapshots = await Promise.all(
		members.map(async (member) => {
			try {
				const data = await fetchMemberSnapshot(member.username, member.displayName);
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
					eloSource: 'derived' as const,
					currentStreak: 0,
					longestStreak: 0,
					year: new Date().getFullYear(),
					contributions: [],
					availableYears: [new Date().getFullYear()],
					error: message,
				} satisfies MemberSnapshot;
			}
		})
	);

	return snapshots.sort(compareMembers);
};
