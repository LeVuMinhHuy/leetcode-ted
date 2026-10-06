import { addBoardMember, listBoardMembers } from '@/lib/board/members';
import { leetCodeUserExists } from '@/lib/leetcode/client';
import { revalidatePath } from 'next/cache';
import { NextResponse } from 'next/server';

const USERNAME_PATTERN = /^[a-zA-Z0-9_-]{3,30}$/;

export const GET = async () => {
	const members = await listBoardMembers();
	return NextResponse.json({ members });
};

export const POST = async (request: Request) => {
	try {
		const body = (await request.json()) as { username?: string; displayName?: string };
		const username = body.username?.trim() ?? '';
		const displayName = body.displayName?.trim();

		if (!USERNAME_PATTERN.test(username)) {
			return NextResponse.json(
				{ error: 'Use a valid LeetCode username (3-30 letters, numbers, _ or -).' },
				{ status: 400 }
			);
		}

		const exists = await leetCodeUserExists(username);
		if (!exists) {
			return NextResponse.json({ error: 'That LeetCode username was not found.' }, { status: 404 });
		}

		const member = await addBoardMember(username, displayName);
		revalidatePath('/');
		return NextResponse.json({ member }, { status: 201 });
	} catch (error) {
		console.error('Error joining board:', error);
		return NextResponse.json({ error: 'Failed to join the board' }, { status: 500 });
	}
};
