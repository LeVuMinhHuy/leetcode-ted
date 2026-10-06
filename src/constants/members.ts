export type BoardMemberConfig = {
	username: string;
	displayName?: string;
};

export const BOARD_MEMBERS: BoardMemberConfig[] = [
	{ username: 'tedvu', displayName: 'Ted' },
];

export const BOARD_MEMBERS_COLLECTION = 'board_members';
