export type BoardMemberConfig = {
	username: string;
	displayName?: string;

	excusedDates?: string[];
};

export const BOARD_MEMBERS: BoardMemberConfig[] = [{ username: 'tedvu', displayName: 'Ted' }];

export const BOARD_MEMBERS_COLLECTION = 'board_members';

// The date we all drunk lol
export const EXCUSED_DATES: Record<string, string[]> = {
	jaydennguy2211: ['2026-10-16'],
	n_t_hien126: ['2026-10-16'],
};
