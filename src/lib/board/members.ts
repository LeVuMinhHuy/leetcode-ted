import { BOARD_MEMBERS_COLLECTION } from '@/constants/members';
import type { BoardMemberConfig } from '@/constants/members';
import clientPromise from '@/lib/mongodb';

const normalizeUsername = (username: string): string => username.trim().toLowerCase();

const dedupeMembers = (members: BoardMemberConfig[]): BoardMemberConfig[] => {
	const byUsername = new Map<string, BoardMemberConfig>();

	members.forEach((member) => {
		const username = normalizeUsername(member.username);
		if (!username) return;
		if (!byUsername.has(username)) {
			byUsername.set(username, {
				username,
				displayName: member.displayName?.trim() || undefined,
			});
		}
	});

	return Array.from(byUsername.values());
};

const loadJoinedMembers = async (): Promise<BoardMemberConfig[]> => {
	try {
		const client = await clientPromise();
		const docs = await client
			.db()
			.collection<BoardMemberConfig>(BOARD_MEMBERS_COLLECTION)
			.find({}, { projection: { _id: 0, username: 1, displayName: 1 } })
			.toArray();

		return docs;
	} catch {
		return [];
	}
};

export const listBoardMembers = async (): Promise<BoardMemberConfig[]> =>
	dedupeMembers(await loadJoinedMembers());

export const addBoardMember = async (
	username: string,
	displayName?: string
): Promise<BoardMemberConfig> => {
	const normalized = normalizeUsername(username);
	if (!normalized) {
		throw new Error('Username is required');
	}

	const member: BoardMemberConfig = {
		username: normalized,
		displayName: displayName?.trim() || undefined,
	};

	const client = await clientPromise();
	const collection = client.db().collection(BOARD_MEMBERS_COLLECTION);

	await collection.updateOne(
		{ username: normalized },
		{
			$set: {
				username: normalized,
				...(member.displayName ? { displayName: member.displayName } : {}),
			},
			$setOnInsert: { joinedAt: new Date() },
		},
		{ upsert: true }
	);

	return member;
};
